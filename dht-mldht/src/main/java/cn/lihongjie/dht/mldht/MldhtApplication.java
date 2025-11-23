package cn.lihongjie.dht.mldht;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * MLDHT爬虫服务主类
 * Spring Boot应用，持续运行监听DHT网络并发现InfoHash
 * 不启用Web服务器
 */
@Slf4j
@SpringBootApplication
public class MldhtApplication {
    
    public static void main(String[] args) throws InterruptedException {
        log.info("========================================");
        log.info("DHT MLDHT Spider Service Starting...");
        log.info("========================================");
        
        ConfigurableApplicationContext context = SpringApplication.run(MldhtApplication.class, args);
        
        log.info("MLDHT Service started successfully. Press Ctrl+C to stop.");
        
        // 保持应用运行直到收到中断信号
        Thread.currentThread().join();
    }
}
