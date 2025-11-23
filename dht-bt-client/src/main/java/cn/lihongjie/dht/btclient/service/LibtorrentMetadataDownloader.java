package cn.lihongjie.dht.btclient.service;

import lombok.extern.slf4j.Slf4j;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.SessionParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用 libtorrent4j 通过 magnet 异步获取 .torrent 元数据 (info 字典)。
 * 只拉取元数据, 拉取后立即移除 torrent，避免长时间占用资源。
 */
@Service
@Slf4j
public class LibtorrentMetadataDownloader {

    @Value("${libtorrent.metadata.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${libtorrent.metadata.max-concurrent:200}")
    private int maxConcurrent;

    @Value("${libtorrent.metadata.remove-delay-millis:2000}")
    private long removeDelayMillis;

    private SessionManager session;
    private final ConcurrentMap<String, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger active = new AtomicInteger(0);

    private final MetadataPublisher metadataPublisher;
    private final MetadataStatusService statusService;

    public LibtorrentMetadataDownloader(MetadataPublisher metadataPublisher, MetadataStatusService statusService) {
        this.metadataPublisher = metadataPublisher;
        this.statusService = statusService;
        initSession();
    }

    @Value("${libtorrent.listen.port:6891}")
    private int listenPort;

    private void initSession() {
        try {
            session = new SessionManager();
            SettingsPack sp = new SettingsPack();
            sp.listenInterfaces("0.0.0.0:" + listenPort + ",[::]:" + listenPort);
            SessionParams params = new SessionParams(sp);
            session.start(params);
            log.info("Initialized libtorrent SessionManager listenPort={} maxConcurrent={}", listenPort, maxConcurrent);
        } catch (Exception e) {
            log.error("Failed to initialize SessionManager: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步下载 info 字典原始 bencode 数据 (.torrent 的 info 部分)
     * @param infoHashHex 40位十六进制 infohash
     */
    public CompletableFuture<byte[]> downloadAsync(String infoHashHex) {
        Objects.requireNonNull(infoHashHex, "infoHash");
        if (infoHashHex.length() != 40) {
            CompletableFuture<byte[]> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Invalid infoHash length:" + infoHashHex));
            return failed;
        }
        CompletableFuture<byte[]> existing = pending.get(infoHashHex);
        if (existing != null) return existing;
        if (active.get() >= maxConcurrent) {
            CompletableFuture<byte[]> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RejectedExecutionException("Too many active metadata downloads"));
            return failed;
        }
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pending.put(infoHashHex, future);
        active.incrementAndGet();
        statusService.setStatus(infoHashHex, "FETCHING");
        future.whenComplete((r, ex) -> {
            active.decrementAndGet();
            pending.remove(infoHashHex);
            if (ex != null) metadataPublisher.publishFailure(infoHashHex, ex.getMessage());
        });
        vtExecutor.execute(() -> {
            try {
                String magnet = "magnet:?xt=urn:btih:" + infoHashHex;
                byte[] data = session.fetchMagnet(magnet, timeoutSeconds, new java.io.File("."));
                if (data == null) {
                    future.completeExceptionally(new TimeoutException("TIMEOUT"));
                    statusService.setStatus(infoHashHex, "TIMEOUT");
                } else {
                    metadataPublisher.publishRawInfo(infoHashHex, extractInfoDictionary(data));
                    future.complete(data);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void scheduleRemove() { /* no-op retained for future */ }

    /**
     * 从完整 .torrent bencode 提取 info 字典的 bencode 原始字节。
     * 这里简单做法：解析顶层字典再重新 bencode 其 info 部分。
     */
    private byte[] extractInfoDictionary(byte[] fullTorrentBencode) {
        try {
            // 使用简单的低层解析：libtorrent4j 获得的 bencode 已经是完整 .torrent, 我们只需要 info 部分即可
            // 这里为了避免额外依赖，做一个非常轻量的解析: 先转字符串查找 "4:info" 标记。
            // 注意：这是近似实现，若后续需要更严谨解析可改为真正 bencode parser。
            String s = new String(fullTorrentBencode, StandardCharsets.ISO_8859_1);
            int idx = s.indexOf("4:infod"); // info dict starts with '4:info' + 'd'
            if (idx < 0) { return fullTorrentBencode; }
            // 由于缺少长度信息，需要一个简单平衡计数直到匹配到对应的 'e'
            int depth = 0;
            int start = idx + "4:info".length();
            for (int i = start; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == 'd' || c == 'l') {
                    depth++;
                } else if (c == 'e') {
                    depth--;
                    if (depth <= 0) {
                        return s.substring(start, i + 1).getBytes(StandardCharsets.ISO_8859_1);
                    }
                }
            }
            return fullTorrentBencode;
        } catch (Exception e) {
            return fullTorrentBencode; // 回退
        }
    }

    public String getStats() { return "active=" + active.get() + " pending=" + pending.size(); }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("Shutting down LibtorrentMetadataDownloader active={} pending={}", active.get(), pending.size());
        try { session.stop(); } catch (Exception ignored) {}
        scheduler.shutdownNow();
        vtExecutor.shutdownNow();
    }
}
