package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import cn.lihongjie.dht.metadata.entity.TorrentFileEntity;
import cn.lihongjie.dht.metadata.entity.TorrentMetadataEntity;
import cn.lihongjie.dht.metadata.repository.TorrentMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, String> redisTemplate;

    public MetadataPersistenceService(TorrentMetadataRepository repository,
                                      MetadataCacheService cacheService,
                                      MetadataStatsService statsService,
                                      @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.repository = repository;
        this.cacheService = cacheService;
        this.statsService = statsService;
        this.redisTemplate = redisTemplate;
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
            // 第二级：数据库查询（处理Bloom Filter误判）
            log.debug("Metadata already exists for InfoHash: {}, skipping", infoHash);
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
            
            log.info("Saved metadata for InfoHash: {}, name: {}, size: {} bytes", 
                    infoHash, metadata.getName(), metadata.getTotalSize());
            
        } catch (Exception e) {
            log.error("Failed to save metadata for InfoHash: {}", infoHash, e);
            throw new RuntimeException("Failed to save metadata", e);
        }
    }
    
    /**
     * Bloom Filter预检查（使用Redis原生BF.MEXISTS命令）
     */
    private boolean mightExist(String infoHash) {
        try {
            Object result = redisTemplate.execute(
                (connection) -> connection.execute("BF.MEXISTS", 
                    bloomFilterKey.getBytes(), infoHash.getBytes()),
                true
            );
            return result != null && "1".equals(result.toString());
        } catch (Exception e) {
            log.error("Bloom Filter check failed for {}: {}", infoHash, e.getMessage());
            return true; // 出错时假设存在，走数据库查询
        }
    }
    
    /**
     * 标记到Bloom Filter（使用Redis原生BF.MADD命令）
     */
    private void markAsProcessed(String infoHash) {
        try {
            redisTemplate.execute(
                (connection) -> connection.execute("BF.MADD", 
                    bloomFilterKey.getBytes(), infoHash.getBytes()),
                true
            );
        } catch (Exception e) {
            log.error("Bloom Filter mark failed for {}: {}", infoHash, e.getMessage());
        }
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
