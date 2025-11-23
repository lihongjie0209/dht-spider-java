package cn.lihongjie.dht.btclient;

import bt.runtime.BtRuntime;
import bt.metainfo.Torrent;
import cn.lihongjie.dht.btclient.service.BtClientPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 简单的磁力链接元数据下载集成测试。
 * 依赖公网 DHT/Peers，可能会有偶发超时；用于人工验证功能。
 */
public class MetadataDownloadIntegrationTest {

    // Ubuntu 25.10 live server amd64 ISO infohash (用户提供)
    private static final String INFO_HASH = "CCBD47A30A5A13A5260295E4BD65C038244E9DF0";

    @Test
    @DisplayName("下载磁力链接元数据并验证基本字段")
    void downloadMagnetMetadata() throws Exception {
        // 临时存储目录（仅用于 bt-core 运行要求，不会实际写文件内容）
        Path storage = Files.createTempDirectory("bt-meta-test");

        // 构建最小化 BtRuntime
        BtRuntime runtime = BtRuntime.builder().build();

        // 创建池（单次下载），超时时间 45 秒以适配公网波动
        BtClientPool pool = new BtClientPool(runtime, storage, 1, 45);

        BtClientPool.DownloadResult result = pool.download(INFO_HASH, t -> {});

        Assertions.assertNotNull(result, "下载结果不应为 null");
        org.junit.jupiter.api.Assumptions.assumeTrue(result.isSuccess(), "跳过：当前状态=" + result.status());
        Torrent torrent = result.torrent();
        Assertions.assertNotNull(torrent, "Torrent 对象为 null");
        Assertions.assertNotNull(torrent.getName(), "Torrent 名称为空");
        Assertions.assertTrue(torrent.getSize() > 0, "总大小无效");
        Assertions.assertFalse(torrent.getFiles().isEmpty(), "文件列表为空");
    }
}
