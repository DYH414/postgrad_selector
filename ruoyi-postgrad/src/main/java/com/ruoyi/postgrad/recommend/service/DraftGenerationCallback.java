package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;

/**
 * 草稿生成进度回调 —— Service 层不依赖 web 层（SseEmitter），
 * 由 Controller 实现此接口，将进度事件桥接到 SSE。
 */
public interface DraftGenerationCallback {

    /**
     * 推送进度事件。
     *
     * @param phase   阶段标识：loading_profile / building_pool / ai_selecting / validating
     * @param message 人类可读的进度描述
     * @param found   候选池中找到的数量（仅 building_pool 阶段有意义，其他阶段可为 null）
     * @param tier    当前 AI 选校的档位（仅 ai_selecting 阶段有意义）
     */
    void onProgress(String phase, String message, Integer found, String tier);

    /**
     * 草稿生成完成。
     *
     * @param draft       生成的草稿
     * @param profileBasis 画像依据
     * @param removedCount 被校验拦截的候选数
     */
    void onDone(DraftVO draft, ProfileBasisVO profileBasis, int removedCount);

    /**
     * 单档 AI 选校完成（逐档实时推送，不等全部完成）。
     *
     * @param tier     档位标识：reach / steady / safe
     * @param tierJson 该档完整 TierCandidates JSON（含已选的候选+理由+风险）
     */
    default void onTierComplete(String tier, String tierJson) {}

    /**
     * 草稿生成失败。
     *
     * @param error 异常
     */
    void onError(Throwable error);
}
