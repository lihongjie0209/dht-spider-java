package cn.lihongjie.dht.btclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * BT客户端服务主类
 * Spring Boot应用，持续运行消费InfoHash并下载元数据
 * 不启用Web服务器
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "cn.lihongjie.dht")
public class BtClientApplication {
    
    public static void main(String[] args) throws InterruptedException {
        log.info("========================================");
        log.info("DHT BT Client Service Starting...");
        log.info("========================================");
        
        ConfigurableApplicationContext context = SpringApplication.run(BtClientApplication.class, args);
        
        log.info("BT Client Service started successfully. Press Ctrl+C to stop.");
        
        // 保持应用运行
        Thread.currentThread().join();
    }
}
