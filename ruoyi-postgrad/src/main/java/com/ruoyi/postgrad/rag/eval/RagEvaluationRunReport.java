package com.ruoyi.postgrad.rag.eval;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable result of one frozen dataset ablation run. */
public class RagEvaluationRunReport {

    private Map<RagEvaluationMode, RagEvaluationResult> results = new LinkedHashMap<>();
    private Map<RagEvaluationMode, RagEvaluationResult> safetyResults = new LinkedHashMap<>();
    private Map<RagEvaluationMode, AbstentionMetrics> abstentionMetrics = new LinkedHashMap<>();
    private Map<RagEvaluationMode, LatencyMetrics> latencyMetrics = new LinkedHashMap<>();
    private Map<String, String> runMetadata = new LinkedHashMap<>();
    private PairedComparison pairedComparison;
    private String reportPath;

    public Map<RagEvaluationMode, RagEvaluationResult> getResults() { return results; }
    public void setResults(Map<RagEvaluationMode, RagEvaluationResult> results) { this.results = results; }
    public Map<RagEvaluationMode, RagEvaluationResult> getSafetyResults() { return safetyResults; }
    public void setSafetyResults(Map<RagEvaluationMode, RagEvaluationResult> safetyResults) {
        this.safetyResults = safetyResults;
    }
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
    public PairedComparison getPairedComparison() { return pairedComparison; }
    public void setPairedComparison(PairedComparison pairedComparison) {
        this.pairedComparison = pairedComparison;
    }
    public String getReportPath() { return reportPath; }
    public void setReportPath(String reportPath) { this.reportPath = reportPath; }

    public static class AbstentionMetrics {
        private int totalCases;
        private int correctAbstentions;
        private int technicalErrorCases;
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
        public int getTechnicalErrorCases() { return technicalErrorCases; }
        public void setTechnicalErrorCases(int technicalErrorCases) {
            this.technicalErrorCases = technicalErrorCases;
        }
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

    public static class PairedComparison {
        private int comparedCases;
        private int improvedCases;
        private int tiedCases;
        private int regressedCases;

        public PairedComparison() {}

        public PairedComparison(int comparedCases, int improvedCases,
                                int tiedCases, int regressedCases) {
            this.comparedCases = comparedCases;
            this.improvedCases = improvedCases;
            this.tiedCases = tiedCases;
            this.regressedCases = regressedCases;
        }

        public int getComparedCases() { return comparedCases; }
        public void setComparedCases(int comparedCases) { this.comparedCases = comparedCases; }
        public int getImprovedCases() { return improvedCases; }
        public void setImprovedCases(int improvedCases) { this.improvedCases = improvedCases; }
        public int getTiedCases() { return tiedCases; }
        public void setTiedCases(int tiedCases) { this.tiedCases = tiedCases; }
        public int getRegressedCases() { return regressedCases; }
        public void setRegressedCases(int regressedCases) { this.regressedCases = regressedCases; }
    }
}
