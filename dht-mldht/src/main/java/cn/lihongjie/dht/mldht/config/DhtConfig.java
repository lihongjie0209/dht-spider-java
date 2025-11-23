package cn.lihongjie.dht.mldht.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * DHT网络配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "dht")
public class DhtConfig {
    
    /**
     * 起始监听端口
     */
    private int startPort = 6881;
    
    /**
     * DHT节点数量
     */
    private int nodeCount = 4;
    
    /**
     * Bootstrap节点列表
     */
    private List<String> bootstrapNodes = List.of(
        "router.bittorrent.com:6881",
        "dht.transmissionbt.com:6881",
        "router.utorrent.com:6881"
    );
    
    /**
     * 是否启用NodeId均匀分布算法
     */
    private boolean distributeNodeIds = true;
    
    /**
     * 获取指定索引的端口
     */
    public int getPortForNode(int nodeIndex) {
        return startPort + nodeIndex;
    }
}
