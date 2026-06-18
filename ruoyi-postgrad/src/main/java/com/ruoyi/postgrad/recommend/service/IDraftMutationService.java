package com.ruoyi.postgrad.recommend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;

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

    /**
     * 批量填充档位 — 从工作集选取最优候选填满指定档位。
     * <p>后端按策略自动选候选，AI 只需指定档位。</p>
     */
    DraftMutationResultVO fillTier(Long userId, String tier, CandidateWorkspaceVO workspace);

    /**
     * 批量移除候选 — 从草稿中移除多个学校，每个移除触发 refill 策略。
     */
    DraftMutationResultVO batchRemove(Long userId, List<Long> programIds,
                                       CandidateWorkspaceVO workspace);

    /**
     * 批量替换草稿候选 — 每个替换项独立校验，单项失败不阻塞其他项。
     * <p>至少一个成功 → ok=true；全部失败 → ok=false。不触发自动填充（替换即交换，非净变化）。</p>
     */
    Map<String, Object> batchReplace(Long userId, List<DraftReplacementRequest> replacements,
                                      CandidateWorkspaceVO workspace,
                                      Function<Long, CandidateCardVO> externalCandidateResolver);

    /**
     * 直接添加候选到草稿（跳过 Workspace 校验）。
     * <p>用于从外部 DB 搜索结果添加画像地区以外的学校。</p>
     */
    DraftMutationResultVO addCandidateDirect(Long userId, CandidateCardVO candidate, String tier);
}
