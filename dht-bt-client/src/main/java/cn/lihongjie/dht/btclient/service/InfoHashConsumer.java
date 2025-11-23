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
    
    private final LibtorrentMetadataDownloader libtorrentMetadataDownloader;
    private final BloomFilterService bloomFilterService;
    
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
            
            // 统一使用 libtorrent4j 异步获取元数据
            libtorrentMetadataDownloader.downloadAsync(infoHash)
                    .whenComplete((data, ex) -> {
                        if (ex != null) {
                            log.debug("libtorrent metadata failed infoHash={} reason={}", infoHash, ex.getMessage());
                        } else if (data != null) {
                            if (dedupEnabled) bloomFilterService.add(bloomFilterKey, infoHash);
                            processedCount.incrementAndGet();
                            log.info("libtorrent metadata success infoHash={} size={} bytes", infoHash, data.length);
                        } else {
                            log.debug("libtorrent metadata empty infoHash={}", infoHash);
                        }
                        acknowledgment.acknowledge();
                    });
            
        } catch (Exception e) {
            log.error("Error processing InfoHash message", e);
            acknowledgment.acknowledge(); // 避免重复处理
        }
    }
    
    // 旧的直连与回退逻辑已停用
}
