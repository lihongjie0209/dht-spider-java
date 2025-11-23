package cn.lihongjie.dht.mldht.service;

import cn.lihongjie.dht.common.constants.KafkaTopics;
import cn.lihongjie.dht.common.model.InfoHashMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InfoHash发布服务
 * 负责去重和发布InfoHash到Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfoHashPublisher {
    
    private final KafkaTemplate<String, InfoHashMessage> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${dedup.enabled:true}")
    private boolean dedupEnabled;
    
    @Value("${dedup.ttl.days:7}")
    private long dedupTtlDays;
    
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
     * 检查InfoHash是否已经处理过
     */
    private boolean isDuplicate(String infoHash) {
        try {
            String key = "dht:dedup:infohash:" + infoHash;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking duplicate for InfoHash: {}", infoHash, e);
            // 出错时不去重，避免丢失数据
            return false;
        }
    }
    
    /**
     * 标记InfoHash为已处理
     */
    private void markAsProcessed(String infoHash) {
        try {
            String key = "dht:dedup:infohash:" + infoHash;
            redisTemplate.opsForValue().set(key, "1", Duration.ofDays(dedupTtlDays));
        } catch (Exception e) {
            log.error("Error marking InfoHash as processed: {}", infoHash, e);
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
