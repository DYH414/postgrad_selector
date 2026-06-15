package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;

/**
 * 草稿变更服务 —— 移除、添加、替换的统一编排。
 * <p>所有草稿写操作都经过此服务，确保填充策略和决策日志一致执行。</p>
 */
public interface IDraftMutationService {

    /**
     * 从草稿中移除候选，并触发填充策略。
     */
    DraftMutationResultVO removeCandidate(Long userId, Long programId,
                                           CandidateWorkspaceVO workspace);

    /**
     * 从工作集添加候选到草稿。
     */
    DraftMutationResultVO addCandidate(Long userId, Long programId, String tier,
                                        CandidateWorkspaceVO workspace);

    /**
     * 替换草稿中的候选（移除 + 添加）。
     */
    DraftMutationResultVO replaceCandidate(Long userId, Long removeProgramId,
                                            Long addProgramId, String tier,
                                            CandidateWorkspaceVO workspace);

    /**
     * 确认填充候选（用户/AI 从 confirm 列表中选择）。
     */
    DraftMutationResultVO confirmRefillCandidate(Long userId, Long programId, String tier,
                                                  CandidateWorkspaceVO workspace);
}
