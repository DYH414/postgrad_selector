package com.ruoyi.postgrad.domain.ai;

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

    // ── 裁决字段 ──
    /** AI 原始判断（addToReport 传入的值） */
    private String aiJudgement;
    /** 后端统一裁决后的最终档位 reach/steady/safe，judgement 字段存此值以兼容旧逻辑 */
    private String finalJudgement;
    /** 是否被后端降级/调整 */
    private Boolean adjusted;
    /** 调整原因（仅 adjusted=true 时有值） */
    private String adjustReason;

    public AiBookmark() {}

    public long getProgramId() { return programId; }
    public void setProgramId(long programId) { this.programId = programId; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String v) { this.schoolName = v; }
    public String getProgramName() { return programName; }
    public void setProgramName(String v) { this.programName = v; }
    public String getJudgement() { return judgement; }
    /** 设置 judgement，同时兼容旧代码。调用方应优先使用 setFinalJudgement */
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

    // ── 裁决字段 getter/setter ──
    public String getAiJudgement() { return aiJudgement; }
    public void setAiJudgement(String v) { this.aiJudgement = v; }
    public String getFinalJudgement() { return finalJudgement; }
    public void setFinalJudgement(String v) { this.finalJudgement = v; }
    public Boolean getAdjusted() { return adjusted; }
    public void setAdjusted(Boolean v) { this.adjusted = v; }
    public String getAdjustReason() { return adjustReason; }
    public void setAdjustReason(String v) { this.adjustReason = v; }
}
