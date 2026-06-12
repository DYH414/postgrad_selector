package com.ruoyi.postgrad.recommend.service;

import java.util.List;

import com.ruoyi.postgrad.recommend.domain.TierCandidates;

/**
 * 候选池服务 —— 从 DB 查询候选 → 粗筛 → 规则分档。
 * <p>纯计算服务，不涉及 AI 调用。分档规则是确定性的。</p>
 */
public interface ICandidatePoolService {

    /**
     * 构建候选池并按规则分三档。
     *
     * @param estimatedScore        预估分数
     * @param targetRegions         目标地区列表（空 = 不限）
     * @param schoolTierPreference  学校层次偏好（用于综合得分加权）
     * @return 三档候选列表，顺序为 [reach, steady, safe]
     */
    List<TierCandidates> buildPool(int estimatedScore, List<String> targetRegions, String schoolTierPreference);
}
