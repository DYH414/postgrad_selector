package com.ruoyi.postgrad.observe.llm.domain;

import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshot;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

public class AiLlmCallTrace {

    private static final Pattern API_KEY = Pattern.compile("(?i)(sk-[a-z0-9_-]{8,}|bearer\\s+[a-z0-9._-]{8,})");
    private Long id;
    private String traceId;
    private String runId;
    private String operationKey;
    private String provider;
    private String model;
    private String outcome;
    private Long durationMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private String errorSummary;
    private LocalDateTime occurredAt;

    public static AiLlmCallTrace from(AiInvocationSnapshot snapshot) {
        AiLlmCallTrace trace = new AiLlmCallTrace();
        trace.traceId = UUID.randomUUID().toString();
        trace.runId = snapshot.getRunId();
        trace.operationKey = defaultText(snapshot.getOperationKey());
        trace.provider = defaultText(snapshot.getProvider());
        trace.model = defaultText(snapshot.getModel());
        trace.outcome = defaultText(snapshot.getOutcome());
        trace.durationMs = snapshot.getDurationMs() == null ? 0L : snapshot.getDurationMs();
        trace.inputTokens = snapshot.getInputTokens() == null ? 0 : snapshot.getInputTokens();
        trace.outputTokens = snapshot.getOutputTokens() == null ? 0 : snapshot.getOutputTokens();
        trace.errorSummary = sanitize(snapshot.getErrorSummary());
        trace.occurredAt = snapshot.getOccurredAt() == null ? LocalDateTime.now() : snapshot.getOccurredAt();
        return trace;
    }

    private static String defaultText(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        String redacted = API_KEY.matcher(value).replaceAll("[REDACTED]");
        return redacted.length() <= 512 ? redacted : redacted.substring(0, 512);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
