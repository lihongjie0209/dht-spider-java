package cn.lihongjie.dht.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * InfoHash消息：从MLDHT传递到BT客户端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfoHashMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * InfoHash (40位十六进制字符串)
     */
    private String infoHash;
    
    /**
     * 发现时间
     */
    private Instant discoveredAt;
    
    /**
     * 来源节点IP
     */
    private String sourceIp;
    
    /**
     * 来源节点端口
     */
    private Integer sourcePort;
}
