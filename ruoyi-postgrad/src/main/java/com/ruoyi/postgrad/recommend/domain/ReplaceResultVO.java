package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * 替换候选操作的结果 —— 更新后的草稿 + 新加入的候选信息。
 */
public class ReplaceResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 更新后的草稿 */
    private DraftVO draft;

    /** 新替换进来的候选 */
    private CandidateCardVO replacedWith;

    // ── getters / setters ──

    public DraftVO getDraft() { return draft; }
    public void setDraft(DraftVO draft) { this.draft = draft; }

    public CandidateCardVO getReplacedWith() { return replacedWith; }
    public void setReplacedWith(CandidateCardVO replacedWith) { this.replacedWith = replacedWith; }
}
