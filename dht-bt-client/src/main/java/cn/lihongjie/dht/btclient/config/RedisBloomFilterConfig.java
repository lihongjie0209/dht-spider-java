package cn.lihongjie.dht.btclient.config;

import cn.lihongjie.dht.common.util.BloomFilterUtils;
import cn.lihongjie.dht.springcommon.bloom.BloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Bloom Filter初始化配置（BT Client服务专用）
 * 跟踪已下载的InfoHash
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBloomFilterConfig {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final BloomFilterService bloomFilterService;
    
    @Value("${dedup.bloom.key:dht:bloom:downloaded}")
    private String bloomFilterKey;
    
    /**
     * 应用启动后初始化Bloom Filter
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeBloomFilter() {
        try {
            bloomFilterService.reserve(bloomFilterKey, BloomFilterUtils.ERROR_RATE, BloomFilterUtils.CAPACITY);
            
        } catch (Exception e) {
            log.error("Failed to initialize Bloom Filter: {}", e.getMessage());
            log.warn("Please ensure Redis Stack (with RedisBloom module) is running");
        }
    }
}
