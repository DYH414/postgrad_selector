package com.ruoyi.postgrad.recommend.service;

import java.util.List;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ReplaceResultVO;

/**
 * 草稿服务 —— 草稿的完整生命周期管理。
 * <p>草稿按 userId 存储（一个用户同时只有一个草稿），Redis key: {@code ai:v2:draft:{userId}}。</p>
 */
public interface IDraftService {

    /**
     * 生成草稿（通过回调推送进度，不依赖 web 层）。
     * <p>回调事件序列：onProgress(loading_profile) → onProgress(building_pool) →
     * onProgress(ai_selecting:reach) → onProgress(ai_selecting:steady) → onProgress(ai_selecting:safe) →
     * onProgress(validating) → onDone(draft, profileBasis) | onError(throwable)。</p>
     *
     * @param userId   当前用户 ID
     * @param callback 进度回调（由 Controller 层实现，桥接到 SSE）
     */
    void generateDraft(Long userId, DraftGenerationCallback callback);

    /**
     * 获取当前草稿。
     *
     * @param userId 当前用户 ID
     * @return 草稿状态；不存在时返回空 TierCandidates 的 DraftVO
     */
    DraftVO getDraft(Long userId);

    /**
     * 从草稿中移除候选。
     *
     * @param userId    当前用户 ID
     * @param programId 要移除的候选 programId
     * @return 更新后的草稿
     */
    DraftVO removeCandidate(Long userId, Long programId);

    /**
     * 替换草稿中的候选。
     *
     * @param userId          当前用户 ID
     * @param removeProgramId 要移除的候选 programId
     * @param tier            目标档位：reach / steady / safe
     * @param preference      替换偏好：safer / higher_tier / closer_region
     * @return 更新后的草稿 + 新替换进来的候选
     */
    ReplaceResultVO replaceCandidate(Long userId, Long removeProgramId, String tier, String preference);

    /**
     * 将之前移除的候选加回草稿。
     *
     * @param userId    当前用户 ID
     * @param programId 要加回的候选 programId
     * @return 更新后的草稿
     */
    DraftVO addBackCandidate(Long userId, Long programId);

    /**
     * 获取同档其他可选候选（用于替换操作）。
     *
     * @param userId    当前用户 ID
     * @param tier      目标档位：reach / steady / safe
     * @param excludeId 排除的 programId（当前草稿中已有的候选也可排除）
     * @return 可选候选列表（按综合得分排序）
     */
    List<CandidateCardVO> getAlternatives(Long userId, String tier, Long excludeId);
}
