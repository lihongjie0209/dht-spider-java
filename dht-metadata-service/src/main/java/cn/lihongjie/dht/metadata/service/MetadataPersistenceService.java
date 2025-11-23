package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import cn.lihongjie.dht.metadata.entity.TorrentFileEntity;
import cn.lihongjie.dht.metadata.entity.TorrentMetadataEntity;
import cn.lihongjie.dht.metadata.repository.TorrentMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import cn.lihongjie.dht.springcommon.bloom.BloomFilterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * 元数据持久化服务
 * 使用Bloom Filter预检查避免不必要的数据库查询
 */
@Slf4j
@Service
public class MetadataPersistenceService {
    private final TorrentMetadataRepository repository;
    private final MetadataCacheService cacheService;
    private final MetadataStatsService statsService;
    private final BloomFilterService bloomFilterService;

    public MetadataPersistenceService(TorrentMetadataRepository repository,
                                      MetadataCacheService cacheService,
                                      MetadataStatsService statsService,
                                      BloomFilterService bloomFilterService) {
        this.repository = repository;
        this.cacheService = cacheService;
        this.statsService = statsService;
        this.bloomFilterService = bloomFilterService;
    }
    
    @Value("${dedup.enabled:true}")
    private boolean dedupEnabled;
    
    @Value("${dedup.bloom.key:dht:bloom:infohash}")
    private String bloomFilterKey;
    
    /**
     * 保存元数据
     * 使用三级检查：Bloom Filter预检查 -> 数据库查询 -> 保存
     */
    @Transactional
    public void save(TorrentMetadata metadata) {
        String infoHash = metadata.getInfoHash();
        
        // 第一级：Bloom Filter预检查（快速排除肯定不存在的）
        if (dedupEnabled && !mightExist(infoHash)) {
            // Bloom Filter说不存在，直接保存（跳过数据库查询）
            log.debug("Bloom Filter: {} not exists, saving directly", infoHash);
        } else if (repository.existsByInfoHash(infoHash)) {
            // 已存在则更新状态（以及可用的基础信息），避免重复插入
            repository.findByInfoHash(infoHash).ifPresent(entity -> {
                if (metadata.getStatus() != null) {
                    entity.setStatus(metadata.getStatus());
                }
                if (metadata.getName() != null && (entity.getName() == null || "FAILED".equalsIgnoreCase(entity.getStatus()))) {
                    entity.setName(metadata.getName());
                }
                if (metadata.getTotalSize() != null && metadata.getTotalSize() > 0 && (entity.getTotalSize() == null || entity.getTotalSize() == 0)) {
                    entity.setTotalSize(metadata.getTotalSize());
                }
                entity.setUpdatedAt(Instant.now());
                repository.save(entity);
                log.debug("Updated existing metadata for InfoHash: {} with status={} ", infoHash, entity.getStatus());
            });
            return;
        }
        
        try {
            // 转换为实体
            TorrentMetadataEntity entity = convertToEntity(metadata);
            
            // 保存到数据库
            TorrentMetadataEntity saved = repository.save(entity);
            
            // 标记到Bloom Filter
            if (dedupEnabled) {
                markAsProcessed(infoHash);
            }
            
            // 缓存到Redis
            cacheService.cache(metadata);
            
            // 统计
            statsService.incrementPersisted();
            
            log.info("Saved metadata for InfoHash: {}, status: {}, name: {}, size: {} bytes", 
                    infoHash, metadata.getStatus(), metadata.getName(), metadata.getTotalSize());
            
        } catch (Exception e) {
            log.error("Failed to save metadata for InfoHash: {}", infoHash, e);
            throw new RuntimeException("Failed to save metadata", e);
        }
    }
    
    /**
     * Bloom Filter预检查（使用Redis原生BF.EXISTS命令，单元素）
     */
    private boolean mightExist(String infoHash) {
        return bloomFilterService.exists(bloomFilterKey, infoHash);
    }
    
    /**
     * 标记到Bloom Filter（使用Redis原生BF.ADD命令，单元素）
     */
    private void markAsProcessed(String infoHash) {
        bloomFilterService.add(bloomFilterKey, infoHash);
    }
    
    /**
     * 转换为实体
     */
    private TorrentMetadataEntity convertToEntity(TorrentMetadata metadata) {
        Instant now = Instant.now();
        
        TorrentMetadataEntity entity = TorrentMetadataEntity.builder()
                .infoHash(metadata.getInfoHash())
                .name(metadata.getName())
                .totalSize(metadata.getTotalSize())
                .status(metadata.getStatus() != null ? metadata.getStatus() : "SUCCESS")
                .createdAt(now)
                .updatedAt(now)
                .build();
        
        // 转换文件列表
        if (metadata.getFiles() != null && !metadata.getFiles().isEmpty()) {
            var files = metadata.getFiles().stream()
                    .map(file -> TorrentFileEntity.builder()
                            .metadata(entity)  // 设置父实体引用
                            .filePath(file.getPath())
                            .fileSize(file.getLength())
                            .build())
                    .collect(Collectors.toList());
            entity.getFiles().addAll(files);
        }
        
        return entity;
    }
}
