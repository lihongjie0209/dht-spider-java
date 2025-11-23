package cn.lihongjie.dht.metadata.repository;

import cn.lihongjie.dht.metadata.entity.TorrentMetadataEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 种子元数据仓库
 */
@Repository
public interface TorrentMetadataRepository extends JpaRepository<TorrentMetadataEntity, Long> {
    
    /**
     * 根据InfoHash查找
     */
    Optional<TorrentMetadataEntity> findByInfoHash(String infoHash);
    
    /**
     * 检查InfoHash是否存在
     */
    boolean existsByInfoHash(String infoHash);
    
    /**
     * 根据名称模糊搜索
     */
    @Query("SELECT t FROM TorrentMetadataEntity t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<TorrentMetadataEntity> searchByName(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 获取最新的种子
     */
    Page<TorrentMetadataEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
