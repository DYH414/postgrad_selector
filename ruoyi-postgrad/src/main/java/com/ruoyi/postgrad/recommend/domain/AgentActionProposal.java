package com.ruoyi.postgrad.recommend.domain;

import java.util.Map;

public class AgentActionProposal {
    private String token;
    private Long userId;
    private String runId;
    private String action;
    private Map<String, Object> arguments;
    private String summary;
    private String draftHash;
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDraftHash() { return draftHash; }
    public void setDraftHash(String draftHash) { this.draftHash = draftHash; }
}
