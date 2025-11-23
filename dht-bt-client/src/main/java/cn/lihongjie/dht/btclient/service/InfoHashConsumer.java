package cn.lihongjie.dht.btclient.service;

import cn.lihongjie.dht.common.constants.KafkaTopics;
import cn.lihongjie.dht.common.model.InfoHashMessage;
import cn.lihongjie.dht.common.util.BloomFilterUtils;
import cn.lihongjie.dht.springcommon.bloom.BloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final BloomFilterService bloomFilterService;
    private final DirectPeerDownloader directPeerDownloader;
    
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
            if (dedupEnabled && bloomFilterService.exists(bloomFilterKey, infoHash)) {
                long count = duplicateCount.incrementAndGet();
                if (count % 100 == 0) {
                    log.debug("Skipped {} duplicate downloads", count);
                }
                acknowledgment.acknowledge();
                return;
            }
            
            // 先尝试直连获取 ut_metadata，若成功则跳过后续下载
            if (message.getSourceIp() != null && message.getSourcePort() != null) {
                directPeerDownloader.tryDirectAndFetch(infoHash, message.getSourceIp(), message.getSourcePort())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.debug("Direct attempt error infoHash={} reason={}", infoHash, ex.getMessage());
                                fallbackDownload(infoHash, acknowledgment);
                            } else if (result.metadataSuccess()) {
                                log.info("Early ut_metadata success infoHash={} (handshake={})", infoHash, result.handshakeSuccess());
                                if (dedupEnabled) {
                                    bloomFilterService.add(bloomFilterKey, infoHash);
                                }
                                processedCount.incrementAndGet();
                                acknowledgment.acknowledge();
                            } else {
                                // 未成功获取元数据，回退
                                fallbackDownload(infoHash, acknowledgment);
                            }
                        });
            } else {
                // 没有 peer 信息直接执行常规下载
                fallbackDownload(infoHash, acknowledgment);
            }
            
        } catch (Exception e) {
            log.error("Error processing InfoHash message", e);
            acknowledgment.acknowledge(); // 避免重复处理
        }
    }
    
    private void fallbackDownload(String infoHash, Acknowledgment acknowledgment) {
        metadataDownloader.downloadAsync(infoHash)
                .whenComplete((metadata, ex) -> {
                    if (ex != null) {
                        log.error("Failed to download metadata for InfoHash: {}", infoHash, ex);
                    } else if (metadata != null) {
                        log.info("Successfully downloaded metadata for InfoHash: {}, name: {}",
                                infoHash, metadata.getName());
                        if (dedupEnabled) {
                            bloomFilterService.add(bloomFilterKey, infoHash);
                        }
                        processedCount.incrementAndGet();
                    } else {
                        log.warn("No metadata downloaded for InfoHash: {}", infoHash);
                    }
                    acknowledgment.acknowledge();
                });
    }
}
