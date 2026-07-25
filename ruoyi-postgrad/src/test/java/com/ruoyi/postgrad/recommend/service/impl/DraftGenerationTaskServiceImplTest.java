package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        doReturn(ops).when(redis).opsForValue();
        // 默认：锁获取成功（null → Boolean.FALSE.equals(null) = false → 通过锁检查）
        doReturn(null).when(ops).setIfAbsent(anyString(), anyString(), any(Duration.class));

        draftService = mock(IDraftService.class);

        // 同步执行器：CompletableFuture.runAsync 在同一线程执行，测试可预测
        ThreadPoolTaskExecutor syncExecutor = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };

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
    void startShouldRejectDuplicateWhenLockHeld() {
        doReturn(false).when(ops).setIfAbsent(anyString(), anyString(), any(Duration.class));

        DraftGenerationTaskVO vo = service.start(1L);

        assertEquals("locked", vo.getStatus());
        assertEquals("已有草稿正在生成，请稍候", vo.getMessage());
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
        doReturn(persisted).when(ops).get("ai:v2:draft:task:" + vo.getTaskId());

        assertEquals(false, service.validateStreamToken(vo.getTaskId(), "wrong-token"));
    }
}
