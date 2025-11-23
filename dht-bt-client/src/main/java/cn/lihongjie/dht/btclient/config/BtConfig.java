package cn.lihongjie.dht.btclient.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import bt.runtime.Config;
import bt.runtime.BtRuntime;

/**
 * Bt库配置
 * 简化配置，每次下载创建独立的BtRuntime
 */
@Slf4j
@Configuration
public class BtConfig {

    @Value("${bt.download.storage-path:./bt-temp}")
    private String storagePath;

    @Value("${bt.pool.size:100}")
    private int poolSize;

    @Value("${bt.pool.acceptor-port:0}")
    private int acceptorPort;

    @Bean
    public Path btStoragePath() {
        try {
            Path path = Paths.get(storagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created BT storage directory: {}", path.toAbsolutePath());
            }
            return path;
        } catch (Exception e) {
            log.error("Failed to create BT storage directory", e);
            throw new RuntimeException("Failed to create BT storage directory", e);
        }
    }

    @Bean
    public Config btRuntimeConfig() {
        Config config = new Config();
        config.setAcceptorPort(acceptorPort); // shared acceptor port (0=random)
        config.setNumOfHashingThreads(1);
        return config;
    }

    @Bean
    public BtRuntime btRuntime(Config btRuntimeConfig) {
        BtRuntime runtime = BtRuntime.builder(btRuntimeConfig)
                .autoLoadModules()
                .build();
        log.info("Initialized shared BtRuntime for client pool. poolSize={}, acceptorPort={}", poolSize, acceptorPort);
        return runtime;
    }
}
