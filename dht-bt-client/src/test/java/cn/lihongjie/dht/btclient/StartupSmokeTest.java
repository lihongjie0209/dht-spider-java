package cn.lihongjie.dht.btclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = BtClientApplication.class)
@Testcontainers
class StartupSmokeTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis/redis-stack:latest"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("dedup.enabled", () -> "true");
    }

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assert applicationContext.isActive();
    }
}
