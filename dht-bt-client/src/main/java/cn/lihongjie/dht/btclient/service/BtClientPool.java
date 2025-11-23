package cn.lihongjie.dht.btclient.service;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.metainfo.Torrent;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * BT客户端池
 * 管理多个BtClient实例，实现客户端复用
 */
@Slf4j
@Component
public class BtClientPool {
    
    private final BtRuntime btRuntime;
    private final Path btStoragePath;
    private final int poolSize;
    private final BlockingQueue<PooledClient> availableClients;
    private final AtomicInteger activeClients = new AtomicInteger(0);
    private volatile boolean shutdown = false;
    
    public BtClientPool(BtRuntime btRuntime, Path btStoragePath, int poolSize) {
        this.btRuntime = btRuntime;
        this.btStoragePath = btStoragePath;
        this.poolSize = poolSize;
        this.availableClients = new LinkedBlockingQueue<>(poolSize);
        
        log.info("Initializing BT client pool with size: {}", poolSize);
    }
    
    /**
     * 从池中获取客户端
     */
    public PooledClient acquire(String infoHash, Consumer<Torrent> callback) throws InterruptedException {
        if (shutdown) {
            throw new IllegalStateException("Client pool has been shut down");
        }
        
        PooledClient client = availableClients.poll();
        
        if (client == null && activeClients.get() < poolSize) {
            // 池未满且没有可用客户端，创建新的
            client = createNewClient();
            activeClients.incrementAndGet();
            log.debug("Created new BT client, pool size: {}/{}", activeClients.get(), poolSize);
        } else if (client == null) {
            // 池已满，等待可用客户端
            log.debug("Waiting for available BT client...");
            client = availableClients.poll(30, TimeUnit.SECONDS);
            if (client == null) {
                throw new IllegalStateException("Timeout waiting for available BT client");
            }
        }
        
        // 配置客户端用于此次下载
        client.configure(infoHash, callback);
        return client;
    }
    
    /**
     * 将客户端归还到池中
     */
    public void release(PooledClient client) {
        if (shutdown) {
            client.destroy();
            return;
        }
        
        try {
            client.reset();
            if (!availableClients.offer(client, 5, TimeUnit.SECONDS)) {
                log.warn("Failed to return client to pool, destroying it");
                client.destroy();
                activeClients.decrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            client.destroy();
            activeClients.decrementAndGet();
        }
    }
    
    /**
     * 创建新的客户端
     */
    private PooledClient createNewClient() {
        return new PooledClient(btRuntime, btStoragePath);
    }
    
    /**
     * 关闭客户端池
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down BT client pool...");
        shutdown = true;
        
        PooledClient client;
        while ((client = availableClients.poll()) != null) {
            client.destroy();
        }
        
        log.info("BT client pool shut down, total clients destroyed: {}", activeClients.get());
    }
    
    /**
     * 获取池统计信息
     */
    public PoolStats getStats() {
        return new PoolStats(poolSize, activeClients.get(), availableClients.size());
    }
    
    /**
     * 池化的客户端包装类
     */
    public static class PooledClient {
        private final BtRuntime runtime;
        private final Path storagePath;
        private BtClient client;
        private String currentInfoHash;
        private final Torrent[] torrentHolder = new Torrent[1];
        
        public PooledClient(BtRuntime runtime, Path storagePath) {
            this.runtime = runtime;
            this.storagePath = storagePath;
        }
        
        /**
         * 配置客户端用于新的下载任务
         */
        public void configure(String infoHash, Consumer<Torrent> callback) {
            this.currentInfoHash = infoHash;
            this.torrentHolder[0] = null;
            
            String magnetUri = "magnet:?xt=urn:btih:" + infoHash;
            Storage storage = new FileSystemStorage(storagePath);
            
            this.client = Bt.client(runtime)
                    .storage(storage)
                    .magnet(magnetUri)
                    .afterTorrentFetched(torrent -> {
                        torrentHolder[0] = torrent;
                        if (callback != null) {
                            callback.accept(torrent);
                        }
                    })
                    .build();
        }
        
        /**
         * 启动客户端
         */
        public void start() {
            if (client != null) {
                client.startAsync();
            }
        }
        
        /**
         * 等待元数据获取完成
         */
        public Torrent waitForMetadata(int timeoutSeconds) {
            long startTime = System.currentTimeMillis();
            while (torrentHolder[0] == null && 
                   (System.currentTimeMillis() - startTime) < (timeoutSeconds * 1000L)) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return torrentHolder[0];
        }
        
        /**
         * 停止当前任务
         */
        public void stop() {
            if (client != null) {
                try {
                    client.stop();
                } catch (Exception e) {
                    log.debug("Error stopping BT client", e);
                }
            }
        }
        
        /**
         * 重置客户端状态，准备下次使用
         */
        public void reset() {
            stop();
            this.client = null;
            this.currentInfoHash = null;
            this.torrentHolder[0] = null;
        }
        
        /**
         * 销毁客户端
         */
        public void destroy() {
            stop();
            this.client = null;
        }
        
        public String getCurrentInfoHash() {
            return currentInfoHash;
        }
    }
    
    /**
     * 池统计信息
     */
    public record PoolStats(int poolSize, int activeClients, int availableClients) {
        @Override
        public String toString() {
            return String.format("Pool[size=%d, active=%d, available=%d, inUse=%d]", 
                    poolSize, activeClients, availableClients, activeClients - availableClients);
        }
    }
}
