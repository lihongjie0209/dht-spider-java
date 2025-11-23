package cn.lihongjie.dht.mldht.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * NodeId生成器
 * 使用算法使节点均匀分布在DHT网络的160位地址空间中
 */
@Slf4j
@Component
public class NodeIdGenerator {
    
    private static final int NODE_ID_LENGTH = 20; // 160 bits / 8 = 20 bytes
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * 生成均匀分布的NodeId列表
     * 将160位地址空间均匀划分，每个节点占据一个区间的起始位置
     * 
     * @param nodeCount 节点数量
     * @return NodeId列表
     */
    public List<byte[]> generateDistributedNodeIds(int nodeCount) {
        List<byte[]> nodeIds = new ArrayList<>(nodeCount);
        
        if (nodeCount <= 0) {
            throw new IllegalArgumentException("Node count must be positive");
        }
        
        // 计算每个节点在160位空间中的间隔
        // 使用BigInteger的概念：将2^160空间划分为nodeCount个区间
        for (int i = 0; i < nodeCount; i++) {
            byte[] nodeId = generateDistributedNodeId(i, nodeCount);
            nodeIds.add(nodeId);
            log.debug("Generated NodeId for node {}: {}", i, bytesToHex(nodeId));
        }
        
        return nodeIds;
    }
    
    /**
     * 为指定索引的节点生成NodeId
     * 使用均匀分布算法
     */
    private byte[] generateDistributedNodeId(int nodeIndex, int totalNodes) {
        byte[] nodeId = new byte[NODE_ID_LENGTH];
        
        // 计算该节点在地址空间中的位置
        // 使用高位字节来分布，低位字节随机化
        // 这样可以确保节点在DHT网络中均匀分布
        
        // 使用前4个字节（32位）来分布节点
        long segment = (long) nodeIndex * (1L << 32) / totalNodes;
        
        // 将segment写入nodeId的前4个字节
        nodeId[0] = (byte) ((segment >> 24) & 0xFF);
        nodeId[1] = (byte) ((segment >> 16) & 0xFF);
        nodeId[2] = (byte) ((segment >> 8) & 0xFF);
        nodeId[3] = (byte) (segment & 0xFF);
        
        // 剩余16个字节使用随机值，增加节点的唯一性
        byte[] randomBytes = new byte[NODE_ID_LENGTH - 4];
        RANDOM.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, nodeId, 4, randomBytes.length);
        
        return nodeId;
    }
    
    /**
     * 生成完全随机的NodeId
     */
    public byte[] generateRandomNodeId() {
        byte[] nodeId = new byte[NODE_ID_LENGTH];
        RANDOM.nextBytes(nodeId);
        return nodeId;
    }
    
    /**
     * 基于种子生成NodeId（用于测试或特定场景）
     */
    public byte[] generateNodeIdFromSeed(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(seed.getBytes());
            // SHA-1 产生160位（20字节），正好是NodeId的长度
            return hash;
        } catch (Exception e) {
            log.error("Failed to generate NodeId from seed", e);
            return generateRandomNodeId();
        }
    }
    
    /**
     * 字节数组转十六进制字符串（用于日志）
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
