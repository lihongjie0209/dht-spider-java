package cn.lihongjie.dht.metadata.service;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * 失败元数据消费服务
 * 消费 BT Client 发布到失败主题的消息，记录统计并为后续持久化扩展预留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedMetadataConsumerService {

    private final MetadataStatsService statsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topic.metadata.failed}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "1"
    )
    public void consumeFailed(String message) {
        statsService.incrementFailed();
        try {
            TorrentMetadata metadata = objectMapper.readValue(message, TorrentMetadata.class);
            String infoHash = metadata.getInfoHash();
            log.info("Consumed FAILED metadata infoHash={} status={}", infoHash, metadata.getStatus());
            // 后续如需入库，可在此处调用持久化服务并加上状态字段
        } catch (Exception e) {
            log.warn("Failed to parse FAILED metadata message: {}", message, e);
        }
    }
}
