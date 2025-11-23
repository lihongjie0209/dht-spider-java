package cn.lihongjie.dht.mldht.service;

import cn.lihongjie.dht.common.constants.KafkaTopics;
import cn.lihongjie.dht.common.model.InfoHashMessage;
import cn.lihongjie.dht.common.util.BloomFilterUtils;
import cn.lihongjie.dht.springcommon.bloom.BloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * InfoHash发布服务
 * 负责使用Bloom Filter去重和发布InfoHash到Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfoHashPublisher {
    
    private final KafkaTemplate<String, InfoHashMessage> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate; // retained for potential future non-bloom redis ops
    private final BloomFilterService bloomFilterService;
    
    @Value("${dedup.enabled:true}")
    private boolean dedupEnabled;
    
    @Value("${dedup.bloom.key:dht:bloom:infohash}")
    private String bloomFilterKey;
    
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    
    /**
     * 发布InfoHash消息
     */
    public void publish(InfoHashMessage message) {
        String infoHash = message.getInfoHash();
        
        // 去重检查
        if (dedupEnabled && isDuplicate(infoHash)) {
            duplicateCount.incrementAndGet();
            if (duplicateCount.get() % 100 == 0) {
                log.debug("Skipped {} duplicate InfoHashes", duplicateCount.get());
            }
            return;
        }
        
        // 发布到Kafka
        try {
            kafkaTemplate.send(KafkaTopics.INFOHASH_DISCOVERED, infoHash, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InfoHash: {}", infoHash, ex);
                    } else {
                        long count = publishedCount.incrementAndGet();
                        if (count % 100 == 0) {
                            log.info("Published {} InfoHashes to Kafka", count);
                        }
                    }
                });
            
            // 标记为已处理
            if (dedupEnabled) {
                markAsProcessed(infoHash);
            }
            
        } catch (Exception e) {
            log.error("Error publishing InfoHash: {}", infoHash, e);
        }
    }
    
    /**
     * 检查InfoHash是否已经处理过（使用Redis原生BF.MEXISTS命令）
     */
    private boolean isDuplicate(String infoHash) {
        try {
            return bloomFilterService.exists(bloomFilterKey, infoHash);
        } catch (Exception e) {
            log.error("Bloom Filter check failed via BloomFilterService for {}: {}", infoHash, e.getMessage());
            return false;
        }
    }
    
    /**
     * 标记InfoHash为已处理（使用Redis原生BF.MADD命令）
     */
    private void markAsProcessed(String infoHash) {
        try {
            bloomFilterService.add(bloomFilterKey, infoHash);
        } catch (Exception e) {
            log.error("Bloom Filter mark failed via BloomFilterService for {}: {}", infoHash, e.getMessage());
        }
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Published: %d, Duplicates: %d", 
                           publishedCount.get(), duplicateCount.get());
    }
}
