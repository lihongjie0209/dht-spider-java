package cn.lihongjie.dht.btclient.service;

import cn.lihongjie.dht.common.constants.KafkaTopics;
import cn.lihongjie.dht.common.model.InfoHashMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * InfoHash消费者
 * 从Kafka消费InfoHash，触发元数据下载
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfoHashConsumer {
    
    private final MetadataDownloader metadataDownloader;
    
    @KafkaListener(
        topics = KafkaTopics.INFOHASH_DISCOVERED,
        groupId = "${spring.kafka.consumer.group-id}",
        concurrency = "3"
    )
    public void consume(InfoHashMessage message, Acknowledgment acknowledgment) {
        try {
            String infoHash = message.getInfoHash();
            log.debug("Received InfoHash: {}", infoHash);
            
            // 异步下载元数据
            metadataDownloader.downloadAsync(infoHash)
                .whenComplete((metadata, ex) -> {
                    if (ex != null) {
                        log.error("Failed to download metadata for InfoHash: {}", infoHash, ex);
                    } else if (metadata != null) {
                        log.info("Successfully downloaded metadata for InfoHash: {}, name: {}", 
                                infoHash, metadata.getName());
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
}
