package com.ruoyi.postgrad.observe.llm.service;

import com.ruoyi.postgrad.observe.llm.domain.AiLlmTraceAggregate;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmCallTrace;
import com.ruoyi.postgrad.observe.llm.mapper.AiLlmCallTraceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiLlmTraceQueryService {
    private final AiLlmCallTraceMapper mapper;

    public AiLlmTraceQueryService(AiLlmCallTraceMapper mapper) {
        this.mapper = mapper;
    }

    public AiLlmObserveSnapshot load(AiObserveWindow window) {
        return load(window, LocalDateTime.now());
    }

    AiLlmObserveSnapshot load(AiObserveWindow window, LocalDateTime now) {
        LocalDateTime start = now.minusHours(window.getHours());
        AiLlmTraceAggregate aggregate = mapper.selectAggregate(start);
        if (aggregate == null) aggregate = new AiLlmTraceAggregate();
        return new AiLlmObserveSnapshot(
            aggregate,
            mapper.selectModelGroups(start),
            mapper.selectOperationGroups(start),
            mapper.selectLatest(start),
            start
        );
    }

    public List<AiLlmCallTrace> listByRunId(String runId, int limit) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is required");
        return mapper.selectByRunId(runId, Math.max(1, Math.min(limit, 200)));
    }
}
