package com.ruoyi.postgrad.rag.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagEvaluationResult {

    private String setName;
    private int totalCases;
    private int passedCases;
    private int failedCases;
    private double passRate;
    private List<RagEvaluationCaseResult> cases = new ArrayList<>();

    private int positiveCases;
    private int noAnswerCases;
    private double noAnswerAccuracy;
    private double falsePositiveRate;

    // ── 检索排序质量指标 ──
    private double mrr;
    private double hitAt1;
    private double hitAt3;
    private double hitAt5;
    private double recallAt5;
    private double ndcg5;
    private int evalK = 5;

    // ── 系统级指标 ──
    private double filterAccuracy;
    private double routeAccuracy;
    private double fallbackAccuracy;

    // ── 分层统计 ──
    private Map<String, MetricBreakdown> byCategory = new LinkedHashMap<>();
    private Map<String, MetricBreakdown> byDifficulty = new LinkedHashMap<>();

    // ── 分段指标（structured / general / negative） ──
    private SegmentMetrics structuredMetrics;
    private SegmentMetrics generalMetrics;
    private SegmentMetrics negativeMetrics;

    // ── getters / setters ──

    public String getSetName() { return setName; }
    public void setSetName(String setName) { this.setName = setName; }
    public int getTotalCases() { return totalCases; }
    public void setTotalCases(int totalCases) { this.totalCases = totalCases; }
    public int getPassedCases() { return passedCases; }
    public void setPassedCases(int passedCases) { this.passedCases = passedCases; }
    public int getFailedCases() { return failedCases; }
    public void setFailedCases(int failedCases) { this.failedCases = failedCases; }
    public double getPassRate() { return passRate; }
    public void setPassRate(double passRate) { this.passRate = passRate; }
    public List<RagEvaluationCaseResult> getCases() { return cases; }
    public void setCases(List<RagEvaluationCaseResult> cases) { this.cases = cases; }
    public int getPositiveCases() { return positiveCases; }
    public void setPositiveCases(int positiveCases) { this.positiveCases = positiveCases; }
    public int getNoAnswerCases() { return noAnswerCases; }
    public void setNoAnswerCases(int noAnswerCases) { this.noAnswerCases = noAnswerCases; }
    public double getNoAnswerAccuracy() { return noAnswerAccuracy; }
    public void setNoAnswerAccuracy(double noAnswerAccuracy) { this.noAnswerAccuracy = noAnswerAccuracy; }
    public double getFalsePositiveRate() { return falsePositiveRate; }
    public void setFalsePositiveRate(double falsePositiveRate) { this.falsePositiveRate = falsePositiveRate; }

    public double getMrr() { return mrr; }
    public void setMrr(double mrr) { this.mrr = mrr; }
    public double getHitAt1() { return hitAt1; }
    public void setHitAt1(double hitAt1) { this.hitAt1 = hitAt1; }
    public double getHitAt3() { return hitAt3; }
    public void setHitAt3(double hitAt3) { this.hitAt3 = hitAt3; }
    public double getHitAt5() { return hitAt5; }
    public void setHitAt5(double hitAt5) { this.hitAt5 = hitAt5; }
    public double getRecallAt5() { return recallAt5; }
    public void setRecallAt5(double recallAt5) { this.recallAt5 = recallAt5; }
    public double getNdcg5() { return ndcg5; }
    public void setNdcg5(double ndcg5) { this.ndcg5 = ndcg5; }
    public int getEvalK() { return evalK; }
    public void setEvalK(int evalK) { this.evalK = evalK; }

    public double getFilterAccuracy() { return filterAccuracy; }
    public void setFilterAccuracy(double filterAccuracy) { this.filterAccuracy = filterAccuracy; }
    public double getRouteAccuracy() { return routeAccuracy; }
    public void setRouteAccuracy(double routeAccuracy) { this.routeAccuracy = routeAccuracy; }
    public double getFallbackAccuracy() { return fallbackAccuracy; }
    public void setFallbackAccuracy(double fallbackAccuracy) { this.fallbackAccuracy = fallbackAccuracy; }

    public Map<String, MetricBreakdown> getByCategory() { return byCategory; }
    public void setByCategory(Map<String, MetricBreakdown> byCategory) { this.byCategory = byCategory; }
    public Map<String, MetricBreakdown> getByDifficulty() { return byDifficulty; }
    public void setByDifficulty(Map<String, MetricBreakdown> byDifficulty) { this.byDifficulty = byDifficulty; }

    public SegmentMetrics getStructuredMetrics() { return structuredMetrics; }
    public void setStructuredMetrics(SegmentMetrics m) { this.structuredMetrics = m; }
    public SegmentMetrics getGeneralMetrics() { return generalMetrics; }
    public void setGeneralMetrics(SegmentMetrics m) { this.generalMetrics = m; }
    public SegmentMetrics getNegativeMetrics() { return negativeMetrics; }
    public void setNegativeMetrics(SegmentMetrics m) { this.negativeMetrics = m; }

    public static class SegmentMetrics {
        private int count;
        private double mrr;
        private double hitAt1;
        private double hitAt3;
        private double hitAt5;
        private double recallAt5;

        public SegmentMetrics() {}
        public SegmentMetrics(int count, double mrr, double hitAt1, double hitAt3, double hitAt5, double recallAt5) {
            this.count = count; this.mrr = mrr; this.hitAt1 = hitAt1; this.hitAt3 = hitAt3;
            this.hitAt5 = hitAt5; this.recallAt5 = recallAt5;
        }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public double getMrr() { return mrr; }
        public void setMrr(double mrr) { this.mrr = mrr; }
        public double getHitAt1() { return hitAt1; }
        public void setHitAt1(double h) { this.hitAt1 = h; }
        public double getHitAt3() { return hitAt3; }
        public void setHitAt3(double h) { this.hitAt3 = h; }
        public double getHitAt5() { return hitAt5; }
        public void setHitAt5(double h) { this.hitAt5 = h; }
        public double getRecallAt5() { return recallAt5; }
        public void setRecallAt5(double r) { this.recallAt5 = r; }
    }

    // ── inner types ──

    public static class MetricBreakdown {
        private int count;
        private double mrr;
        private double hitAt5;

        public MetricBreakdown() {}
        public MetricBreakdown(int count, double mrr, double hitAt5) {
            this.count = count; this.mrr = mrr; this.hitAt5 = hitAt5;
        }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public double getMrr() { return mrr; }
        public void setMrr(double mrr) { this.mrr = mrr; }
        public double getHitAt5() { return hitAt5; }
        public void setHitAt5(double hitAt5) { this.hitAt5 = hitAt5; }
    }

    public static class RagEvaluationCaseResult {
        private String id;
        private String query;
        private boolean passed;
        private String failureReason;
        private List<String> evidence = new ArrayList<>();
        private int firstRelevantRank = -1;
        private int relevantInTopK;
        private String category;
        private String difficulty;
        private List<String> tags = new ArrayList<>();
        // 系统级匹配
        private Boolean routeMatch;
        private Boolean filterMatch;
        private Boolean fallbackMatch;
        private List<com.ruoyi.postgrad.rag.dto.RagChunkResult> candidates = new ArrayList<>();
        private long latencyMs;
        private String technicalErrorCode;
        private String technicalErrorSummary;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        public List<String> getEvidence() { return evidence; }
        public void setEvidence(List<String> evidence) { this.evidence = evidence; }
        public int getFirstRelevantRank() { return firstRelevantRank; }
        public void setFirstRelevantRank(int firstRelevantRank) { this.firstRelevantRank = firstRelevantRank; }
        public int getRelevantInTopK() { return relevantInTopK; }
        public void setRelevantInTopK(int relevantInTopK) { this.relevantInTopK = relevantInTopK; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
        public Boolean getRouteMatch() { return routeMatch; }
        public void setRouteMatch(Boolean routeMatch) { this.routeMatch = routeMatch; }
        public Boolean getFilterMatch() { return filterMatch; }
        public void setFilterMatch(Boolean filterMatch) { this.filterMatch = filterMatch; }
        public Boolean getFallbackMatch() { return fallbackMatch; }
        public void setFallbackMatch(Boolean fallbackMatch) { this.fallbackMatch = fallbackMatch; }
        public List<com.ruoyi.postgrad.rag.dto.RagChunkResult> getCandidates() { return candidates; }
        public void setCandidates(List<com.ruoyi.postgrad.rag.dto.RagChunkResult> candidates) {
            this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
        }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
        public String getTechnicalErrorCode() { return technicalErrorCode; }
        public void setTechnicalErrorCode(String technicalErrorCode) { this.technicalErrorCode = technicalErrorCode; }
        public String getTechnicalErrorSummary() { return technicalErrorSummary; }
        public void setTechnicalErrorSummary(String technicalErrorSummary) { this.technicalErrorSummary = technicalErrorSummary; }
    }
}
