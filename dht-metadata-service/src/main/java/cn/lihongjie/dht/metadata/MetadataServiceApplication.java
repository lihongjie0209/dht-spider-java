package cn.lihongjie.dht.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 元数据服务主类
 * Spring Boot Web应用，提供REST API用于查询种子元数据
 */
@SpringBootApplication(scanBasePackages = "cn.lihongjie.dht")
public class MetadataServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MetadataServiceApplication.class, args);
    }
}
