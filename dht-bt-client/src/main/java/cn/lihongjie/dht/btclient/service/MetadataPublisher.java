package cn.lihongjie.dht.btclient.service;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import cn.lihongjie.dht.btclient.parser.RawInfoParser;
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
    private final MetadataStatusService statusService;

    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final Bencode bencode = new Bencode();
    private final RawInfoParser rawInfoParser = new RawInfoParser();
    
    /**
     * 发布Torrent元数据
     */
    public void publish(String infoHash, Torrent torrent) {
        try {
            TorrentMetadata metadata = convertToMetadata(infoHash, torrent);
            metadata.setStatus("SUCCESS");
            
            kafkaTemplate.send(KafkaTopics.METADATA_FETCHED, infoHash, metadata)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish metadata for InfoHash: {}", infoHash, ex);
                        statusService.setStatus(infoHash, "FAILED");
                    } else {
                        long count = publishedCount.incrementAndGet();
                        statusService.setStatus(infoHash, "SUCCESS");
                        if (count % 10 == 0) {
                            log.info("Published {} metadata to Kafka", count);
                        }
                    }
                });
            
        } catch (Exception e) {
            log.error("Error publishing metadata for InfoHash: {}", infoHash, e);
            publishFailure(infoHash, e.getMessage());
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
            .length(file.getSize())
            .build();
    }

    /**
     * 直接发布从 ut_metadata 获取的原始 info 字典（不再二次下载）。
     * @param infoHash 十六进制 infohash
     * @param rawInfoBytes ut_metadata 拼装完整后的 info 字典原始字节
     */
    public void publishRawInfo(String infoHash, byte[] rawInfoBytes) {
        try {
            // Validate it's a dictionary quickly (optional fast-fail)
            Object decoded = bencode.decode(rawInfoBytes, Type.DICTIONARY);
            if (!(decoded instanceof java.util.Map)) {
                log.warn("Raw info decode not a dict infoHash={}", infoHash);
                return;
            }

            RawInfoParser.RawInfoResult result = rawInfoParser.parse(infoHash, rawInfoBytes);

            TorrentMetadata metadata = TorrentMetadata.builder()
                    .infoHash(infoHash)
                    .name(result.getName() != null ? result.getName() : infoHash)
                    .totalSize(result.getTotalSize())
                    .files(result.getFiles())
                    .fetchedAt(java.time.Instant.now())
                    .status("SUCCESS")
                    .build();

            kafkaTemplate.send(KafkaTopics.METADATA_FETCHED, infoHash, metadata)
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            log.error("Failed publish raw info metadata infoHash={}", infoHash, ex);
                            statusService.setStatus(infoHash, "FAILED");
                        } else {
                            long c = publishedCount.incrementAndGet();
                            statusService.setStatus(infoHash, "SUCCESS");
                            if (c % 10 == 0) {
                                log.info("Published {} metadata (raw) to Kafka", c);
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Publish raw info failed infoHash={} err={}", infoHash, e.getMessage());
            publishFailure(infoHash, e.getMessage());
        }
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Published: %d Failed: %d", publishedCount.get(), failedCount.get());
    }

    /**
     * 发布失败消息到失败主题，并记录状态
     */
    public void publishFailure(String infoHash, String reason) {
        try {
            TorrentMetadata metadata = TorrentMetadata.builder()
                    .infoHash(infoHash)
                    .name(null)
                    .files(java.util.List.of())
                    .totalSize(0L)
                    .fetchedAt(java.time.Instant.now())
                    .status("FAILED")
                    .failureMessage(reason)
                    .build();
            kafkaTemplate.send(KafkaTopics.METADATA_FAILED, infoHash, metadata)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish FAILURE metadata infoHash={} reason={} err={}", infoHash, reason, ex.getMessage());
                        } else {
                            failedCount.incrementAndGet();
                            statusService.setStatus(infoHash, "FAILED");
                            log.info("Published FAILURE metadata infoHash={} reason={}", infoHash, reason);
                        }
                    });
        } catch (Exception e) {
            log.error("Error building FAILURE metadata infoHash={} err={}", infoHash, e.getMessage());
        }
    }
}
