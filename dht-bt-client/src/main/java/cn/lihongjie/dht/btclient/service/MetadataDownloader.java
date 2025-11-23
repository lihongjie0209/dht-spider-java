package cn.lihongjie.dht.btclient.service;

import bt.metainfo.Torrent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 元数据下载器
 * 每次下载创建独立的BtRuntime和BtClient，避免生命周期冲突
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataDownloader {

    private final MetadataPublisher metadataPublisher;
    private final BtClientPool btClientPool;
    
    @Value("${bt.download.timeout-seconds:60}")
    private int timeoutSeconds;
    
    @Value("${download.concurrent.max:100}")
    private int maxConcurrent;
    
    private final ExecutorService downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger activeDownloads = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    @jakarta.annotation.PostConstruct
    public void init() {
        // 更新并发限制
        log.info("Initialized MetadataDownloader with max concurrent downloads: {}", maxConcurrent);
    }
    
    /**
     * 异步下载元数据
     */
    public CompletableFuture<Torrent> downloadAsync(String infoHash) {
        return CompletableFuture.supplyAsync(() -> {
            activeDownloads.incrementAndGet();
            try {
                return download(infoHash);
            } finally {
                activeDownloads.decrementAndGet();
            }
        }, downloadExecutor);
    }
    
    /**
     * 下载元数据（创建独立的BtRuntime和BtClient）
     */
    private Torrent download(String infoHash) {
        log.debug("Starting pooled metadata download for InfoHash: {} (Active: {})", infoHash, activeDownloads.get());
        try {
            BtClientPool.DownloadResult result = btClientPool.download(infoHash, t -> {
                log.debug("Callback publishing metadata infoHash={}", infoHash);
                metadataPublisher.publish(infoHash, t);
            });
            switch (result.status()) {
                case SUCCESS -> {
                    successCount.incrementAndGet();
                    log.debug("Download success infoHash={} elapsed={}ms", infoHash, result.elapsedMillis());
                }
                case TIMEOUT -> {
                    failureCount.incrementAndGet();
                    log.warn("Download timeout infoHash={} elapsed={}ms", infoHash, result.elapsedMillis());
                }
                case NO_PEERS -> {
                    failureCount.incrementAndGet();
                    log.warn("Download no-peers infoHash={} elapsed={}ms", infoHash, result.elapsedMillis());
                }
                case ERROR -> {
                    failureCount.incrementAndGet();
                    log.error("Download error infoHash={} elapsed={}ms err={}", infoHash, result.elapsedMillis(),
                            result.error() != null ? result.error().getMessage() : "unknown");
                }
            }
            return result.torrent();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failureCount.incrementAndGet();
            log.error("Interrupted download infoHash={}", infoHash);
            return null;
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("Unexpected error download infoHash={}: {}", infoHash, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Active=%d Success=%d Failed=%d Pool=%s", 
            activeDownloads.get(), successCount.get(), failureCount.get(), btClientPool.getStats());
    }
}
