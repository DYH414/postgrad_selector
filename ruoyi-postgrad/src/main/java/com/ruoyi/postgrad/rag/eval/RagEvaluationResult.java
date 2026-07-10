package com.ruoyi.postgrad.rag.eval;

import java.util.ArrayList;
import java.util.List;

public class RagEvaluationResult {

    private int totalCases;
    private int passedCases;
    private int failedCases;
    private double passRate;
    private List<RagEvaluationCaseResult> cases = new ArrayList<>();

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

    public static class RagEvaluationCaseResult {
        private String id;
        private String query;
        private boolean passed;
        private String failureReason;
        private List<String> evidence = new ArrayList<>();

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
    }
}
