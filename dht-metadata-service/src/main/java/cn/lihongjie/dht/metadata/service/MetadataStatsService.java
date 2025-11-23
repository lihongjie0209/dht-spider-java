package cn.lihongjie.dht.metadata.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 元数据统计服务
 */
@Slf4j
@Getter
@Service
@EnableScheduling
public class MetadataStatsService {
    
    private final AtomicLong totalConsumed = new AtomicLong(0);
    private final AtomicLong totalPersisted = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    
    /**
     * 增加消费计数
     */
    public void incrementConsumed() {
        totalConsumed.incrementAndGet();
    }
    
    /**
     * 增加持久化计数
     */
    public void incrementPersisted() {
        totalPersisted.incrementAndGet();
    }
    
    /**
     * 增加失败计数
     */
    public void incrementFailed() {
        totalFailed.incrementAndGet();
    }
    
    /**
     * 定期输出统计信息
     */
    @Scheduled(fixedRate = 60000) // 每分钟
    public void logStats() {
        log.info("Metadata Stats - Consumed: {}, Persisted: {}, Failed: {}", 
                totalConsumed.get(), totalPersisted.get(), totalFailed.get());
    }
    
    /**
     * 获取统计信息
     */
    public String getStatsString() {
        return String.format("Consumed: %d, Persisted: %d, Failed: %d", 
                totalConsumed.get(), totalPersisted.get(), totalFailed.get());
    }
}
