package cn.lihongjie.dht.metadata.repository;

import cn.lihongjie.dht.metadata.entity.TorrentFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 种子文件仓库
 */
@Repository
public interface TorrentFileRepository extends JpaRepository<TorrentFileEntity, Long> {
    
    /**
     * 根据元数据ID查询文件列表
     */
    List<TorrentFileEntity> findByMetadataId(Long metadataId);
    
    /**
     * 根据文件路径模糊搜索
     */
    @Query("SELECT f FROM TorrentFileEntity f WHERE LOWER(f.filePath) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<TorrentFileEntity> searchByFilePath(@Param("keyword") String keyword);
}
