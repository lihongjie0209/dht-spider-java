package cn.lihongjie.dht.metadata.dto;

import cn.lihongjie.dht.common.model.TorrentMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 种子元数据DTO
 * 用于REST API响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TorrentMetadataDTO {
    
    /**
     * InfoHash（40位十六进制字符串）
     */
    private String infoHash;
    
    /**
     * 种子名称
     */
    private String name;
    
    /**
     * 总大小（字节）
     */
    private Long totalSize;
    
    /**
     * 文件列表
     */
    private List<TorrentMetadata.FileInfo> files;
    
    /**
     * 获取时间
     */
    private Instant fetchedAt;
    
    /**
     * 人类可读的大小
     */
    public String getFormattedSize() {
        if (totalSize == null) {
            return "0 B";
        }
        
        long size = totalSize;
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
