package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import cn.lihongjie.dht.metadata.entity.TorrentFileEntity;
import cn.lihongjie.dht.metadata.entity.TorrentMetadataEntity;
import cn.lihongjie.dht.metadata.repository.TorrentMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * 元数据持久化服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataPersistenceService {
    
    private final TorrentMetadataRepository repository;
    private final MetadataCacheService cacheService;
    private final MetadataStatsService statsService;
    
    /**
     * 保存元数据
     */
    @Transactional
    public void save(TorrentMetadata metadata) {
        String infoHash = metadata.getInfoHash();
        
        // 检查是否已存在
        if (repository.existsByInfoHash(infoHash)) {
            log.debug("Metadata already exists for InfoHash: {}, skipping", infoHash);
            return;
        }
        
        try {
            // 转换为实体
            TorrentMetadataEntity entity = convertToEntity(metadata);
            
            // 保存到数据库
            TorrentMetadataEntity saved = repository.save(entity);
            
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
                            .metadataId(entity.getId())
                            .filePath(file.getPath())
                            .fileSize(file.getLength())
                            .build())
                    .collect(Collectors.toList());
            entity.getFiles().addAll(files);
        }
        
        return entity;
    }
}
