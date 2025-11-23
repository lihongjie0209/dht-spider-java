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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    private final AtomicInteger timeout = new AtomicInteger();
    private final AtomicInteger noPeers = new AtomicInteger();
    private final AtomicInteger error = new AtomicInteger();
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
     * 阻塞式执行一次下载，会话结束即销毁 BtClient，返回结果状态。
     */
    public DownloadResult download(String infoHash, Consumer<Torrent> callback) throws InterruptedException {
        semaphore.acquire();
        inProgress.incrementAndGet();
        long start = System.currentTimeMillis();
        DownloadStatus status;
        Torrent torrent = null;
        Throwable err = null;
        try {
            String magnet = "magnet:?xt=urn:btih:" + infoHash;
            Storage storage = new FileSystemStorage(storagePath);
            final Torrent[] holder = new Torrent[1];
            CountDownLatch latch = new CountDownLatch(1);
            BtClient client = Bt.client(runtime)
                    .storage(storage)
                    .magnet(magnet)
                    .afterTorrentFetched(t -> {
                        holder[0] = t;
                        if (callback != null) {
                            callback.accept(t);
                        }
                        latch.countDown();
                        log.info("Fetched metadata infoHash={} name={}", infoHash, t.getName());
                    })
                    .build();

            client.startAsync();
            boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            torrent = holder[0];
            if (!completed) {
                status = DownloadStatus.TIMEOUT;
                timeout.incrementAndGet();
                log.warn("Timeout waiting metadata infoHash={}", infoHash);
            } else if (torrent == null) {
                status = DownloadStatus.NO_PEERS; // 回调没触发但 latch 结束(极少情况)
                noPeers.incrementAndGet();
                log.warn("No peers metadata infoHash={}", infoHash);
            } else {
                status = DownloadStatus.SUCCESS;
                success.incrementAndGet();
            }
            try { client.stop(); } catch (Exception ignore) {}
        } catch (Exception e) {
            status = DownloadStatus.ERROR;
            error.incrementAndGet();
            err = e;
            log.error("Error downloading metadata infoHash={}: {}", infoHash, e.getMessage());
        } finally {
            inProgress.decrementAndGet();
            semaphore.release();
        }
        long elapsed = System.currentTimeMillis() - start;
        return new DownloadResult(infoHash, torrent, status, elapsed, err);
    }

    public String getStats() {
        return String.format(
                "poolSize=%d available=%d inProgress=%d success=%d timeout=%d noPeers=%d error=%d",
                poolSize, semaphore.availablePermits(), inProgress.get(), success.get(), timeout.get(), noPeers.get(), error.get());
    }

    public enum DownloadStatus { SUCCESS, TIMEOUT, NO_PEERS, ERROR }

    public record DownloadResult(String infoHash, Torrent torrent, DownloadStatus status,
                                 long elapsedMillis, Throwable error) {
        public boolean isSuccess() { return status == DownloadStatus.SUCCESS; }
    }
}
