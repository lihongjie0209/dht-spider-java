package cn.lihongjie.dht.btclient.runner;

import cn.lihongjie.dht.btclient.service.BtClientPool;
import cn.lihongjie.dht.btclient.service.MetadataPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 条件激活的单次下载运行器：用于人工验证下载功能是否正常。
 * 启动参数示例：
 * --download.test.enabled=true --download.test.infohash=CCBD47A30A5A13A5260295E4BD65C038244E9DF0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleDownloadRunner implements CommandLineRunner {

    @Value("${download.test.enabled:false}")
    private boolean enabled;

    @Value("${download.test.infohash:}")
    private String infoHash;

    private final BtClientPool btClientPool;
    private final MetadataPublisher metadataPublisher;

    @Override
    public void run(String... args) throws Exception {
        if (!enabled) {
            return; // not active
        }
        if (infoHash == null || infoHash.isBlank()) {
            log.error("download.test.enabled=true 但未提供 download.test.infohash");
            return;
        }
        log.info("[TEST] 开始单次元数据下载验证 infoHash={}", infoHash);
        BtClientPool.DownloadResult result = btClientPool.download(infoHash, t -> {
            log.info("[TEST] 回调获取到元数据 name={} size={}", t.getName(), t.getSize());
            metadataPublisher.publish(infoHash, t);
        });
        log.info("[TEST] 下载状态 status={} elapsed={}ms", result.status(), result.elapsedMillis());
        if (result.isSuccess()) {
            log.info("[TEST] 下载成功，证明功能正常 name={} totalSize={} files={}",
                    result.torrent().getName(), result.torrent().getSize(), result.torrent().getFiles().size());
        } else {
            log.warn("[TEST] 下载未成功 status={} 可重试或更换 infohash", result.status());
        }
        // 单次验证完成后退出，避免继续运行后台消费者
        System.exit(0);
    }
}
