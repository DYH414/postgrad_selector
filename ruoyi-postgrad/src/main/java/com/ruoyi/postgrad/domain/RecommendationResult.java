package com.ruoyi.postgrad.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐结果
 */
public class RecommendationResult
{
    private List<RecommendationItem> steady = new ArrayList<>();
    private List<RecommendationItem> focus = new ArrayList<>();
    private List<RecommendationItem> reach = new ArrayList<>();
    private List<RecommendationItem> notRecommended = new ArrayList<>();
    private List<RecommendationItem> insufficient = new ArrayList<>();
    private List<RecommendationItem> overflow = new ArrayList<>();
    private int totalCandidates;

    // ── getters & setters ──

    public List<RecommendationItem> getSteady() { return steady; }
    public void setSteady(List<RecommendationItem> steady) { this.steady = steady; }

    public List<RecommendationItem> getFocus() { return focus; }
    public void setFocus(List<RecommendationItem> focus) { this.focus = focus; }

    public List<RecommendationItem> getReach() { return reach; }
    public void setReach(List<RecommendationItem> reach) { this.reach = reach; }

    public List<RecommendationItem> getNotRecommended() { return notRecommended; }
    public void setNotRecommended(List<RecommendationItem> notRecommended) { this.notRecommended = notRecommended; }

    public List<RecommendationItem> getInsufficient() { return insufficient; }
    public void setInsufficient(List<RecommendationItem> insufficient) { this.insufficient = insufficient; }

    public List<RecommendationItem> getOverflow() { return overflow; }
    public void setOverflow(List<RecommendationItem> overflow) { this.overflow = overflow; }

    public int getTotalCandidates() { return totalCandidates; }
    public void setTotalCandidates(int totalCandidates) { this.totalCandidates = totalCandidates; }
}
