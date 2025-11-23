package cn.lihongjie.dht.metadata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson配置
 * 支持Java 8时间类型序列化
 */
@Configuration
public class JacksonConfig {
    
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册Java 8时间模块，支持 Instant, LocalDateTime 等类型
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
