package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.CandidateUniverseVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;

/**
 * 候选工作集服务 —— 从 Universe 构建分层活动候选集。
 * <p>每档最多 30 个候选，按策略排序，包含多样性约束。</p>
 */
public interface ICandidateWorkspaceService {

    /** 每档默认上限 */
    int DEFAULT_TIER_LIMIT = 30;

    /**
     * 从 Universe 构建 Workspace。
     *
     * @param universe          候选宇宙
     * @param schoolTierPref    学校层次偏好
     * @return 三层候选工作集
     */
    CandidateWorkspaceVO buildWorkspace(CandidateUniverseVO universe, String schoolTierPref);
}
