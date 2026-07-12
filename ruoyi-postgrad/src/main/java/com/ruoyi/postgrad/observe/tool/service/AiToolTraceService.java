package com.ruoyi.postgrad.observe.tool.service;

import java.time.LocalDateTime;
import java.util.List;

import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import com.ruoyi.postgrad.observe.tool.mapper.AiToolCallTraceMapper;
import org.springframework.stereotype.Service;

@Service
public class AiToolTraceService {

    private final AiToolCallTraceMapper mapper;

    public AiToolTraceService(AiToolCallTraceMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(AiToolCallTrace trace) {
        mapper.insert(trace);
    }

    public void complete(String callId, String status, String code, boolean retryable,
        String resultSummary, int resultTokenEstimate, long durationMs,
        LocalDateTime confirmationExpiresAt) {
        mapper.complete(callId, status, code, retryable, resultSummary, resultTokenEstimate,
            durationMs, confirmationExpiresAt);
    }

    public void cancel(String callId) {
        mapper.updateStatus(callId, "CANCELLED");
    }

    public List<AiToolCallTrace> listRecent(int limit) {
        return displayStatuses(mapper.selectRecent(clampLimit(limit)));
    }

    public List<AiToolCallTrace> listByRunId(String runId, int limit) {
        return displayStatuses(mapper.selectByRunId(runId, clampLimit(limit)));
    }

    private List<AiToolCallTrace> displayStatuses(List<AiToolCallTrace> traces) {
        LocalDateTime now = LocalDateTime.now();
        traces.forEach(trace -> {
            if ("WAITING_CONFIRMATION".equals(trace.getStatus())
                && trace.getConfirmationExpiresAt() != null
                && trace.getConfirmationExpiresAt().isBefore(now)) {
                trace.setStatus("EXPIRED");
            }
        });
        return traces;
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(100, limit));
    }
}
