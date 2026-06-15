package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.CandidateUniverseVO;
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;

/**
 * 候选宇宙服务 —— 从 DB 规则查询构建广泛候选供给。
 * <p>纯计算，不涉及 AI。返回的 Universe 应包含数十到数百个候选。</p>
 */
public interface ICandidateUniverseService {

    /**
     * 基于用户画像构建候选宇宙。
     *
     * @param userId            用户 ID
     * @param profile           用户画像
     * @param estimatedScore    预估分数
     * @param targetRegions     目标地区
     * @param schoolTierPref    学校层次偏好
     * @return 候选宇宙（含广泛候选列表和来源摘要）
     */
    CandidateUniverseVO buildUniverse(Long userId, ProfileBasisVO profile,
                                       int estimatedScore, java.util.List<String> targetRegions,
                                       String schoolTierPref);
}
