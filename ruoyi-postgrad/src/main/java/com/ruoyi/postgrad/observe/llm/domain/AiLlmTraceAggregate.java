package com.ruoyi.postgrad.observe.llm.domain;

public class AiLlmTraceAggregate {
    private long totalCalls;
    private long successCalls;
    private double averageDurationMs;
    private long inputTokens;
    private long outputTokens;

    public AiLlmTraceAggregate() {
    }

    public AiLlmTraceAggregate(long totalCalls, long successCalls, double averageDurationMs,
                               long inputTokens, long outputTokens) {
        this.totalCalls = totalCalls;
        this.successCalls = successCalls;
        this.averageDurationMs = averageDurationMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public double successRate() {
        return totalCalls == 0 ? 0.0D : successCalls * 100.0D / totalCalls;
    }

    public long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(long totalCalls) { this.totalCalls = totalCalls; }
    public long getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(long successCalls) { this.successCalls = successCalls; }
    public double getAverageDurationMs() { return averageDurationMs; }
    public void setAverageDurationMs(double averageDurationMs) { this.averageDurationMs = averageDurationMs; }
    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
}
