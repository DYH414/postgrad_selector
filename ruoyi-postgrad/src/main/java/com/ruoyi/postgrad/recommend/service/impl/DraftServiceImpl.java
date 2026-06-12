package com.ruoyi.postgrad.recommend.service.impl;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ReplaceResultVO;
import com.ruoyi.postgrad.recommend.service.IDraftService;

/**
 * 草稿服务实现 —— 编排候选池构建、AI 选择、校验、Redis 持久化。
 *
 * <p>TODO: 实现 generateDraft / getDraft / removeCandidate / replaceCandidate / addBackCandidate / getAlternatives</p>
 */
@Service
public class DraftServiceImpl implements IDraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftServiceImpl.class);

    /** Redis key 前缀：草稿 */
    private static final String DRAFT_KEY_PREFIX = "ai:v2:draft:";

    /** Redis key 前缀：候选池快照（用于替换/加回操作） */
    private static final String DRAFT_POOL_KEY_PREFIX = "ai:v2:draft:pool:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    // TODO: 注入 ICandidatePoolService
    // TODO: 注入 IAiSelectorService
    // TODO: 注入 UserProfileMapper

    @Override
    public SseEmitter generateDraft(Long userId) {
        // TODO: 实现 SSE 流式生成草稿
        // 1. 推送 progress: loading_profile
        // 2. 加载用户画像
        // 3. 推送 progress: building_pool
        // 4. 调用 ICandidatePoolService.buildPool()
        // 5. 每档推送 progress: ai_selecting:{tier}
        // 6. 串行调用 IAiSelectorService.select() × 3
        // 7. 推送 progress: validating
        // 8. 调用 SelectionValidator 校验
        // 9. 构建 DraftVO → 存储到 Redis
        // 10. 推送 done
        // 11. 异常时推送 error
        throw new UnsupportedOperationException("TODO: implement generateDraft");
    }

    @Override
    public DraftVO getDraft(Long userId) {
        // TODO: 从 Redis ai:v2:draft:{userId} 读取草稿
        // 不存在时返回空的 DraftVO（tiers 为三档空数组）
        throw new UnsupportedOperationException("TODO: implement getDraft");
    }

    @Override
    public DraftVO removeCandidate(Long userId, Long programId) {
        // TODO: 从草稿中移除指定候选，写入 removedCandidates，写回 Redis
        throw new UnsupportedOperationException("TODO: implement removeCandidate");
    }

    @Override
    public ReplaceResultVO replaceCandidate(Long userId, Long removeProgramId, String tier, String preference) {
        // TODO: 从同档候选池中选择替代候选
        // 1. 将 removeProgramId 移入 removedCandidates
        // 2. 从候选池快照中按 preference 选择替代
        // 3. 将替代候选加入草稿
        // 4. 写回 Redis
        throw new UnsupportedOperationException("TODO: implement replaceCandidate");
    }

    @Override
    public DraftVO addBackCandidate(Long userId, Long programId) {
        // TODO: 从 removedCandidates 中找回并加回对应档位
        throw new UnsupportedOperationException("TODO: implement addBackCandidate");
    }

    @Override
    public List<CandidateCardVO> getAlternatives(Long userId, String tier, Long excludeId) {
        // TODO: 从候选池快照中获取同档其他候选（排除已在草稿中的）
        throw new UnsupportedOperationException("TODO: implement getAlternatives");
    }

    // ── private helpers (to be added) ──

    /**
     * 构建用户草稿的 Redis key。
     *
     * @param userId 用户 ID
     * @return Redis key
     */
    private String draftKey(Long userId) {
        return DRAFT_KEY_PREFIX + userId;
    }

    /**
     * 构建用户候选池快照的 Redis key。
     *
     * @param userId 用户 ID
     * @return Redis key
     */
    private String draftPoolKey(Long userId) {
        return DRAFT_POOL_KEY_PREFIX + userId;
    }
}
