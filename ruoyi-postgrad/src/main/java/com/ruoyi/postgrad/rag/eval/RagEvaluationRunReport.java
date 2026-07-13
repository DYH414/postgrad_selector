package com.ruoyi.postgrad.rag.eval;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable result of one frozen dataset ablation run. */
public class RagEvaluationRunReport {

    private Map<RagEvaluationMode, RagEvaluationResult> results = new LinkedHashMap<>();
    private Map<RagEvaluationMode, AbstentionMetrics> abstentionMetrics = new LinkedHashMap<>();
    private Map<RagEvaluationMode, LatencyMetrics> latencyMetrics = new LinkedHashMap<>();
    private Map<String, String> runMetadata = new LinkedHashMap<>();
    private String reportPath;

    public Map<RagEvaluationMode, RagEvaluationResult> getResults() { return results; }
    public void setResults(Map<RagEvaluationMode, RagEvaluationResult> results) { this.results = results; }
    public Map<RagEvaluationMode, AbstentionMetrics> getAbstentionMetrics() { return abstentionMetrics; }
    public void setAbstentionMetrics(Map<RagEvaluationMode, AbstentionMetrics> abstentionMetrics) {
        this.abstentionMetrics = abstentionMetrics;
    }
    public Map<RagEvaluationMode, LatencyMetrics> getLatencyMetrics() { return latencyMetrics; }
    public void setLatencyMetrics(Map<RagEvaluationMode, LatencyMetrics> latencyMetrics) {
        this.latencyMetrics = latencyMetrics;
    }
    public Map<String, String> getRunMetadata() { return runMetadata; }
    public void setRunMetadata(Map<String, String> runMetadata) { this.runMetadata = runMetadata; }
    public String getReportPath() { return reportPath; }
    public void setReportPath(String reportPath) { this.reportPath = reportPath; }

    public static class AbstentionMetrics {
        private int totalCases;
        private int correctAbstentions;
        private double noAnswerAccuracy;
        private double falsePositiveRate;

        public AbstentionMetrics() {}

        public AbstentionMetrics(int totalCases, int correctAbstentions,
                                  double noAnswerAccuracy, double falsePositiveRate) {
            this.totalCases = totalCases;
            this.correctAbstentions = correctAbstentions;
            this.noAnswerAccuracy = noAnswerAccuracy;
            this.falsePositiveRate = falsePositiveRate;
        }

        public int getTotalCases() { return totalCases; }
        public void setTotalCases(int totalCases) { this.totalCases = totalCases; }
        public int getCorrectAbstentions() { return correctAbstentions; }
        public void setCorrectAbstentions(int correctAbstentions) { this.correctAbstentions = correctAbstentions; }
        public double getNoAnswerAccuracy() { return noAnswerAccuracy; }
        public void setNoAnswerAccuracy(double noAnswerAccuracy) { this.noAnswerAccuracy = noAnswerAccuracy; }
        public double getFalsePositiveRate() { return falsePositiveRate; }
        public void setFalsePositiveRate(double falsePositiveRate) { this.falsePositiveRate = falsePositiveRate; }
    }

    public static class LatencyMetrics {
        private int sampleCount;
        private long p50Ms;
        private long p95Ms;

        public LatencyMetrics() {}

        public LatencyMetrics(int sampleCount, long p50Ms, long p95Ms) {
            this.sampleCount = sampleCount;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
        }

        public int getSampleCount() { return sampleCount; }
        public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
        public long getP50Ms() { return p50Ms; }
        public void setP50Ms(long p50Ms) { this.p50Ms = p50Ms; }
        public long getP95Ms() { return p95Ms; }
        public void setP95Ms(long p95Ms) { this.p95Ms = p95Ms; }
    }
}
