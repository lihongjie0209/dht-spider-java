package cn.lihongjie.dht.metadata.service;

import org.junit.jupiter.api.BeforeAll;
import cn.lihongjie.dht.springcommon.bloom.BloomFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BloomFilterServiceIntegrationTest {

    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis/redis-stack:latest"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        redis.start();
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private BloomFilterService bloomFilterService;

    private final String key = "test:bloom:metadata";

    @BeforeAll
    void setup() {
        bloomFilterService.reserve(key, 0.01, 1000);
    }

    @Test
    void addAndExistsCycle() {
        String value = "def456";
        assertFalse(bloomFilterService.exists(key, value), "Value should not exist before add");
        bloomFilterService.add(key, value);
        assertTrue(bloomFilterService.exists(key, value), "Value should exist after add");
    }
}
