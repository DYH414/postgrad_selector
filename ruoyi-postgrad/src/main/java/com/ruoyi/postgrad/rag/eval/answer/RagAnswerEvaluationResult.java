package com.ruoyi.postgrad.rag.eval.answer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagAnswerEvaluationResult {

    private Map<String, String> runMetadata = new LinkedHashMap<>();
    private int totalCases;
    private int generatedCases;
    private int judgedCases;
    private int generationErrorCases;
    private int judgeTechnicalErrors;
    private int consensusPassedCases;
    private double consensusPassRate;
    private double judgeAgreementRate;
    private long caseLatencyP50Ms;
    private long caseLatencyP95Ms;
    private double averageFaithfulness;
    private double averageAnswerRelevance;
    private double averageCitationCorrectness;
    private double averageAbstentionCorrectness;
    private String reportPath;
    private List<CaseResult> cases = new ArrayList<>();

    public Map<String, String> getRunMetadata() { return runMetadata; }
    public void setRunMetadata(Map<String, String> runMetadata) { this.runMetadata = runMetadata; }
    public int getTotalCases() { return totalCases; }
    public void setTotalCases(int totalCases) { this.totalCases = totalCases; }
    public int getGeneratedCases() { return generatedCases; }
    public void setGeneratedCases(int generatedCases) { this.generatedCases = generatedCases; }
    public int getJudgedCases() { return judgedCases; }
    public void setJudgedCases(int judgedCases) { this.judgedCases = judgedCases; }
    public int getGenerationErrorCases() { return generationErrorCases; }
    public void setGenerationErrorCases(int generationErrorCases) { this.generationErrorCases = generationErrorCases; }
    public int getJudgeTechnicalErrors() { return judgeTechnicalErrors; }
    public void setJudgeTechnicalErrors(int judgeTechnicalErrors) { this.judgeTechnicalErrors = judgeTechnicalErrors; }
    public int getConsensusPassedCases() { return consensusPassedCases; }
    public void setConsensusPassedCases(int consensusPassedCases) {
        this.consensusPassedCases = consensusPassedCases;
    }
    public double getConsensusPassRate() { return consensusPassRate; }
    public void setConsensusPassRate(double consensusPassRate) {
        this.consensusPassRate = consensusPassRate;
    }
    public double getJudgeAgreementRate() { return judgeAgreementRate; }
    public void setJudgeAgreementRate(double judgeAgreementRate) { this.judgeAgreementRate = judgeAgreementRate; }
    public long getCaseLatencyP50Ms() { return caseLatencyP50Ms; }
    public void setCaseLatencyP50Ms(long caseLatencyP50Ms) {
        this.caseLatencyP50Ms = caseLatencyP50Ms;
    }
    public long getCaseLatencyP95Ms() { return caseLatencyP95Ms; }
    public void setCaseLatencyP95Ms(long caseLatencyP95Ms) {
        this.caseLatencyP95Ms = caseLatencyP95Ms;
    }
    public double getAverageFaithfulness() { return averageFaithfulness; }
    public void setAverageFaithfulness(double averageFaithfulness) { this.averageFaithfulness = averageFaithfulness; }
    public double getAverageAnswerRelevance() { return averageAnswerRelevance; }
    public void setAverageAnswerRelevance(double averageAnswerRelevance) { this.averageAnswerRelevance = averageAnswerRelevance; }
    public double getAverageCitationCorrectness() { return averageCitationCorrectness; }
    public void setAverageCitationCorrectness(double averageCitationCorrectness) { this.averageCitationCorrectness = averageCitationCorrectness; }
    public double getAverageAbstentionCorrectness() { return averageAbstentionCorrectness; }
    public void setAverageAbstentionCorrectness(double averageAbstentionCorrectness) { this.averageAbstentionCorrectness = averageAbstentionCorrectness; }
    public String getReportPath() { return reportPath; }
    public void setReportPath(String reportPath) { this.reportPath = reportPath; }
    public List<CaseResult> getCases() { return cases; }
    public void setCases(List<CaseResult> cases) { this.cases = cases; }

    public static class CaseResult {
        private String id;
        private String query;
        private String category;
        private List<EvidenceSnapshot> evidence = new ArrayList<>();
        private String answer;
        private String technicalError;
        private long durationMs;
        private List<JudgeResult> judgments = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public List<EvidenceSnapshot> getEvidence() { return evidence; }
        public void setEvidence(List<EvidenceSnapshot> evidence) { this.evidence = evidence; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public String getTechnicalError() { return technicalError; }
        public void setTechnicalError(String technicalError) { this.technicalError = technicalError; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public List<JudgeResult> getJudgments() { return judgments; }
        public void setJudgments(List<JudgeResult> judgments) { this.judgments = judgments; }
    }

    public static class EvidenceSnapshot {
        private String label;
        private String sourceName;
        private String contentHash;
        private String content;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getSourceName() { return sourceName; }
        public void setSourceName(String sourceName) { this.sourceName = sourceName; }
        public String getContentHash() { return contentHash; }
        public void setContentHash(String contentHash) { this.contentHash = contentHash; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class JudgeResult {
        private Integer faithfulness;
        private Integer answerRelevance;
        private Integer citationCorrectness;
        private Integer abstentionCorrectness;
        private Boolean passed;
        private String reason;
        private String rawOutput;
        private String technicalError;

        public Integer getFaithfulness() { return faithfulness; }
        public void setFaithfulness(Integer faithfulness) { this.faithfulness = faithfulness; }
        public Integer getAnswerRelevance() { return answerRelevance; }
        public void setAnswerRelevance(Integer answerRelevance) { this.answerRelevance = answerRelevance; }
        public Integer getCitationCorrectness() { return citationCorrectness; }
        public void setCitationCorrectness(Integer citationCorrectness) { this.citationCorrectness = citationCorrectness; }
        public Integer getAbstentionCorrectness() { return abstentionCorrectness; }
        public void setAbstentionCorrectness(Integer abstentionCorrectness) { this.abstentionCorrectness = abstentionCorrectness; }
        public Boolean getPassed() { return passed; }
        public void setPassed(Boolean passed) { this.passed = passed; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getRawOutput() { return rawOutput; }
        public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }
        public String getTechnicalError() { return technicalError; }
        public void setTechnicalError(String technicalError) { this.technicalError = technicalError; }

        public boolean isValid() {
            return technicalError == null && faithfulness != null && answerRelevance != null
                && citationCorrectness != null && abstentionCorrectness != null && passed != null;
        }
    }
}
