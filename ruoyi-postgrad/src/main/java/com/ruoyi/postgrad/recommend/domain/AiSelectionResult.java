package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.List;

/**
 * AI 选校返回 —— AI 在单档内挑选的结果。
 * <p>内部模型，在 AiSelectorService → SelectionValidator → DraftService 之间流转。</p>
 */
public class AiSelectionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所选档位 */
    private String tier;

    /** AI 选中的候选 */
    private List<SelectedItem> selected;

    /** 被校验拦截的候选（不在池内、重复、数据不足等） */
    private List<BlockedItem> blocked;

    // ── 内嵌类 ──

    /** AI 选中的单个候选 */
    public static class SelectedItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long programId;
        private String reason;
        private List<String> risks;
        private List<String> pros;
        private List<String> cons;

        public Long getProgramId() { return programId; }
        public void setProgramId(Long programId) { this.programId = programId; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public List<String> getRisks() { return risks; }
        public void setRisks(List<String> risks) { this.risks = risks; }

        public List<String> getPros() { return pros; }
        public void setPros(List<String> pros) { this.pros = pros; }

        public List<String> getCons() { return cons; }
        public void setCons(List<String> cons) { this.cons = cons; }
    }

    /** 被拦截的候选 */
    public static class BlockedItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long programId;
        private String schoolName;
        private String blockReason;

        public Long getProgramId() { return programId; }
        public void setProgramId(Long programId) { this.programId = programId; }

        public String getSchoolName() { return schoolName; }
        public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

        public String getBlockReason() { return blockReason; }
        public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    }

    // ── getters / setters ──

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public List<SelectedItem> getSelected() { return selected; }
    public void setSelected(List<SelectedItem> selected) { this.selected = selected; }

    public List<BlockedItem> getBlocked() { return blocked; }
    public void setBlocked(List<BlockedItem> blocked) { this.blocked = blocked; }
}
