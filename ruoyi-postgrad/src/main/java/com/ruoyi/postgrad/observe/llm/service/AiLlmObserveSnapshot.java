package com.ruoyi.postgrad.observe.llm.service;

import com.ruoyi.postgrad.observe.domain.AiObserveGroupVO;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmCallTrace;
import com.ruoyi.postgrad.observe.llm.domain.AiLlmTraceAggregate;
import java.time.LocalDateTime;
import java.util.List;

public class AiLlmObserveSnapshot {
    private final AiLlmTraceAggregate aggregate;
    private final List<AiObserveGroupVO> modelGroups;
    private final List<AiObserveGroupVO> operationGroups;
    private final AiLlmCallTrace latest;
    private final LocalDateTime windowStart;

    public AiLlmObserveSnapshot(AiLlmTraceAggregate aggregate,
                                List<AiObserveGroupVO> modelGroups,
                                List<AiObserveGroupVO> operationGroups,
                                AiLlmCallTrace latest,
                                LocalDateTime windowStart) {
        this.aggregate = aggregate;
        this.modelGroups = modelGroups;
        this.operationGroups = operationGroups;
        this.latest = latest;
        this.windowStart = windowStart;
    }

    public AiLlmTraceAggregate getAggregate() { return aggregate; }
    public List<AiObserveGroupVO> getModelGroups() { return modelGroups; }
    public List<AiObserveGroupVO> getOperationGroups() { return operationGroups; }
    public AiLlmCallTrace getLatest() { return latest; }
    public LocalDateTime getWindowStart() { return windowStart; }
}
