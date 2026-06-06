package com.ruoyi.postgrad.domain;

import java.util.List;

/** AI 报告书签 —— 对话中通过 addToReport 工具累积的候选学校记录 */
public class AiBookmark {
    private long programId;
    private String schoolName;
    private String programName;
    private String judgement; // reach / steady / safe（后端校验白名单）
    private String reason;
    private List<String> pros;
    private List<String> cons;
    private List<String> tradeoffs;
    private String recommendedAction;
    /** 来源：conversation_ai / auto_fill_search / background_ai / rule_fallback / user_confirmed */
    private String source;
    /** 状态：suggested / discussed / confirmed / removed / rejected */
    private String status;
    private boolean userConfirmed;

    public AiBookmark() {}

    public long getProgramId() { return programId; }
    public void setProgramId(long programId) { this.programId = programId; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String v) { this.schoolName = v; }
    public String getProgramName() { return programName; }
    public void setProgramName(String v) { this.programName = v; }
    public String getJudgement() { return judgement; }
    public void setJudgement(String v) { this.judgement = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public List<String> getPros() { return pros; }
    public void setPros(List<String> v) { this.pros = v; }
    public List<String> getCons() { return cons; }
    public void setCons(List<String> v) { this.cons = v; }
    public List<String> getTradeoffs() { return tradeoffs; }
    public void setTradeoffs(List<String> v) { this.tradeoffs = v; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String v) { this.recommendedAction = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public boolean isUserConfirmed() { return userConfirmed; }
    public void setUserConfirmed(boolean v) { this.userConfirmed = v; }
}
