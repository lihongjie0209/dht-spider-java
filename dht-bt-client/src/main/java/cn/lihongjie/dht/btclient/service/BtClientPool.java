package cn.lihongjie.dht.btclient.service;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.metainfo.Torrent;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 简化的 BT 客户端并发控制：
 * 不再复用 BtClient，会话即建即销；只用信号量限制最大并发。
 */
@Slf4j
@Component
public class BtClientPool {

    private final BtRuntime runtime;
    private final Path storagePath;
    private final int poolSize;
    private final int timeoutSeconds;
    private final Semaphore semaphore;
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger failure = new AtomicInteger();
    private final AtomicInteger inProgress = new AtomicInteger();

    public BtClientPool(BtRuntime runtime,
                        Path storagePath,
                        @Value("${bt.pool.size:100}") int poolSize,
                        @Value("${bt.download.timeout-seconds:60}") int timeoutSeconds) {
        this.runtime = runtime;
        this.storagePath = storagePath;
        this.poolSize = poolSize;
        this.timeoutSeconds = timeoutSeconds;
        this.semaphore = new Semaphore(poolSize);
        log.info("Initialized ephemeral BtClientPool concurrency={} timeoutSeconds={}", poolSize, timeoutSeconds);
    }

    /**
     * 阻塞式执行一次下载，会话结束即销毁 BtClient。
     */
    public Torrent download(String infoHash, Consumer<Torrent> callback) throws InterruptedException {
        semaphore.acquire();
        inProgress.incrementAndGet();
        try {
            String magnet = "magnet:?xt=urn:btih:" + infoHash;
            Storage storage = new FileSystemStorage(storagePath);
            final Torrent[] holder = new Torrent[1];
            BtClient client = Bt.client(runtime)
                    .storage(storage)
                    .magnet(magnet)
                    .afterTorrentFetched(t -> {
                        holder[0] = t;
                        success.incrementAndGet();
                        if (callback != null) {
                            callback.accept(t);
                        }
                        log.info("Fetched metadata infoHash={} name={}", infoHash, t.getName());
                    })
                    .build();

            client.startAsync();
            long start = System.currentTimeMillis();
            while (holder[0] == null && (System.currentTimeMillis() - start) < timeoutSeconds * 1000L) {
                Thread.sleep(100);
            }

            if (holder[0] == null) {
                failure.incrementAndGet();
                log.warn("Timeout/no peers infoHash={}", infoHash);
            }
            return holder[0];
        } catch (Exception e) {
            failure.incrementAndGet();
            log.error("Error downloading metadata infoHash={}: {}", infoHash, e.getMessage());
            return null;
        } finally {
            inProgress.decrementAndGet();
            semaphore.release();
        }
    }

    public String getStats() {
        return String.format("poolSize=%d available=%d inProgress=%d success=%d failure=%d", poolSize, semaphore.availablePermits(), inProgress.get(), success.get(), failure.get());
    }
}
