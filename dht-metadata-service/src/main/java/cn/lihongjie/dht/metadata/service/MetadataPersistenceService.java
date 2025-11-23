package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import cn.lihongjie.dht.metadata.entity.TorrentMetadataEntity;
import cn.lihongjie.dht.metadata.repository.TorrentMetadataRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
    private final ObjectMapper objectMapper;
    
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
    private TorrentMetadataEntity convertToEntity(TorrentMetadata metadata) throws JsonProcessingException {
        Instant now = Instant.now();
        
        return TorrentMetadataEntity.builder()
                .infoHash(metadata.getInfoHash())
                .name(metadata.getName())
                .totalSize(metadata.getTotalSize())
                .filesJson(objectMapper.writeValueAsString(metadata.getFiles()))
                .rawMetadata(metadata.getRawMetadata())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
