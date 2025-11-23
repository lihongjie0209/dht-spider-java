package cn.lihongjie.dht.btclient.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MetadataStatusService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "metadata:status:";
    private static final Duration TTL = Duration.ofDays(7); // keep a week

    public void setStatus(String infoHash, String status) {
        if (infoHash == null || status == null) return;
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + infoHash, status, TTL);
    }

    public String getStatus(String infoHash) {
        if (infoHash == null) return null;
        return stringRedisTemplate.opsForValue().get(KEY_PREFIX + infoHash);
    }
}
