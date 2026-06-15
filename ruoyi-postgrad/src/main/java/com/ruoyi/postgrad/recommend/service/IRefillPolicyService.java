package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.ExcludedCandidateVO;
import com.ruoyi.postgrad.recommend.domain.RefillResultVO;

import java.util.List;

/**
 * 填充策略服务 —— 决定移除后的填充行为。
 */
public interface IRefillPolicyService {

    /**
     * 根据移除的候选和当前工作集，决定填充策略。
     *
     * @param workspace   当前工作集
     * @param excluded    已排除候选列表
     * @param removedTier 被移除候选的档位
     * @param draftIds    当前草稿中已有的 programId 集合（避免重复）
     * @return 填充结果（auto/confirm/none）
     */
    RefillResultVO evaluate(CandidateWorkspaceVO workspace,
                             List<ExcludedCandidateVO> excluded,
                             String removedTier,
                             java.util.Set<Long> draftIds);

    /**
     * 确认候选后执行添加（AI 或用户选择了一个 confirm 候选）。
     *
     * @param workspace  当前工作集
     * @param programId  要添加的候选 ID
     * @param tier       目标档位
     * @return 对应的 CandidateCardVO，未找到返回 null
     */
    CandidateCardVO confirmCandidate(CandidateWorkspaceVO workspace, Long programId, String tier);
}
