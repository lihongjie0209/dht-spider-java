package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 元数据缓存服务
 * 使用Redis缓存热门元数据，提高查询性能
 */
@Slf4j
@Service
public class MetadataCacheService {
    
    private static final String CACHE_KEY_PREFIX = "torrent:metadata:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public MetadataCacheService(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate, 
                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 缓存元数据
     */
    public void cache(TorrentMetadata metadata) {
        try {
            String key = buildKey(metadata.getInfoHash());
            String value = objectMapper.writeValueAsString(metadata);
            
            redisTemplate.opsForValue().set(key, value, CACHE_TTL);
            log.debug("Cached metadata for InfoHash: {}", metadata.getInfoHash());
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache metadata for InfoHash: {}", metadata.getInfoHash(), e);
        }
    }
    
    /**
     * 从缓存获取元数据
     */
    public Optional<TorrentMetadata> get(String infoHash) {
        try {
            String key = buildKey(infoHash);
            String value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                TorrentMetadata metadata = objectMapper.readValue(value, TorrentMetadata.class);
                log.debug("Cache hit for InfoHash: {}", infoHash);
                return Optional.of(metadata);
            }
            
            log.debug("Cache miss for InfoHash: {}", infoHash);
            return Optional.empty();
            
        } catch (Exception e) {
            log.warn("Failed to get cached metadata for InfoHash: {}", infoHash, e);
            return Optional.empty();
        }
    }
    
    /**
     * 删除缓存
     */
    public void evict(String infoHash) {
        String key = buildKey(infoHash);
        redisTemplate.delete(key);
        log.debug("Evicted cache for InfoHash: {}", infoHash);
    }
    
    /**
     * 构建缓存键
     */
    private String buildKey(String infoHash) {
        return CACHE_KEY_PREFIX + infoHash;
    }
}
