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
import java.util.concurrent.Semaphore;
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
    private final Semaphore concurrencyLimit = new Semaphore(100); // 默认值，会在PostConstruct中更新
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
            try {
                concurrencyLimit.acquire();
                activeDownloads.incrementAndGet();
                return download(infoHash);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while acquiring semaphore for {}", infoHash);
                return null;
            } finally {
                activeDownloads.decrementAndGet();
                concurrencyLimit.release();
            }
        }, downloadExecutor);
    }
    
    /**
     * 下载元数据（创建独立的BtRuntime和BtClient）
     */
    private Torrent download(String infoHash) {
        log.debug("Starting pooled metadata download for InfoHash: {} (Active: {})", infoHash, activeDownloads.get());
        try {
            Torrent torrent = btClientPool.download(infoHash, t -> {
                log.info("Metadata fetched InfoHash={} name={}", infoHash, t.getName());
                metadataPublisher.publish(infoHash, t);
            });
            if (torrent != null) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
            return torrent;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failureCount.incrementAndGet();
            log.error("Interrupted pooled download InfoHash={}", infoHash);
            return null;
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("Error pooled download InfoHash={}: {}", infoHash, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Active=%d Success=%d Failed=%d SemaphoreAvailable=%d Pool=%s", 
            activeDownloads.get(), successCount.get(), failureCount.get(),
            concurrencyLimit.availablePermits(), btClientPool.getStats());
    }
}
