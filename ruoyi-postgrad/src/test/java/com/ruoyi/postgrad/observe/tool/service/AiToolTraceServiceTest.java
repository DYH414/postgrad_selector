package com.ruoyi.postgrad.observe.tool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import com.ruoyi.postgrad.observe.tool.mapper.AiToolCallTraceMapper;
import org.junit.jupiter.api.Test;

class AiToolTraceServiceTest {

    @Test
    void completesTraceWithSanitizedTerminalFields() {
        AiToolCallTraceMapper mapper = mock(AiToolCallTraceMapper.class);
        AiToolTraceService service = new AiToolTraceService(mapper);

        service.complete("call-1", "SUCCEEDED", "OK", false,
            "3 candidates", 120, 42L, null);

        verify(mapper).complete("call-1", "SUCCEEDED", "OK", false,
            "3 candidates", 120, 42L, null);
    }

    @Test
    void displaysExpiredWithoutUpdatingDatabase() {
        AiToolCallTrace row = new AiToolCallTrace();
        row.setStatus("WAITING_CONFIRMATION");
        row.setConfirmationExpiresAt(LocalDateTime.now().minusMinutes(1));
        AiToolCallTraceMapper mapper = mock(AiToolCallTraceMapper.class);
        when(mapper.selectRecent(50)).thenReturn(List.of(row));

        List<AiToolCallTrace> result = new AiToolTraceService(mapper).listRecent(50);

        assertEquals("EXPIRED", result.get(0).getStatus());
        verify(mapper, never()).updateStatus(anyString(), anyString());
    }
}
