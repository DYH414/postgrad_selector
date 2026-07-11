package com.ruoyi.postgrad.recommend.service;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class ChatRunLockService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(3);
    private static final String KEY_PREFIX = "ai:v2:chat:lock:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final StringRedisTemplate redisTemplate;

    public ChatRunLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(Long userId, String owner) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key(userId), owner, LOCK_TTL));
    }

    public boolean release(Long userId, String owner) {
        Long released = redisTemplate.execute(RELEASE_SCRIPT, List.of(key(userId)), owner);
        return Long.valueOf(1L).equals(released);
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
