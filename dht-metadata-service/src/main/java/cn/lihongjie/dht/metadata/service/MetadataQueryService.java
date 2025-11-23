package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import cn.lihongjie.dht.metadata.dto.TorrentMetadataDTO;
import cn.lihongjie.dht.metadata.entity.TorrentMetadataEntity;
import cn.lihongjie.dht.metadata.repository.TorrentMetadataRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 元数据查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataQueryService {
    
    private final TorrentMetadataRepository repository;
    private final MetadataCacheService cacheService;
    private final ObjectMapper objectMapper;
    
    /**
     * 根据InfoHash查询
     */
    public Optional<TorrentMetadataDTO> findByInfoHash(String infoHash) {
        // 先查缓存
        Optional<TorrentMetadata> cached = cacheService.get(infoHash);
        if (cached.isPresent()) {
            return Optional.of(convertToDTO(cached.get()));
        }
        
        // 查数据库
        return repository.findByInfoHash(infoHash)
                .map(this::convertToDTO);
    }
    
    /**
     * 搜索种子
     */
    public Page<TorrentMetadataDTO> search(String keyword, Pageable pageable) {
        return repository.searchByName(keyword, pageable)
                .map(this::convertToDTO);
    }
    
    /**
     * 获取最新种子
     */
    public Page<TorrentMetadataDTO> getLatest(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::convertToDTO);
    }
    
    /**
     * 转换为DTO（从TorrentMetadata）
     */
    private TorrentMetadataDTO convertToDTO(TorrentMetadata metadata) {
        return TorrentMetadataDTO.builder()
                .infoHash(metadata.getInfoHash())
                .name(metadata.getName())
                .totalSize(metadata.getTotalSize())
                .files(metadata.getFiles())
                .fetchedAt(metadata.getFetchedAt())
                .build();
    }
    
    /**
     * 转换为DTO（从Entity）
     */
    private TorrentMetadataDTO convertToDTO(TorrentMetadataEntity entity) {
        try {
            List<TorrentMetadata.FileInfo> files = objectMapper.readValue(
                    entity.getFilesJson(),
                    new TypeReference<List<TorrentMetadata.FileInfo>>() {}
            );
            
            return TorrentMetadataDTO.builder()
                    .infoHash(entity.getInfoHash())
                    .name(entity.getName())
                    .totalSize(entity.getTotalSize())
                    .files(files)
                    .fetchedAt(entity.getCreatedAt())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to convert entity to DTO for InfoHash: {}", entity.getInfoHash(), e);
            throw new RuntimeException("Failed to convert entity to DTO", e);
        }
    }
}
