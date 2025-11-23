package cn.lihongjie.dht.metadata.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 种子文件实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "torrent_file", indexes = {
    @Index(name = "idx_metadata_id", columnList = "metadataId"),
    @Index(name = "idx_file_path", columnList = "filePath")
})
public class TorrentFileEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long metadataId;
    
    @Column(nullable = false, length = 1000)
    private String filePath;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metadataId", insertable = false, updatable = false)
    private TorrentMetadataEntity metadata;
}
