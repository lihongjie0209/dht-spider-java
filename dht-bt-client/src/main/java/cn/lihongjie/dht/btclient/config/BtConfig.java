package cn.lihongjie.dht.btclient.config;

import bt.Bt;
import bt.dht.DHTConfig;
import bt.dht.DHTModule;
import bt.runtime.BtRuntime;
import bt.runtime.Config;
import cn.lihongjie.dht.btclient.service.BtClientPool;
import com.google.inject.Module;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Bt库配置
 */
@Slf4j
@Configuration
public class BtConfig {
    
    @Value("${bt.runtime.num-of-hashing-threads:4}")
    private int numOfHashingThreads;
    
    @Value("${bt.download.storage-path:./bt-temp}")
    private String storagePath;
    
    @Value("${bt.client.pool-size:10}")
    private int clientPoolSize;
    
    @Bean
    public Config btRuntimeConfig() {
        return new Config() {
            @Override
            public int getNumOfHashingThreads() {
                return numOfHashingThreads;
            }
            
            @Override
            public int getMaxConcurrentlyActivePeerConnectionsPerTorrent() {
                // 只获取元数据，不下载文件，限制连接数
                return 10;
            }
            
            @Override
            public int getMaxPeerConnectionsPerTorrent() {
                return 20;
            }
        };
    }
    
    @Bean
    public Module dhtModule() {
        return new DHTModule(new DHTConfig() {
            @Override
            public boolean shouldUseRouterBootstrap() {
                // 使用公共DHT引导节点
                return true;
            }
        });
    }
    
    @Bean
    public BtRuntime btRuntime(Config config, Module dhtModule) {
        log.info("Initializing Bt runtime with {} hashing threads", numOfHashingThreads);
        
        BtRuntime runtime = BtRuntime.builder(config)
                .module(dhtModule)
                .autoLoadModules()
                .build();
        
        log.info("Bt runtime initialized successfully");
        return runtime;
    }
    
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
    public BtClientPool btClientPool(BtRuntime btRuntime, Path btStoragePath) {
        log.info("Creating BT client pool with size: {}", clientPoolSize);
        return new BtClientPool(btRuntime, btStoragePath, clientPoolSize);
    }
}
