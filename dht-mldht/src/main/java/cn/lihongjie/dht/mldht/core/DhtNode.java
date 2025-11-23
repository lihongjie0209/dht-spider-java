package cn.lihongjie.dht.mldht.core;

import cn.lihongjie.dht.common.model.InfoHashMessage;
import cn.lihongjie.dht.mldht.service.InfoHashPublisher;
import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.DHTtype;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.messages.AnnounceRequest;
import lbms.plugins.mldht.kad.PeerAddressDBItem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DHT节点
 * 基于 the8472/mldht 库实现
 */
@Slf4j
@Getter
public class DhtNode implements AutoCloseable {
    
    private final int nodeIndex;
    private final int port;
    private final byte[] nodeId;
    private final List<String> bootstrapNodes;
    private final InfoHashPublisher publisher;
    
    private DHT dht;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong discoveredCount = new AtomicLong(0);
    private final AtomicLong messageCount = new AtomicLong(0);
    
    public DhtNode(int nodeIndex, int port, byte[] nodeId, 
                   List<String> bootstrapNodes, InfoHashPublisher publisher) {
        this.nodeIndex = nodeIndex;
        this.port = port;
        this.nodeId = nodeId;
        this.bootstrapNodes = bootstrapNodes;
        this.publisher = publisher;
    }
    
    /**
     * 启动DHT节点
     */
    public void start() throws Exception {
        if (running.get()) {
            log.warn("Node {} is already running on port {}", nodeIndex, port);
            return;
        }
        
        // 创建DHT配置
        DHTConfiguration config = new DHTConfiguration() {
            @Override
            public boolean isPersistingID() {
                return false; // 不持久化节点ID
            }
            
            @Override
            public Path getStoragePath() {
                return Paths.get("./dht-data/node-" + nodeIndex);
            }
            
            @Override
            public int getListeningPort() {
                return port;
            }
            
            @Override
            public boolean noRouterBootstrap() {
                // 启用自动bootstrap，MLDHT会自动连接到硬编码的公共DHT节点：
                // - dht.transmissionbt.com:6881
                // - router.bittorrent.com:6881
                // - router.utorrent.com:6881
                // - router.silotis.us:6881
                return false;
            }
            
            @Override
            public boolean allowMultiHoming() {
                // 禁用 multihoming，允许在 NAT/Docker 环境中绑定到任意本地地址
                // 如果为 true，MLDHT 只会绑定到公网 IP，在内网环境下无法创建服务器
                return false;
            }
        };
        
        // 创建DHT实例（IPv4）
        dht = new DHT(DHTtype.IPV4_DHT);
        dht.setLogLevel(LogLevel.Error); // 只记录错误日志，减少RPC噪音
        
        // 如果指定了NodeId，使用指定的ID
        if (nodeId != null && nodeId.length == 20) {
            // mldht库会自动处理NodeId
            log.debug("Node {} will use custom NodeId", nodeIndex);
        }
        
        // 注册消息监听器 (必须在 start() 前注册)
        dht.addIncomingMessageListener((d, m) -> {
            messageCount.incrementAndGet();
            try {
                if (m instanceof AnnounceRequest announce) {
                    byte[] infoHashBytes = announce.getTarget().getHash();
                    if (infoHashBytes != null && infoHashBytes.length == 20) {
                        String infoHash = bytesToHex(infoHashBytes);
                        InetSocketAddress origin = announce.getOrigin();
                        onInfoHashDiscovered(infoHash, origin.getAddress(), origin.getPort());
                    }
                }
                if (messageCount.get() % 1000 == 0) {
                    log.debug("Node {} processed {} messages, discovered {} InfoHashes", nodeIndex, messageCount.get(), discoveredCount.get());
                }
            } catch (Exception e) {
                log.error("Node {} error processing message", nodeIndex, e);
            }
        });

        // 启动DHT（会自动触发 bootstrap 流程）
        dht.start(config);
        
        // 等待DHT初始化和服务器绑定
        log.info("Node {} waiting for DHT initialization...", nodeIndex);
        Thread.sleep(3000);
        
        // 检查服务器状态
        int serverCount = dht.getServerManager().getServerCount();
        int activeServerCount = dht.getServerManager().getActiveServerCount();
        
        log.info("DHT Node {} started - Port: {}, Type: {}, Running: {}, Servers: {}/{}, RoutingTableSize: {}", 
                 nodeIndex, port, dht.getType(), dht.isRunning(), 
                 activeServerCount, serverCount,
                 dht.getNode().getNumEntriesInRoutingTable());
        
        if (serverCount == 0) {
            log.error("Node {} CRITICAL: No RPC servers created! DHT cannot bind to port {}. Check firewall/permissions.", 
                      nodeIndex, port);
        }
        
        running.set(true);
        
        // 等待DHT运行（阻塞当前线程）
        int statsInterval = 0;
        while (running.get()) {
            try {
                Thread.sleep(1000);
                statsInterval++;
                
                // 每30秒输出统计信息
                if (statsInterval % 30 == 0) {
                    log.info("Node {} Stats: Running={}, Messages={}, Discovered={}", 
                             nodeIndex, dht.isRunning(), messageCount.get(), discoveredCount.get());
                }
                
                // 检查DHT状态
                if (!dht.isRunning()) {
                    log.error("Node {} DHT stopped unexpectedly", nodeIndex);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 当发现新的InfoHash时调用
     */
    private void onInfoHashDiscovered(String infoHash, InetAddress sourceIp, int sourcePort) {
        discoveredCount.incrementAndGet();
        
        // 创建消息
        InfoHashMessage message = InfoHashMessage.builder()
            .infoHash(infoHash)
            .discoveredAt(Instant.now())
            .sourceIp(sourceIp.getHostAddress())
            .sourcePort(sourcePort)
            .build();
        
        // 发布到Kafka
        publisher.publish(message);
        
        if (discoveredCount.get() % 10 == 0) {
            log.info("Node {} discovered {} InfoHashes (latest: {})", 
                     nodeIndex, discoveredCount.get(), infoHash);
        }
    }
    
    /**
     * 停止DHT节点
     */
    public void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        if (dht != null && dht.isRunning()) {
            dht.stop();
        }
        
        log.info("Node {} stopped. Discovered {} InfoHashes from {} messages", 
                 nodeIndex, discoveredCount.get(), messageCount.get());
    }
    
    @Override
    public void close() {
        stop();
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
    
    /**
     * 获取节点统计信息
     */
    public String getStats() {
        int numPeers = 0;
        if (dht != null && dht.isRunning()) {
            numPeers = dht.getServerManager().getActiveServerCount();
        }
        
        return String.format("Node[%d] port=%d, running=%s, servers=%d, messages=%d, discovered=%d", 
                           nodeIndex, port, dht != null && dht.isRunning(), 
                           numPeers, messageCount.get(), discoveredCount.get());
    }
}
