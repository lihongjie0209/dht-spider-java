package cn.lihongjie.dht.btclient.service;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import cn.lihongjie.dht.common.constants.KafkaTopics;
import cn.lihongjie.dht.common.model.TorrentMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 元数据发布服务
 * 负责将下载的Torrent元数据发布到Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataPublisher {
    
    private final KafkaTemplate<String, TorrentMetadata> kafkaTemplate;
    
    private final AtomicLong publishedCount = new AtomicLong(0);
    
    /**
     * 发布Torrent元数据
     */
    public void publish(String infoHash, Torrent torrent) {
        try {
            TorrentMetadata metadata = convertToMetadata(infoHash, torrent);
            
            kafkaTemplate.send(KafkaTopics.METADATA_FETCHED, infoHash, metadata)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish metadata for InfoHash: {}", infoHash, ex);
                    } else {
                        long count = publishedCount.incrementAndGet();
                        if (count % 10 == 0) {
                            log.info("Published {} metadata to Kafka", count);
                        }
                    }
                });
            
        } catch (Exception e) {
            log.error("Error publishing metadata for InfoHash: {}", infoHash, e);
        }
    }
    
    /**
     * 转换Bt的Torrent对象为我们的TorrentMetadata
     */
    private TorrentMetadata convertToMetadata(String infoHash, Torrent torrent) {
        List<TorrentMetadata.FileInfo> files = torrent.getFiles().stream()
            .map(this::convertFileInfo)
            .collect(Collectors.toList());
        
        return TorrentMetadata.builder()
            .infoHash(infoHash)
            .name(torrent.getName())
            .totalSize(torrent.getSize())
            .files(files)
            .fetchedAt(Instant.now())
            .build();
    }
    
    /**
     * 转换文件信息
     */
    private TorrentMetadata.FileInfo convertFileInfo(TorrentFile file) {
        return TorrentMetadata.FileInfo.builder()
            .path(String.join("/", file.getPathElements()))
            .size(file.getSize())
            .build();
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Published: %d", publishedCount.get());
    }
}
