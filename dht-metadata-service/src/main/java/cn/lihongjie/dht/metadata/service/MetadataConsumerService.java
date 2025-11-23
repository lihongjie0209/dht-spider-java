package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * 元数据消费服务
 * 从Kafka消费已下载的元数据并持久化到数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataConsumerService {
    
    private final MetadataPersistenceService persistenceService;
    private final MetadataStatsService statsService;
    private final ObjectMapper objectMapper;
    
    /**
     * 消费元数据消息
     */
    @KafkaListener(
        topics = "${kafka.topic.metadata}",
        groupId = "${spring.kafka.consumer.group-id}",
        concurrency = "3"
    )
    public void consumeMetadata(String message) {
        statsService.incrementConsumed();
        
        try {
            log.debug("Received metadata message: {}", message);
            
            // 解析消息
            TorrentMetadata metadata = objectMapper.readValue(message, TorrentMetadata.class);
            
            // 持久化
            persistenceService.save(metadata);
            
            log.info("Successfully persisted metadata for InfoHash: {}", metadata.getInfoHash());
            
        } catch (Exception e) {
            statsService.incrementFailed();
            log.error("Failed to process metadata message: {}", message, e);
        }
    }
}
