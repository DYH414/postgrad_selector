package com.ruoyi.postgrad.observe.llm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.observe.domain.AiObserveGroupVO;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmCallTrace;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmTraceAggregate;
import com.ruoyi.postgrad.observe.llm.mapper.AiLlmCallTraceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiLlmTraceQueryServiceTest {

    @Test
    void loadShouldReturnWindowedAggregateGroupsAndLatest() {
        AiLlmCallTraceMapper mapper = mock(AiLlmCallTraceMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        LocalDateTime start = now.minusHours(24);
        AiLlmTraceAggregate aggregate = new AiLlmTraceAggregate(2, 1, 200, 30, 40);
        AiObserveGroupVO model = new AiObserveGroupVO();
        model.setKey("deepseek-chat");
        model.setCalls(2);
        AiLlmCallTrace latest = new AiLlmCallTrace();
        latest.setTraceId("trace-2");
        when(mapper.selectAggregate(start)).thenReturn(aggregate);
        when(mapper.selectModelGroups(start)).thenReturn(List.of(model));
        when(mapper.selectOperationGroups(start)).thenReturn(List.of());
        when(mapper.selectLatest(start)).thenReturn(latest);

        AiLlmObserveSnapshot result = new AiLlmTraceQueryService(mapper)
            .load(AiObserveWindow.HOURS_24, now);

        assertEquals(2, result.getAggregate().getTotalCalls());
        assertEquals(50.0D, result.getAggregate().successRate());
        assertEquals("trace-2", result.getLatest().getTraceId());
        assertEquals(1, result.getModelGroups().size());
        assertEquals(start, result.getWindowStart());
    }
}
