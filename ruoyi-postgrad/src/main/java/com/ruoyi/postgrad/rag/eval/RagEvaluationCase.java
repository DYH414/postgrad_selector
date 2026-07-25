package com.ruoyi.postgrad.rag.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagEvaluationCase {

    private String id;
    private String query;
    /** 检索过滤条件（只约束检索范围，不用于相关性判定） */
    private Map<String, String> expectedMetadata = new LinkedHashMap<>();
    private boolean sourceDataAvailable = true;
    private boolean noAnswer;

    // ── 相关性判定（与 filter 正交） ──
    /** 结构化招生类：多条 OR 匹配，每条按 programId/docType/section/year 判定 */
    private List<RelevantCondition> relevantConditions = new ArrayList<>();
    /** 通用知识类：sourceName 前缀匹配 */
    private List<String> relevantSources = new ArrayList<>();

    // ── 评测集分类 ──
    private String expectedRoute;
    private String ragUsage;
    private boolean expectedFallback;
    private String difficulty = "medium";
    private List<String> tags = new ArrayList<>();
    private String category;

    /** AGENT_E2E 用例规格；非 Agent 用例为 null（不序列化，不影响 RAG 冻结集 checksum）。 */
    private AgentCaseSpec agent;

    public RagEvaluationCase() {}

    public RagEvaluationCase(String id, String query, Map<String, String> expectedMetadata) {
        this.id = id;
        this.query = query;
        this.expectedMetadata = expectedMetadata != null ? new LinkedHashMap<>(expectedMetadata) : new LinkedHashMap<>();
    }

    // ── getters / setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Map<String, String> getExpectedMetadata() { return expectedMetadata; }
    public void setExpectedMetadata(Map<String, String> expectedMetadata) {
        this.expectedMetadata = expectedMetadata != null ? new LinkedHashMap<>(expectedMetadata) : new LinkedHashMap<>();
    }
    public boolean isSourceDataAvailable() { return sourceDataAvailable; }
    public void setSourceDataAvailable(boolean sourceDataAvailable) { this.sourceDataAvailable = sourceDataAvailable; }
    public boolean isNoAnswer() { return noAnswer || "safety".equals(category); }
    public void setNoAnswer(boolean noAnswer) { this.noAnswer = noAnswer; }

    public List<RelevantCondition> getRelevantConditions() { return relevantConditions; }
    public void setRelevantConditions(List<RelevantCondition> relevantConditions) {
        this.relevantConditions = relevantConditions != null ? relevantConditions : new ArrayList<>();
    }
    public List<String> getRelevantSources() { return relevantSources; }
    public void setRelevantSources(List<String> relevantSources) {
        this.relevantSources = relevantSources != null ? relevantSources : new ArrayList<>();
    }

    public String getExpectedRoute() { return expectedRoute; }
    public void setExpectedRoute(String expectedRoute) { this.expectedRoute = expectedRoute; }
    public String getRagUsage() { return ragUsage; }
    public void setRagUsage(String ragUsage) { this.ragUsage = ragUsage; }
    public boolean isExpectedFallback() { return expectedFallback; }
    public void setExpectedFallback(boolean expectedFallback) { this.expectedFallback = expectedFallback; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public AgentCaseSpec getAgent() { return agent; }
    public void setAgent(AgentCaseSpec agent) { this.agent = agent; }

    // ── 相关性条件（内部类） ──

    /**
     * 单个相关性条件。所有非 null 字段都匹配 → 该条件命中。
     * contentHash / programId / docType / section / year / schoolId 均可选（null=不检查该字段）。
     */
    public static class RelevantCondition {
        private String contentHash;
        private String programId;
        private String docType;
        private String section;
        private String year;
        private String schoolId;

        public RelevantCondition() {}

        public static RelevantCondition of(String programId, String docType, String section, String year) {
            RelevantCondition c = new RelevantCondition();
            c.programId = programId;
            c.docType = docType;
            c.section = section;
            c.year = year;
            return c;
        }

        public static RelevantCondition section(String section) {
            RelevantCondition c = new RelevantCondition();
            c.section = section;
            return c;
        }

        public boolean matches(String chunkContentHash, String chunkProgramId, String chunkDocType,
                               String chunkSection, String chunkYear, String chunkSchoolId) {
            if (contentHash != null && !contentHash.equals(chunkContentHash)) return false;
            if (programId != null && !programId.equals(chunkProgramId)) return false;
            if (docType != null && !docType.equals(chunkDocType)) return false;
            if (section != null && !section.equals(chunkSection)) return false;
            if (year != null && !year.equals(chunkYear)) return false;
            if (schoolId != null && !schoolId.equals(chunkSchoolId)) return false;
            return true;
        }

        public String getContentHash() { return contentHash; }
        public void setContentHash(String contentHash) { this.contentHash = contentHash; }
        public String getProgramId() { return programId; }
        public void setProgramId(String programId) { this.programId = programId; }
        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }
        public String getYear() { return year; }
        public void setYear(String year) { this.year = year; }
        public String getSchoolId() { return schoolId; }
        public void setSchoolId(String schoolId) { this.schoolId = schoolId; }
    }
}
