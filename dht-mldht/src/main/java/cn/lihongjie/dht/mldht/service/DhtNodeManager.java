package cn.lihongjie.dht.mldht.service;

import cn.lihongjie.dht.mldht.config.DhtConfig;
import cn.lihongjie.dht.mldht.core.DhtNode;
import cn.lihongjie.dht.mldht.core.NodeIdGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DHT节点管理器
 * 使用虚拟线程管理多个DHT节点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DhtNodeManager {
    
    private final DhtConfig dhtConfig;
    private final NodeIdGenerator nodeIdGenerator;
    private final InfoHashPublisher publisher;
    
    private final List<DhtNode> nodes = new ArrayList<>();
    private final List<Thread> nodeThreads = new ArrayList<>();
    private ScheduledExecutorService statsScheduler;
    
    /**
     * 启动所有DHT节点
     */
    @PostConstruct
    public void start() {
        log.info("========================================");
        log.info("Starting DHT Node Manager");
        log.info("Node Count: {}", dhtConfig.getNodeCount());
        log.info("Start Port: {}", dhtConfig.getStartPort());
        log.info("Using Virtual Threads: {}", Thread.ofVirtual().name("test").unstarted(() -> {}).isVirtual());
        log.info("========================================");
        
        // 生成NodeId列表
        List<byte[]> nodeIds;
        if (dhtConfig.isDistributeNodeIds()) {
            log.info("Generating distributed NodeIds for {} nodes", dhtConfig.getNodeCount());
            nodeIds = nodeIdGenerator.generateDistributedNodeIds(dhtConfig.getNodeCount());
        } else {
            log.info("Generating random NodeIds for {} nodes", dhtConfig.getNodeCount());
            nodeIds = new ArrayList<>();
            for (int i = 0; i < dhtConfig.getNodeCount(); i++) {
                nodeIds.add(nodeIdGenerator.generateRandomNodeId());
            }
        }
        
        // 创建并启动所有节点
        for (int i = 0; i < dhtConfig.getNodeCount(); i++) {
            final int nodeIdx = i;
            int port = dhtConfig.getPortForNode(nodeIdx);
            byte[] nodeId = nodeIds.get(nodeIdx);

            DhtNode node = new DhtNode(
                nodeIdx,
                port,
                nodeId,
                dhtConfig.getBootstrapNodes(),
                publisher
            );

            nodes.add(node);

            // 使用虚拟线程启动节点
            Thread nodeThread = Thread.ofVirtual()
                .name("dht-node-" + nodeIdx)
                .start(() -> {
                    try {
                        node.start();
                    } catch (Exception e) {
                        log.error("Failed to start DHT node {}", nodeIdx, e);
                    }
                });

            nodeThreads.add(nodeThread);

            log.info("Started DHT Node {} on port {} (Virtual Thread: {})",
                     nodeIdx, port, nodeThread.isVirtual());
        }
        
        // 启动统计信息定时输出
        startStatsReporter();
        
        log.info("All {} DHT nodes started successfully", nodes.size());
    }
    
    /**
     * 启动统计信息报告器
     */
    private void startStatsReporter() {
        statsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "dht-stats-reporter");
            thread.setDaemon(true);
            return thread;
        });
        
        statsScheduler.scheduleAtFixedRate(() -> {
            try {
                logStatistics();
            } catch (Exception e) {
                log.error("Error reporting statistics", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 输出统计信息
     */
    private void logStatistics() {
        log.info("========================================");
        log.info("DHT Spider Statistics");
        log.info("========================================");
        
        long totalMessages = 0;
        long totalDiscovered = 0;
        
        for (DhtNode node : nodes) {
            totalMessages += node.getMessageCount().get();
            totalDiscovered += node.getDiscoveredCount().get();
            log.info(node.getStats());
        }
        
        log.info("Total Messages: {}", totalMessages);
        log.info("Total Discovered: {}", totalDiscovered);
        log.info("Publisher: {}", publisher.getStats());
        log.info("========================================");
    }
    
    /**
     * 停止所有DHT节点
     */
    @PreDestroy
    public void stop() {
        log.info("Stopping DHT Node Manager...");
        
        // 停止统计报告器
        if (statsScheduler != null) {
            statsScheduler.shutdown();
            try {
                if (!statsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    statsScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                statsScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 停止所有节点
        for (DhtNode node : nodes) {
            try {
                node.stop();
            } catch (Exception e) {
                log.error("Error stopping node {}", node.getNodeIndex(), e);
            }
        }
        
        // 等待虚拟线程结束
        for (Thread thread : nodeThreads) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 输出最终统计
        logStatistics();
        
        log.info("DHT Node Manager stopped");
    }
    
    /**
     * 获取所有节点
     */
    public List<DhtNode> getNodes() {
        return new ArrayList<>(nodes);
    }
}
