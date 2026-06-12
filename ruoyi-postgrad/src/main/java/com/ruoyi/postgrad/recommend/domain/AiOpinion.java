package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.List;

/**
 * AI 观点 —— AI 对候选学校的纯分析产出，后端不修改其语义。
 * <p>仅包含 AI 的自然语言分析字段。裁决和状态字段属于草稿生命周期，放在 CandidateCardVO 顶层。</p>
 */
public class AiOpinion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** AI 推荐理由 */
    private String reason;

    /** AI 识别的风险 */
    private List<String> risks;

    /** AI 列出的优势 */
    private List<String> pros;

    /** AI 列出的劣势 */
    private List<String> cons;

    /** AI 列出的权衡项 */
    private List<String> tradeoffs;

    /** AI 建议的后续行动 */
    private String recommendedAction;

    // ── getters / setters ──

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public List<String> getRisks() { return risks; }
    public void setRisks(List<String> risks) { this.risks = risks; }

    public List<String> getPros() { return pros; }
    public void setPros(List<String> pros) { this.pros = pros; }

    public List<String> getCons() { return cons; }
    public void setCons(List<String> cons) { this.cons = cons; }

    public List<String> getTradeoffs() { return tradeoffs; }
    public void setTradeoffs(List<String> tradeoffs) { this.tradeoffs = tradeoffs; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
}
