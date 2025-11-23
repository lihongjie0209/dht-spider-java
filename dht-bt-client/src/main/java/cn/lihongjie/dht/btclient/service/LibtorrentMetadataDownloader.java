package cn.lihongjie.dht.btclient.service;

import lombok.extern.slf4j.Slf4j;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.SessionParams;
import org.libtorrent4j.AddTorrentParams;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.AlertListener;
import org.libtorrent4j.alerts.AddTorrentAlert;
import org.libtorrent4j.alerts.MetadataReceivedAlert;
import org.libtorrent4j.alerts.SaveResumeDataAlert;
import org.libtorrent4j.TorrentFlags;
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
    private final ConcurrentMap<String, TorrentHandle> handles = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicInteger active = new AtomicInteger(0);

    private final AlertListener alertListener = new AlertListener() {
        @Override
        public int[] types() { return new int[0]; } // receive all alerts

        @Override
        public void alert(Alert alert) {
            try {
                if (alert instanceof AddTorrentAlert ata) {
                    TorrentHandle h = ata.handle();
                    String ih = h.infoHash().toHex();
                    handles.putIfAbsent(ih, h);
                } else if (alert instanceof MetadataReceivedAlert mra) {
                    TorrentHandle h = mra.handle();
                    String ih = h.infoHash().toHex();
                    CompletableFuture<byte[]> future = pending.get(ih);
                    if (future != null && !future.isDone()) {
                        try {
                            // Request resume data containing the info dictionary once metadata is received
                            h.saveResumeData(TorrentHandle.SAVE_INFO_DICT);
                            statusService.setStatus(ih, "METADATA");
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                            statusService.setStatus(ih, "FAILED");
                        }
                    }
                } else if (alert instanceof SaveResumeDataAlert srda) {
                    AddTorrentParams atp = srda.params();
                    TorrentHandle h = srda.handle();
                    String ih = h.infoHash().toHex();
                    CompletableFuture<byte[]> future = pending.get(ih);
                    if (future != null && !future.isDone()) {
                        try {
                            byte[] resume = AddTorrentParams.writeResumeDataBuf(atp);
                            metadataPublisher.publishRawInfo(ih, extractInfoDictionary(resume));
                            statusService.setStatus(ih, "SUCCESS");
                            future.complete(resume);
                            scheduleRemoval(h, ih);
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                            statusService.setStatus(ih, "FAILED");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Alert processing error: {}", e.getMessage(), e);
            }
        }
    };

    private final MetadataPublisher metadataPublisher;
    private final MetadataStatusService statusService;

    public LibtorrentMetadataDownloader(MetadataPublisher metadataPublisher, MetadataStatusService statusService) {
        this.metadataPublisher = metadataPublisher;
        this.statusService = statusService;
        initSession();
    }

    @Value("${libtorrent.listen.port:6891}")
    private int listenPort;

    private volatile boolean available = true;

    private void initSession() {
        try {
            session = new SessionManager();
            SettingsPack sp = new SettingsPack();
            sp.listenInterfaces("0.0.0.0:" + listenPort + ",[::]:" + listenPort);
            sp.setEnableDht(true);
            sp.setAnnouncePort(listenPort);
            SessionParams params = new SessionParams(sp);
            session.start(params);
            session.addListener(alertListener);
            log.info("Initialized libtorrent SessionManager listenPort={} maxConcurrent={}", listenPort, maxConcurrent);
        } catch (Throwable e) {
            available = false;
            log.error("Libtorrent native load failed, downloader disabled: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步下载 info 字典原始 bencode 数据 (.torrent 的 info 部分)
     * @param infoHashHex 40位十六进制 infohash
     */
    public CompletableFuture<byte[]> downloadAsync(String infoHashHex) {
        Objects.requireNonNull(infoHashHex, "infoHash");
        if (!available) {
            CompletableFuture<byte[]> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("libtorrent native library unavailable"));
            return failed;
        }
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
            handles.remove(infoHashHex);
            if (ex != null) metadataPublisher.publishFailure(infoHashHex, ex.getMessage());
        });
        try {
            String magnet = "magnet:?xt=urn:btih:" + infoHashHex;
            // Use SessionManager.download to add magnet with flags suitable for metadata-only acquisition
            TorrentFlags.UPLOAD_MODE.or_(TorrentFlags.STOP_WHEN_READY); // ensure objects loaded
            session.download(magnet, new java.io.File(System.getProperty("java.io.tmpdir")), TorrentFlags.UPLOAD_MODE.or_(TorrentFlags.STOP_WHEN_READY));
            // schedule timeout
            scheduler.schedule(() -> {
                CompletableFuture<byte[]> f = pending.get(infoHashHex);
                if (f != null && !f.isDone()) {
                    f.completeExceptionally(new TimeoutException("TIMEOUT"));
                    statusService.setStatus(infoHashHex, "TIMEOUT");
                    TorrentHandle h = handles.get(infoHashHex);
                    if (h != null) {
                        try { session.remove(h); } catch (Exception ignored) {}
                    }
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private void scheduleRemoval(TorrentHandle h, String infoHashHex) {
        scheduler.schedule(() -> {
            try {
                if (handles.get(infoHashHex) == h) {
                    session.remove(h);
                    handles.remove(infoHashHex);
                }
            } catch (Exception ex) {
                log.warn("Failed to remove torrent {}: {}", infoHashHex, ex.getMessage());
            }
        }, removeDelayMillis, TimeUnit.MILLISECONDS);
    }

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
    }
}
