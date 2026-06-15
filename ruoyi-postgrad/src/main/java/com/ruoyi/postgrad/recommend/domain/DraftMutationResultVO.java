package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 草稿变更操作结果 —— 移除、添加、替换的统一返回体。
 */
public class DraftMutationResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean ok;
    private String action;          // remove / add / replace
    private DraftVO draft;          // 变更后的草稿
    private RefillResultVO refill;  // 填充信息
    private int draftCount;         // 当前草稿学校数
    private List<DraftDecisionLogVO> decisionLog = new ArrayList<>();

    // ── getters / setters ──

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public DraftVO getDraft() { return draft; }
    public void setDraft(DraftVO draft) { this.draft = draft; }

    public RefillResultVO getRefill() { return refill; }
    public void setRefill(RefillResultVO refill) { this.refill = refill; }

    public int getDraftCount() { return draftCount; }
    public void setDraftCount(int draftCount) { this.draftCount = draftCount; }

    public List<DraftDecisionLogVO> getDecisionLog() { return decisionLog; }
    public void setDecisionLog(List<DraftDecisionLogVO> decisionLog) { this.decisionLog = decisionLog; }
}
