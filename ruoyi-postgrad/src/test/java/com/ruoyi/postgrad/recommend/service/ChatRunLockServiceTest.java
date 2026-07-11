package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ChatRunLockServiceTest {

    @Test
    void releasesOnlyTheMatchingOwner() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("ai:v2:chat:lock:7"), eq("owner-a"), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(), anyList(), eq("owner-a"))).thenReturn(1L);
        when(redis.execute(any(), anyList(), eq("owner-b"))).thenReturn(0L);

        ChatRunLockService locks = new ChatRunLockService(redis);

        assertTrue(locks.acquire(7L, "owner-a"));
        assertFalse(locks.release(7L, "owner-b"));
        assertTrue(locks.release(7L, "owner-a"));
    }

    @Test
    void rejectsWhileActiveAndAllowsTheNextRunAfterTerminalRelease() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        AtomicReference<String> storedOwner = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("ai:v2:chat:lock:7"), any(), any(Duration.class)))
            .thenAnswer(invocation -> storedOwner.compareAndSet(null, invocation.getArgument(1)));
        when(redis.execute(any(), anyList(), any(String.class))).thenAnswer(invocation -> {
            String owner = invocation.getArgument(2);
            return storedOwner.compareAndSet(owner, null) ? 1L : 0L;
        });
        ChatRunLockService locks = new ChatRunLockService(redis);

        assertTrue(locks.acquire(7L, "run-a"));
        assertFalse(locks.acquire(7L, "run-b"));
        assertTrue(locks.release(7L, "run-a"));
        assertTrue(locks.acquire(7L, "run-b"));
    }
}
