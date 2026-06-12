package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 单个候选的完整信息卡片 —— 前端草稿卡片和报告行的数据来源。
 * <p>三层结构：
 * <ul>
 *   <li>{@link SchoolFact} — 系统事实（DB + 后端计算），AI 不得编造</li>
 *   <li>{@link AiOpinion} — AI 纯观点（理由、风险、pros/cons），AI 生成原样透传</li>
 *   <li>顶层裁决/状态 — 后端校验结果 + 草稿生命周期状态</li>
 * </ul>
 */
public class CandidateCardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 系统事实数据 */
    private SchoolFact fact;

    /** AI 纯观点数据 */
    private AiOpinion opinion;

    /** 后端最终裁决档位：reach / steady / safe */
    private String finalJudgement;

    /** 是否被后端降级（finalJudgement != AI 原始判断） */
    private Boolean adjusted;

    /** 降级原因（仅 adjusted=true 时有值） */
    private String adjustReason;

    /** 草稿生命周期状态：selected / removed / verified_pending */
    private String status;

    /** 展示标签（从 fact + opinion 派生，供前端渲染） */
    private List<String> tags;

    // ── 工厂方法 ──

    /**
     * 创建仅含事实数据的卡片（AI 尚未分析时使用）。
     */
    public static CandidateCardVO fromFact(SchoolFact fact) {
        CandidateCardVO vo = new CandidateCardVO();
        vo.setFact(fact);
        return vo;
    }

    /**
     * 创建包含 AI 观点的完整卡片。
     */
    public static CandidateCardVO of(SchoolFact fact, AiOpinion opinion) {
        CandidateCardVO vo = new CandidateCardVO();
        vo.setFact(fact);
        vo.setOpinion(opinion);
        return vo;
    }

    // ── getters / setters ──

    public SchoolFact getFact() { return fact; }
    public void setFact(SchoolFact fact) { this.fact = fact; }

    public AiOpinion getOpinion() { return opinion; }
    public void setOpinion(AiOpinion opinion) { this.opinion = opinion; }

    public String getFinalJudgement() { return finalJudgement; }
    public void setFinalJudgement(String finalJudgement) { this.finalJudgement = finalJudgement; }

    public Boolean getAdjusted() { return adjusted; }
    public void setAdjusted(Boolean adjusted) { this.adjusted = adjusted; }

    public String getAdjustReason() { return adjustReason; }
    public void setAdjustReason(String adjustReason) { this.adjustReason = adjustReason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
