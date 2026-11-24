package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.mapper.AiChatMapper;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.recommend.service.DraftGenerationCallback;

class DraftServiceImplProfileTest {
    private final DraftServiceImpl service = new DraftServiceImpl();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AiChatMapper chatMapper = mock(AiChatMapper.class);
    private final UserProfileMapper profiles = mock(UserProfileMapper.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        ReflectionTestUtils.setField(service, "userProfileMapper", profiles);
        ReflectionTestUtils.setField(service, "aiChatMapper", chatMapper);
    }

    @Test
    void generationWithoutScoreLeavesDraftAndChatHistoryAlone() {
        DraftGenerationCallback callback = mock(DraftGenerationCallback.class);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service.generateDraft(1L, callback);

        verify(callback).onError(isA(IllegalArgumentException.class));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redis, never()).delete(anyString());
        verifyNoInteractions(chatMapper);
    }

    @Test
    void abandonedPlaceholderDoesNotShowAsGeneratingAfterScoreIsAdded() {
        UserProfile profile = new UserProfile();
        profile.setEstimatedScore(250);
        when(profiles.selectUserProfileByUserId(1L)).thenReturn(profile);
        when(redis.hasKey("ai:v2:draft:lock:1")).thenReturn(false);
        when(values.get("ai:v2:draft:1")).thenReturn("""
            {"generatedAt":"2026-11-23T02:55:58","tiers":[
              {"level":"reach","insufficient":true,"insufficientReason":"AI 正在冲刺档挑选合适的学校...","candidates":[]}
            ]}
            """);

        assertNull(service.getDraft(1L).getGeneratedAt());
    }
}
