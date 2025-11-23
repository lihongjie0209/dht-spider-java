# BT Metadata 下载示例

这是一个独立的示例程序，演示如何使用 [atomashpolskiy/bt](https://github.com/atomashpolskiy/bt) 库直接下载 BitTorrent metadata。

## 示例说明

`SimpleBtMetadataDownloader` 展示了完整的 metadata 下载流程：

1. **创建 BtRuntime**：配置 DHT 模块用于发现 peers
2. **构建 BtClient**：使用 magnet link
3. **注册回调**：`afterTorrentFetched` 在 metadata 下载完成时触发
4. **启动客户端**：异步启动并等待完成
5. **打印信息**：显示 torrent 名称、大小、文件列表等

## ⚠️ 网络要求

**重要**：此示例需要能访问公网的网络连接。

### 常见网络问题

如果遇到 `Network is unreachable` 错误且本地 IP 是 `169.254.x.x`：
- 这是 APIPA 自动分配地址，表示网卡未获取有效 DHCP 地址
- 需要使用有网关的网络接口（如 WiFi 或有线以太网）
- 检查网络：`ipconfig /all`（Windows）或 `ifconfig`（Linux/Mac）

### 禁用不需要的虚拟网卡（可选）

如果有多个网卡（VMware/VirtualBox/Hyper-V），可暂时禁用虚拟网卡以避免选择错误：
- 控制面板 → 网络和共享中心 → 更改适配器设置
- 右键禁用 VMware/VirtualBox 虚拟网卡

## 运行方式

### 1. 使用 Maven exec 插件（推荐）

```bash
# 编译并运行（使用默认 InfoHash）
mvn exec:java -pl dht-bt-client \
  -Dexec.mainClass="cn.lihongjie.dht.btclient.example.SimpleBtMetadataDownloader"

# 指定 InfoHash
mvn exec:java -pl dht-bt-client \
  -Dexec.mainClass="cn.lihongjie.dht.btclient.example.SimpleBtMetadataDownloader" \
  -Dexec.args="CCBD47A30A5A13A5260295E4BD65C038244E9DF0"
```

### 2. 直接编译运行

```bash
# 编译项目
mvn clean package -DskipTests -pl dht-bt-client -am

# 注意：Spring Boot 打包后需要使用 -jar 方式运行主类
java -jar dht-bt-client/target/dht-bt-client-1.0.0-SNAPSHOT.jar
```

### 3. 在 IDE 中运行

直接运行 `SimpleBtMetadataDownloader.main()` 方法。

## 预期输出

```
============================================================
简单 BT Metadata 下载器
============================================================
InfoHash: CCBD47A30A5A13A5260295E4BD65C038244E9DF0

✓ BtRuntime 已创建（DHT 已启用）
✓ 存储目录: /path/to/bt-downloads
✓ Magnet Link: magnet:?xt=urn:btih:CCBD47A30A5A13A5260295E4BD65C038244E9DF0

▶ 开始下载 metadata...
  (这可能需要 10-60 秒，取决于网络和 peers 可用性)

✓ Metadata 下载成功！耗时: 15234 ms

────────────────────────────────────────────────────────────
Torrent 信息
────────────────────────────────────────────────────────────
名称:        ubuntu-20.04.3-desktop-amd64.iso
总大小:      3.05 GB
Chunk 大小:  2.00 MB
Chunks 数量: 1563
类型:        单文件种子

文件列表:
────────────────────────────────────────────────────────────
  [1] ubuntu-20.04.3-desktop-amd64.iso (3.05 GB)
────────────────────────────────────────────────────────────

程序结束。
============================================================
```

## 核心代码解析

### 1. 创建 BtRuntime（启用 DHT）

```java
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
```

### 2. 构建客户端并注册回调

```java
BtClient client = Bt.client(runtime)
        .storage(storage)
        .magnet(magnet)
        .afterTorrentFetched(torrent -> {
            // metadata 下载完成时的回调
            System.out.println("名称: " + torrent.getName());
            System.out.println("大小: " + torrent.getSize());
            // 处理 torrent 信息...
        })
        .build();
```

### 3. 启动并等待完成

```java
client.startAsync();

// 等待 metadata 下载（最多 120 秒）
boolean success = metadataLatch.await(120, TimeUnit.SECONDS);

client.stop();
runtime.shutdown();
```

## 关键配置

### DHT Bootstrap 节点

BT 库会自动使用以下公共 DHT bootstrap 节点：
- `router.bittorrent.com:6881`
- `dht.transmissionbt.com:6881`
- `router.utorrent.com:6881`

### 超时设置

默认超时 **120 秒**。可根据网络情况调整：

```java
metadataLatch.await(180, TimeUnit.SECONDS); // 3 分钟
```

### 存储目录

默认保存到 `./bt-downloads`。可自定义：

```java
Path storagePath = Paths.get("/custom/path");
Storage storage = new FileSystemStorage(storagePath);
```

## 常见问题

### 1. 下载超时

**原因**：
- InfoHash 没有活跃的 peers
- 防火墙阻止 DHT/BitTorrent 流量（UDP 端口）
- NAT 穿透失败

**解决**：
- 使用知名种子测试（如 Ubuntu/Debian 镜像）
- 检查防火墙/路由器设置
- 延长超时时间
- 确保 UDP 端口未被占用

### 2. 只获取到 metadata，不下载完整文件

这是**正常行为**。示例程序只下载 **metadata**（torrent 信息），不下载实际文件内容。

如需下载完整文件，参考 [bt 官方示例](https://github.com/atomashpolskiy/bt/tree/master/examples)。

### 3. 如何测试？

推荐使用活跃的公开种子：

```bash
# Ubuntu 20.04 Desktop
java -cp ... SimpleBtMetadataDownloader \
  CCBD47A30A5A13A5260295E4BD65C038244E9DF0

# Debian 11 ISO
java -cp ... SimpleBtMetadataDownloader \
  <debian-infohash>
```

## 参考资料

- [BT 库官方文档](https://github.com/atomashpolskiy/bt)
- [BT 库示例](https://github.com/atomashpolskiy/bt/tree/master/examples)
- [BitTorrent 协议 (BEP 3)](http://www.bittorrent.org/beps/bep_0003.html)
- [Magnet URI 协议 (BEP 9)](http://www.bittorrent.org/beps/bep_0009.html)
- [DHT 协议 (BEP 5)](http://www.bittorrent.org/beps/bep_0005.html)

## 下一步

- 修改代码保存 metadata 到文件
- 添加多个 InfoHash 批量下载
- 集成到生产环境（如当前项目的 `BtClientPool`）
- 添加重试机制和错误处理
