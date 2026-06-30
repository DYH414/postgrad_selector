package com.ruoyi.postgrad.observe.domain;

import java.util.ArrayList;
import java.util.List;

public class AiObserveOverviewVO {

    private long totalCalls;
    private double successRate;
    private double averageDurationMs;
    private double inputTokens;
    private double outputTokens;
    private List<AiObserveGroupVO> modelGroups = new ArrayList<>();
    private List<AiObserveGroupVO> operationGroups = new ArrayList<>();
    private AiObserveLatestVO latest = new AiObserveLatestVO();
    private RagQuickStatusVO rag = new RagQuickStatusVO();

    public long getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(long totalCalls) {
        this.totalCalls = totalCalls;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public double getAverageDurationMs() {
        return averageDurationMs;
    }

    public void setAverageDurationMs(double averageDurationMs) {
        this.averageDurationMs = averageDurationMs;
    }

    public double getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(double inputTokens) {
        this.inputTokens = inputTokens;
    }

    public double getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(double outputTokens) {
        this.outputTokens = outputTokens;
    }

    public List<AiObserveGroupVO> getModelGroups() {
        return modelGroups;
    }

    public void setModelGroups(List<AiObserveGroupVO> modelGroups) {
        this.modelGroups = modelGroups;
    }

    public List<AiObserveGroupVO> getOperationGroups() {
        return operationGroups;
    }

    public void setOperationGroups(List<AiObserveGroupVO> operationGroups) {
        this.operationGroups = operationGroups;
    }

    public AiObserveLatestVO getLatest() {
        return latest;
    }

    public void setLatest(AiObserveLatestVO latest) {
        this.latest = latest;
    }

    public RagQuickStatusVO getRag() {
        return rag;
    }

    public void setRag(RagQuickStatusVO rag) {
        this.rag = rag;
    }
}
