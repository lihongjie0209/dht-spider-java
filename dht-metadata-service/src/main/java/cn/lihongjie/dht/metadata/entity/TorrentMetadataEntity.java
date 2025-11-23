package cn.lihongjie.dht.metadata.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 种子元数据实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "torrent_metadata", indexes = {
    @Index(name = "idx_info_hash", columnList = "infoHash", unique = true),
    @Index(name = "idx_name", columnList = "name"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class TorrentMetadataEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 40)
    private String infoHash;
    
    @Column(nullable = false, length = 500)
    private String name;
    
    @Column(nullable = false)
    private Long totalSize;
    
    @Column(columnDefinition = "TEXT")
    private String filesJson;
    
    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] rawMetadata;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
}
