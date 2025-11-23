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
                return false;
            }
            
            @Override
            public boolean allowMultiHoming() {
                return true;
            }
        };
        
        // 创建DHT实例（IPv4）
        dht = new DHT(DHTtype.IPV4_DHT);
        dht.setLogLevel(LogLevel.Info);
        
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

        // 启动DHT
        dht.start(config);
        
        // 添加bootstrap节点
        for (String bootstrapNode : bootstrapNodes) {
            try {
                String[] parts = bootstrapNode.split(":");
                String host = parts[0];
                int bootstrapPort = Integer.parseInt(parts[1]);
                
                InetSocketAddress address = new InetSocketAddress(host, bootstrapPort);
                dht.addDHTNode(address.getHostString(), bootstrapPort);
                
                log.debug("Node {} added bootstrap node: {}:{}", nodeIndex, host, bootstrapPort);
            } catch (Exception e) {
                log.warn("Node {} failed to add bootstrap node: {}", nodeIndex, bootstrapNode, e);
            }
        }
        
        running.set(true);
        
        log.info("DHT Node {} started on port {}, Type: {}", 
                 nodeIndex, port, dht.getType());
        
        // 等待DHT运行（阻塞当前线程）
        while (running.get()) {
            try {
                Thread.sleep(1000);
                
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
