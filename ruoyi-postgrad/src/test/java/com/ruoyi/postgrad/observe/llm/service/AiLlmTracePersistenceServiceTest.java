package com.ruoyi.postgrad.observe.llm.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ruoyi.postgrad.observe.llm.mapper.AiLlmCallTraceMapper;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshot;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class AiLlmTracePersistenceServiceTest {

    @Test
    void persistShouldInsertCompactSnapshot() {
        AiLlmCallTraceMapper mapper = mock(AiLlmCallTraceMapper.class);
        AiLlmTracePersistenceService service = new AiLlmTracePersistenceService(
            mapper, new SyncTaskExecutor());
        AiInvocationSnapshot snapshot = snapshot("SUCCESS", null);

        service.persist(snapshot);

        verify(mapper).insert(any());
    }

    @Test
    void persistShouldFailOpenWhenDatabaseInsertFails() {
        AiLlmCallTraceMapper mapper = mock(AiLlmCallTraceMapper.class);
        doThrow(new RuntimeException("database unavailable")).when(mapper).insert(any());
        AiLlmTracePersistenceService service = new AiLlmTracePersistenceService(
            mapper, new SyncTaskExecutor());

        assertDoesNotThrow(() -> service.persist(snapshot("ERROR", "sensitive failure")));
    }

    @Test
    void persistShouldFailOpenWhenExecutorRejectsTask() {
        AiLlmCallTraceMapper mapper = mock(AiLlmCallTraceMapper.class);
        AiLlmTracePersistenceService service = new AiLlmTracePersistenceService(
            mapper, task -> { throw new TaskRejectedException("queue full"); });
        assertDoesNotThrow(() -> service.persist(snapshot("SUCCESS", null)));
    }

    private AiInvocationSnapshot snapshot(String outcome, String errorSummary) {
        AiInvocationSnapshot snapshot = new AiInvocationSnapshot();
        snapshot.setRunId("run-1");
        snapshot.setOperationKey("ai-chat");
        snapshot.setProvider("openai");
        snapshot.setModel("deepseek-chat");
        snapshot.setOutcome(outcome);
        snapshot.setDurationMs(125L);
        snapshot.setInputTokens(10);
        snapshot.setOutputTokens(20);
        snapshot.setErrorSummary(errorSummary);
        snapshot.setOccurredAt(LocalDateTime.now());
        return snapshot;
    }
}
