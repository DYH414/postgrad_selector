package com.ruoyi.postgrad.observe.llm.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.observe.llm.mapper.AiLlmCallTraceMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AiLlmTraceRetentionServiceTest {
    @Test
    void cleanupShouldDeleteOnlyRecordsBeforeRetentionBoundary() {
        AiLlmCallTraceMapper mapper = mock(AiLlmCallTraceMapper.class);
        AiLlmTraceRetentionService service = new AiLlmTraceRetentionService(mapper, 30);
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 3, 20);
        service.cleanup(now);
        verify(mapper).deleteBefore(now.minusDays(30));
    }

    @Test
    void cleanupFailureShouldBeFailOpen() {
        AiLlmCallTraceMapper mapper = mock(AiLlmCallTraceMapper.class);
        when(mapper.deleteBefore(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("db down"));
        AiLlmTraceRetentionService service = new AiLlmTraceRetentionService(mapper, 30);
        assertDoesNotThrow(() -> service.cleanup(LocalDateTime.now()));
    }
}
