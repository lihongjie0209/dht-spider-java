package cn.lihongjie.dht.btclient.service;

import cn.lihongjie.dht.common.constants.KafkaTopics;
import cn.lihongjie.dht.common.model.InfoHashMessage;
import cn.lihongjie.dht.common.util.BloomFilterUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * InfoHash消费者
 * 从Kafka消费InfoHash，使用Bloom Filter去重后触发元数据下载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfoHashConsumer {
    
    private final MetadataDownloader metadataDownloader;
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${dedup.enabled:true}")
    private boolean dedupEnabled;
    
    @Value("${dedup.bloom.key:dht:bloom:infohash}")
    private String bloomFilterKey;
    
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    
    @KafkaListener(
        topics = KafkaTopics.INFOHASH_DISCOVERED,
        groupId = "${spring.kafka.consumer.group-id}",
        concurrency = "3"
    )
    public void consume(InfoHashMessage message, Acknowledgment acknowledgment) {
        try {
            String infoHash = message.getInfoHash();
            log.debug("Received InfoHash: {}", infoHash);
            
            // Bloom Filter去重检查
            if (dedupEnabled && isDuplicate(infoHash)) {
                long count = duplicateCount.incrementAndGet();
                if (count % 100 == 0) {
                    log.debug("Skipped {} duplicate downloads", count);
                }
                acknowledgment.acknowledge();
                return;
            }
            
            // 异步下载元数据
            metadataDownloader.downloadAsync(infoHash)
                .whenComplete((metadata, ex) -> {
                    if (ex != null) {
                        log.error("Failed to download metadata for InfoHash: {}", infoHash, ex);
                    } else if (metadata != null) {
                        log.info("Successfully downloaded metadata for InfoHash: {}, name: {}", 
                                infoHash, metadata.getName());
                        
                        // 下载成功后标记为已处理
                        if (dedupEnabled) {
                            markAsProcessed(infoHash);
                        }
                        processedCount.incrementAndGet();
                    } else {
                        log.warn("No metadata downloaded for InfoHash: {}", infoHash);
                    }
                    
                    // 无论成功失败都确认消息
                    acknowledgment.acknowledge();
                });
            
        } catch (Exception e) {
            log.error("Error processing InfoHash message", e);
            acknowledgment.acknowledge(); // 避免重复处理
        }
    }
    
    /**
     * 检查InfoHash是否已下载过（使用Redis原生BF.EXISTS命令，单元素检查）
     */
    private boolean isDuplicate(String infoHash) {
        try {
            Object result = redisTemplate.execute(
                (connection) -> connection.execute("BF.EXISTS",
                    bloomFilterKey.getBytes(), infoHash.getBytes()),
                true);
            return result != null && "1".equals(result.toString());
        } catch (Exception e) {
            log.error("Bloom Filter EXISTS check failed for {}", infoHash, e);
            return false;
        }
    }
    
    /**
     * 标记InfoHash为已下载（使用Redis原生BF.ADD命令，单元素添加）
     */
    private void markAsProcessed(String infoHash) {
        try {
            redisTemplate.execute(
                (connection) -> connection.execute("BF.ADD",
                    bloomFilterKey.getBytes(), infoHash.getBytes()),
                true);
        } catch (Exception e) {
            log.error("Bloom Filter ADD mark failed for {}", infoHash, e);
        }
    }
}
