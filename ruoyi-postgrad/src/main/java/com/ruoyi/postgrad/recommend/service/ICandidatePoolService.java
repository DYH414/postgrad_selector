package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.TierCandidates;

/**
 * 候选池服务 —— 从 DB 查询候选 → 粗筛 → 规则分档。
 * <p>纯计算服务，不涉及 AI 调用。分档规则是确定性的。</p>
 */
public interface ICandidatePoolService {

    /**
     * 构建候选池并按规则分档。
     *
     * @param estimatedScore 预估分数
     * @param targetRegions  目标地区列表（空 = 不限）
     * @param schoolTierPreference 学校层次偏好
     * @return 三档候选（reach / steady / safe），每档含事实卡列表
     */
    TierCandidates buildPool(int estimatedScore, java.util.List<String> targetRegions, String schoolTierPreference);
}
