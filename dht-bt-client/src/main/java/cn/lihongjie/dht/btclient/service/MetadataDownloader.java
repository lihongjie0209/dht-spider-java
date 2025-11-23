package cn.lihongjie.dht.btclient.service;

import bt.metainfo.Torrent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 元数据下载器
 * 使用BT客户端池从Magnet链接下载torrent元数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataDownloader {
    
    private final BtClientPool clientPool;
    private final MetadataPublisher metadataPublisher;
    
    @Value("${bt.download.timeout-seconds:60}")
    private int timeoutSeconds;
    
    private final AtomicInteger activeDownloads = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    /**
     * 异步下载元数据
     */
    public CompletableFuture<Torrent> downloadAsync(String infoHash) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                activeDownloads.incrementAndGet();
                return download(infoHash);
            } finally {
                activeDownloads.decrementAndGet();
            }
        });
    }
    
    /**
     * 下载元数据
     */
    private Torrent download(String infoHash) {
        log.debug("Starting metadata download for InfoHash: {} (Pool stats: {})", 
                infoHash, clientPool.getStats());
        
        BtClientPool.PooledClient client = null;
        try {
            // 从池中获取客户端
            client = clientPool.acquire(infoHash, torrent -> {
                log.info("Metadata fetched for InfoHash: {}, name: {}", infoHash, torrent.getName());
                // 发布元数据
                metadataPublisher.publish(infoHash, torrent);
            });
            
            // 启动客户端
            client.start();
            
            // 等待元数据获取完成或超时
            Torrent torrent = client.waitForMetadata(timeoutSeconds);
            
            if (torrent != null) {
                successCount.incrementAndGet();
                log.debug("Successfully downloaded metadata for InfoHash: {}", infoHash);
                return torrent;
            } else {
                failureCount.incrementAndGet();
                log.warn("Failed to fetch metadata for InfoHash: {} (timeout or no peers)", infoHash);
                return null;
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failureCount.incrementAndGet();
            log.error("Interrupted while downloading metadata for InfoHash: {}", infoHash);
            return null;
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("Error downloading metadata for InfoHash: {}", infoHash, e);
            return null;
        } finally {
            // 将客户端归还到池中
            if (client != null) {
                clientPool.release(client);
            }
        }
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("Active: %d, Success: %d, Failed: %d, Pool: %s", 
                           activeDownloads.get(), successCount.get(), failureCount.get(),
                           clientPool.getStats());
    }
}
