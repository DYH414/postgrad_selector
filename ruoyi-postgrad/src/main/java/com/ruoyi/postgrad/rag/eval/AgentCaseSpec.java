package com.ruoyi.postgrad.rag.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * AGENT_E2E 用例的期望规格（嵌套在 RagEvaluationCase.agent 下）。
 * 非 Agent 用例该字段为 null，不参与 JSON 序列化，从而不影响 RAG 冻结集 checksum。
 *
 * <p>字段语义（详见 agent-eval-implementation-spec §3.1）：</p>
 * <ul>
 *   <li>requiredTools/forbiddenTools —— 确定性规则判定，Python rules 使用</li>
 *   <li>expectedClaims/forbiddenClaims —— 交 Judge 的自然语言约束，规则引擎不做语义匹配</li>
 *   <li>referenceEvidence —— citationTokens 是确定性引用检查的唯一依据</li>
 * </ul>
 */
public class AgentCaseSpec {

    private List<String> requiredTools = new ArrayList<>();
    private List<String> forbiddenTools = new ArrayList<>();
    private boolean expectedAbstention;
    private List<String> expectedClaims = new ArrayList<>();
    private List<String> forbiddenClaims = new ArrayList<>();
    private List<ReferenceEvidence> referenceEvidence = new ArrayList<>();

    public List<String> getRequiredTools() { return requiredTools; }
    public void setRequiredTools(List<String> v) { this.requiredTools = v != null ? v : new ArrayList<>(); }
    public List<String> getForbiddenTools() { return forbiddenTools; }
    public void setForbiddenTools(List<String> v) { this.forbiddenTools = v != null ? v : new ArrayList<>(); }
    public boolean isExpectedAbstention() { return expectedAbstention; }
    public void setExpectedAbstention(boolean v) { this.expectedAbstention = v; }
    public List<String> getExpectedClaims() { return expectedClaims; }
    public void setExpectedClaims(List<String> v) { this.expectedClaims = v != null ? v : new ArrayList<>(); }
    public List<String> getForbiddenClaims() { return forbiddenClaims; }
    public void setForbiddenClaims(List<String> v) { this.forbiddenClaims = v != null ? v : new ArrayList<>(); }
    public List<ReferenceEvidence> getReferenceEvidence() { return referenceEvidence; }
    public void setReferenceEvidence(List<ReferenceEvidence> v) { this.referenceEvidence = v != null ? v : new ArrayList<>(); }

    /** 单条参考证据：答案须含任一 evidence 的全部 citationTokens 才算引用命中。 */
    public static class ReferenceEvidence {
        private String evidenceKey;
        private List<String> citationTokens = new ArrayList<>();

        public ReferenceEvidence() {}

        public String getEvidenceKey() { return evidenceKey; }
        public void setEvidenceKey(String evidenceKey) { this.evidenceKey = evidenceKey; }
        public List<String> getCitationTokens() { return citationTokens; }
        public void setCitationTokens(List<String> v) { this.citationTokens = v != null ? v : new ArrayList<>(); }
    }
}
