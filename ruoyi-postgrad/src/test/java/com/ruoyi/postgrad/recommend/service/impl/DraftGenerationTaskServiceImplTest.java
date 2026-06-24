package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskVO;
import com.ruoyi.postgrad.recommend.service.IDraftService;

class DraftGenerationTaskServiceImplTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private IDraftService draftService;
    private DraftGenerationTaskServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        draftService = mock(IDraftService.class);

        // 同步执行器：CompletableFuture.runAsync 在同一线程执行，测试可预测
        ThreadPoolTaskExecutor syncExecutor = new ThreadPoolTaskExecutor();
        syncExecutor.setCorePoolSize(1);
        syncExecutor.setMaxPoolSize(1);
        syncExecutor.initialize();

        service = new DraftGenerationTaskServiceImpl();
        service.setRedisTemplateForTest(redis);
        service.setDraftServiceForTest(draftService);
        service.setThreadPoolTaskExecutorForTest(syncExecutor);
    }

    @Test
    void startShouldReturnTaskIdAndPersistRunningState() {
        DraftGenerationTaskVO vo = service.start(1L);

        assertNotNull(vo.getTaskId());
        assertNotNull(vo.getStreamToken());
        assertEquals("running", vo.getStatus());
        verify(ops, atLeastOnce()).set(
            startsWith("ai:v2:draft:task:"),
            contains("\"status\":\"running\""),
            any(Duration.class));
        verify(draftService).generateDraft(any(), any());
    }

    @Test
    void validateStreamTokenShouldRejectWrongToken() {
        DraftGenerationTaskVO vo = service.start(1L);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops, atLeastOnce()).set(
            startsWith("ai:v2:draft:task:"),
            jsonCaptor.capture(),
            any(Duration.class));
        String persisted = jsonCaptor.getAllValues().get(0);
        when(ops.get("ai:v2:draft:task:" + vo.getTaskId())).thenReturn(persisted);

        assertEquals(false, service.validateStreamToken(vo.getTaskId(), "wrong-token"));
    }
}
