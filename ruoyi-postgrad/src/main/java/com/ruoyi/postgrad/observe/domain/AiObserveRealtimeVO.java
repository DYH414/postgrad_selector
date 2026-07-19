package com.ruoyi.postgrad.observe.domain;

public class AiObserveRealtimeVO {
    private long totalCalls;
    private double successRate;
    private double averageDurationMs;
    private double inputTokens;
    private double outputTokens;

    public long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(long totalCalls) { this.totalCalls = totalCalls; }
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    public double getAverageDurationMs() { return averageDurationMs; }
    public void setAverageDurationMs(double averageDurationMs) { this.averageDurationMs = averageDurationMs; }
    public double getInputTokens() { return inputTokens; }
    public void setInputTokens(double inputTokens) { this.inputTokens = inputTokens; }
    public double getOutputTokens() { return outputTokens; }
    public void setOutputTokens(double outputTokens) { this.outputTokens = outputTokens; }
}
