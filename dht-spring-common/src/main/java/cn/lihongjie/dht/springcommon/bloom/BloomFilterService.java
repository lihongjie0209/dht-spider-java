package cn.lihongjie.dht.springcommon.bloom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 统一的 RedisBloom BloomFilterService 实现，供多个 Spring 模块复用。
 */
@Slf4j
@Service
public class BloomFilterService {
    private static final DefaultRedisScript<Long> BF_EXISTS_SCRIPT =
            new DefaultRedisScript<>("return redis.call('BF.EXISTS', KEYS[1], ARGV[1])", Long.class);
    private static final DefaultRedisScript<Long> BF_ADD_SCRIPT =
            new DefaultRedisScript<>("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
    private static final DefaultRedisScript<String> BF_RESERVE_SCRIPT =
            new DefaultRedisScript<>("return redis.call('BF.RESERVE', KEYS[1], ARGV[1], ARGV[2])", String.class);

    private final RedisTemplate<String, String> redisTemplate;

    public BloomFilterService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 创建 Bloom Filter（若不存在）。
     */
    public void reserve(String key, double errorRate, long capacity) {
        try {
            Object exists = redisTemplate.getConnectionFactory().getConnection().execute("EXISTS", key.getBytes());
            if (exists != null && "1".equals(exists.toString())) {
                return; // 已存在
            }
            redisTemplate.execute(BF_RESERVE_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(errorRate), String.valueOf(capacity));
            log.info("Bloom Filter reserved: {} (errorRate={}, capacity={})", key, errorRate, capacity);
        } catch (Exception e) {
            log.error("Failed to reserve Bloom Filter {}", key, e);
        }
    }

    /**
     * 判断元素是否可能存在。返回 true 表示存在；返回 false 表示一定不存在。
     */
    public boolean exists(String key, String value) {
        try {
            Long result = redisTemplate.execute(BF_EXISTS_SCRIPT, Collections.singletonList(key), value);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Bloom Filter EXISTS failed for key={}, value={}", key, value, e);
            return true; // 保守退化，避免重复处理
        }
    }

    /**
     * 添加元素到 Bloom Filter。
     */
    public void add(String key, String value) {
        try {
            redisTemplate.execute(BF_ADD_SCRIPT, Collections.singletonList(key), value);
        } catch (Exception e) {
            log.error("Bloom Filter ADD failed for key={}, value={}", key, value, e);
        }
    }
}
