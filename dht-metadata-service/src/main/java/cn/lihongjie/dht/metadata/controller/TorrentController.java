package cn.lihongjie.dht.metadata.controller;

import cn.lihongjie.dht.metadata.dto.TorrentMetadataDTO;
import cn.lihongjie.dht.metadata.service.MetadataQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 元数据查询控制器
 */
@RestController
@RequestMapping("/api/v1/torrents")
@RequiredArgsConstructor
public class TorrentController {
    
    private final MetadataQueryService queryService;
    private final cn.lihongjie.dht.metadata.service.MetadataStatsService statsService;
    
    /**
     * 根据InfoHash查询
     */
    @GetMapping("/{infoHash}")
    public ResponseEntity<TorrentMetadataDTO> getByInfoHash(@PathVariable String infoHash) {
        return queryService.findByInfoHash(infoHash)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 搜索种子
     */
    @GetMapping("/search")
    public Page<TorrentMetadataDTO> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.search(keyword, PageRequest.of(page, size));
    }
    
    /**
     * 获取最新种子
     */
    @GetMapping("/latest")
    public Page<TorrentMetadataDTO> getLatest(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.getLatest(PageRequest.of(page, size));
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
    
    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<String> stats() {
        return ResponseEntity.ok(statsService.getStatsString());
    }
}
