package cn.lihongjie.dht.btclient.service;

import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import java.nio.charset.StandardCharsets;
import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
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
    private final Bencode bencode = new Bencode();
    
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
            Object decoded = bencode.decode(rawInfoBytes, Type.DICTIONARY);
            if (!(decoded instanceof java.util.Map)) {
                log.warn("Raw info decode not a dict infoHash={}", infoHash);
                return;
            }
            @SuppressWarnings("unchecked") java.util.Map<String,Object> info = (java.util.Map<String,Object>) decoded;
            String name = extractString(info.get("name"));
            long totalSize = 0L;
            List<TorrentMetadata.FileInfo> files;
            Object filesObj = info.get("files");
            if (filesObj instanceof java.util.List) {
                @SuppressWarnings("unchecked") List<Object> list = (List<Object>) filesObj;
                files = new java.util.ArrayList<>();
                for (Object f : list) {
                    if (f instanceof java.util.Map) {
                        @SuppressWarnings("unchecked") java.util.Map<String,Object> fm = (java.util.Map<String,Object>) f;
                        long length = extractNumber(fm.get("length"));
                        totalSize += length;
                        List<String> pathElems = new java.util.ArrayList<>();
                        Object pathObj = fm.get("path");
                        if (pathObj instanceof java.util.List) {
                            for (Object pe : (List<?>) pathObj) {
                                pathElems.add(extractString(pe));
                            }
                        }
                        files.add(TorrentMetadata.FileInfo.builder()
                                .path(String.join("/", pathElems))
                                .length(length)
                                .build());
                    }
                }
            } else { // single-file mode
                long length = extractNumber(info.get("length"));
                totalSize = length;
                files = java.util.List.of(TorrentMetadata.FileInfo.builder()
                        .path(name != null ? name : infoHash)
                        .length(length)
                        .build());
            }
            TorrentMetadata metadata = TorrentMetadata.builder()
                    .infoHash(infoHash)
                    .name(name != null ? name : infoHash)
                    .totalSize(totalSize)
                    .files(files)
                    .fetchedAt(java.time.Instant.now())
                    .build();
            kafkaTemplate.send(KafkaTopics.METADATA_FETCHED, infoHash, metadata)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed publish raw info metadata infoHash={}", infoHash, ex);
                        } else {
                            long c = publishedCount.incrementAndGet();
                            if (c % 10 == 0) {
                                log.info("Published {} metadata (raw) to Kafka", c);
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Publish raw info failed infoHash={} err={}", infoHash, e.getMessage());
        }
    }

    private String extractString(Object v) {
        if (v == null) return null;
        if (v instanceof String) return (String) v;
        if (v instanceof byte[]) return new String((byte[]) v, StandardCharsets.UTF_8);
        return v.toString();
    }
    private long extractNumber(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Published: %d", publishedCount.get());
    }
}
