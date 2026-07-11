package com.ruoyi.postgrad.observe.runtime;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class AiInvocationSnapshotRecorder {

    private static final String SUCCESS = "SUCCESS";
    private static final String ERROR = "ERROR";

    private final AtomicReference<AiInvocationSnapshot> latest = new AtomicReference<>(new AiInvocationSnapshot());

    public void recordSuccess(String operationKey, String provider, String model, long durationMs, int inputTokens, int outputTokens) {
        recordSuccess(operationKey, null, provider, model, durationMs, inputTokens, outputTokens);
    }
    public void recordSuccess(String operationKey, String runId, String provider, String model, long durationMs, int inputTokens, int outputTokens) {
        AiInvocationSnapshot snapshot = new AiInvocationSnapshot();
        snapshot.setOperationKey(operationKey);
        snapshot.setRunId(runId);
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setOutcome(SUCCESS);
        snapshot.setDurationMs(durationMs);
        snapshot.setInputTokens(inputTokens);
        snapshot.setOutputTokens(outputTokens);
        snapshot.setOccurredAt(LocalDateTime.now());
        latest.set(snapshot);
    }

    public void recordError(String operationKey, String provider, String model, String errorSummary) {
        recordError(operationKey, null, provider, model, errorSummary);
    }
    public void recordError(String operationKey, String runId, String provider, String model, String errorSummary) {
        AiInvocationSnapshot snapshot = new AiInvocationSnapshot();
        snapshot.setOperationKey(operationKey);
        snapshot.setRunId(runId);
        snapshot.setProvider(provider);
        snapshot.setModel(model);
        snapshot.setOutcome(ERROR);
        snapshot.setErrorSummary(errorSummary);
        snapshot.setOccurredAt(LocalDateTime.now());
        latest.set(snapshot);
    }

    public AiInvocationSnapshot getLatest() {
        return latest.get();
    }
}
