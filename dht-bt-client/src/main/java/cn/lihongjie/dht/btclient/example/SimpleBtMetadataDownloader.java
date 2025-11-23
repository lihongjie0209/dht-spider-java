package cn.lihongjie.dht.btclient.example;

import bt.Bt;
import bt.data.Storage;
import bt.data.file.FileSystemStorage;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.metainfo.Torrent;
import bt.metainfo.TorrentFile;
import bt.runtime.BtClient;
import bt.runtime.BtRuntime;
import bt.runtime.Config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 简单的 BT Metadata 下载示例
 * 
 * 演示如何使用 atomashpolskiy/bt 库直接下载 torrent metadata
 * 
 * 使用步骤：
 * 1. 创建 BtRuntime（配置 DHT）
 * 2. 使用 magnet link 构建 BtClient
 * 3. 注册 afterTorrentFetched 回调获取 metadata
 * 4. 启动客户端并等待下载完成
 * 5. 打印 torrent 信息
 * 
 * 参考：https://github.com/atomashpolskiy/bt/tree/master
 */
public class SimpleBtMetadataDownloader {

    public static void main(String[] args) throws Exception {
        // 示例 InfoHash（Ubuntu 镜像种子）
        String infoHash = "CCBD47A30A5A13A5260295E4BD65C038244E9DF0";
        
        // 如果命令行提供了 infohash，使用它
        if (args.length > 0) {
            infoHash = args[0];
        }
        
        System.out.println("=".repeat(60));
        System.out.println("简单 BT Metadata 下载器");
        System.out.println("=".repeat(60));
        System.out.println("InfoHash: " + infoHash);
        System.out.println();

        // 1. 配置并创建 BtRuntime
        Config config = new Config() {
            @Override
            public int getNumOfHashingThreads() {
                return Runtime.getRuntime().availableProcessors();
            }
        };

        // 配置 DHT（用于发现 peers）
        DHTModule dhtModule = new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                return true; // 使用公共 DHT bootstrap 节点
            }
        });

        BtRuntime runtime = BtRuntime.builder(config)
                .module(dhtModule)
                .autoLoadModules()
                .build();

        System.out.println("✓ BtRuntime 已创建（DHT 已启用）");

        // 2. 准备存储目录
        Path storagePath = Paths.get("./bt-downloads");
        File storageDir = storagePath.toFile();
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        Storage storage = new FileSystemStorage(storagePath);
        
        System.out.println("✓ 存储目录: " + storagePath.toAbsolutePath());

        // 3. 构建 magnet link
        String magnet = "magnet:?xt=urn:btih:" + infoHash;
        System.out.println("✓ Magnet Link: " + magnet);
        System.out.println();

        // 4. 使用 CountDownLatch 等待 metadata 下载完成
        CountDownLatch metadataLatch = new CountDownLatch(1);
        final Torrent[] torrentHolder = new Torrent[1];
        final long startTime = System.currentTimeMillis();

        // 5. 构建 BtClient
        BtClient client = Bt.client(runtime)
                .storage(storage)
                .magnet(magnet)
                .afterTorrentFetched(torrent -> {
                    // 回调：metadata 已获取
                    torrentHolder[0] = torrent;
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println();
                    System.out.println("✓ Metadata 下载成功！耗时: " + elapsed + " ms");
                    System.out.println();
                    printTorrentInfo(torrent);
                    metadataLatch.countDown();
                })
                .build();

        System.out.println("▶ 开始下载 metadata...");
        System.out.println("  (这可能需要 10-60 秒，取决于网络和 peers 可用性)");
        System.out.println();

        // 6. 启动客户端（异步）
        client.startAsync(state -> {
            // 可选：监听状态变化
            if (state.getPiecesRemaining() == 0) {
                System.out.println("  [状态] Pieces 完成: " + state.getPiecesComplete() + "/" + state.getPiecesTotal());
            }
        }, 1000);

        // 7. 等待 metadata 下载完成（最多 2 分钟）
        boolean success = metadataLatch.await(120, TimeUnit.SECONDS);

        if (!success) {
            System.err.println();
            System.err.println("✗ 下载超时（120秒）");
            System.err.println();
            System.err.println("可能原因：");
            System.err.println("  1. InfoHash 没有活跃的 peers");
            System.err.println("  2. 网络防火墙阻止了 DHT/BitTorrent 流量");
            System.err.println("  3. 本地 UDP 端口被占用或受限");
            System.err.println();
            System.err.println("建议：");
            System.err.println("  - 尝试其他知名的 InfoHash（如 Ubuntu/Debian 镜像）");
            System.err.println("  - 检查防火墙设置");
            System.err.println("  - 延长超时时间");
        }

        // 8. 停止客户端并关闭 runtime
        try {
            client.stop();
        } catch (Exception e) {
            // ignore
        }

        runtime.shutdown();
        
        System.out.println();
        System.out.println("程序结束。");
        System.out.println("=".repeat(60));
        
        System.exit(success ? 0 : 1);
    }

    /**
     * 打印 torrent 元数据信息
     */
    private static void printTorrentInfo(Torrent torrent) {
        System.out.println("─".repeat(60));
        System.out.println("Torrent 信息");
        System.out.println("─".repeat(60));
        System.out.println("名称:        " + torrent.getName());
        System.out.println("总大小:      " + formatSize(torrent.getSize()));
        System.out.println("Chunk 大小:  " + formatSize(torrent.getChunkSize()));
        
        // 计算 chunk 数量 (Iterable 没有 size() 方法，需要遍历计数)
        long chunkCount = 0;
        for (byte[] hash : torrent.getChunkHashes()) {
            chunkCount++;
        }
        System.out.println("Chunks 数量: " + chunkCount);
        
        if (torrent.getFiles().size() == 1) {
            System.out.println("类型:        单文件种子");
        } else {
            System.out.println("类型:        多文件种子");
            System.out.println("文件数量:    " + torrent.getFiles().size());
        }

        System.out.println();
        System.out.println("文件列表:");
        System.out.println("─".repeat(60));
        
        int fileCount = 0;
        for (TorrentFile file : torrent.getFiles()) {
            fileCount++;
            String pathStr = String.join("/", file.getPathElements());
            System.out.printf("  [%d] %s (%s)%n", 
                fileCount, 
                pathStr, 
                formatSize(file.getSize())
            );
            
            // 只显示前 10 个文件，避免输出过长
            if (fileCount >= 10 && torrent.getFiles().size() > 10) {
                System.out.println("  ... 还有 " + (torrent.getFiles().size() - 10) + " 个文件");
                break;
            }
        }
        System.out.println("─".repeat(60));
    }

    /**
     * 格式化文件大小
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024L * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
