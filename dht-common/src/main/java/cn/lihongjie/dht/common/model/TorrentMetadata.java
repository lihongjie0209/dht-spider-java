package cn.lihongjie.dht.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 种子元数据：从BT客户端传递到Metadata服务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorrentMetadata implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * InfoHash (40位十六进制字符串)
     */
    private String infoHash;
    
    /**
     * 种子名称
     */
    private String name;
    
    /**
     * 文件列表
     */
    private List<FileInfo> files;
    
    /**
     * 总大小（字节）
     */
    private Long totalSize;
    
    /**
     * 下载完成时间
     */
    private Instant fetchedAt;

    /**
     * 下载状态: SUCCESS | FAILED
     */
    private String status;

    /**
     * 失败原因消息（FAILED 时可用）
     */
    private String failureMessage;

    /**
     * 触发下载/失败时的对端地址（便于后续重试分析）
     */
    private String peerIp;
    private Integer peerPort;

    /**
     * 上次重试时间与重试次数（便于后续调度器使用）
     */
    private Instant lastRetryAt;
    private Integer retryCount;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 文件路径
         */
        private String path;
        
        /**
         * 文件大小（字节）
         */
        private Long length;
    }
}
