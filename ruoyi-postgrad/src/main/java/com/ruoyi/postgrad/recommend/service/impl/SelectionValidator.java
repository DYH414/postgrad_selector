package com.ruoyi.postgrad.recommend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;

/**
 * AI 结果校验器 —— 池外检测、去重、数据不足降级、档位上限控制。
 * <p>在校验失败时生成 BlockedItem 并记录原因，供前端展示"为什么没选XX"。</p>
 */
@Component
public class SelectionValidator {

    private static final Logger log = LoggerFactory.getLogger(SelectionValidator.class);

    /** 每档上限 */
    private static final int REACH_LIMIT = 3;
    private static final int STEADY_LIMIT = 4;
    private static final int SAFE_LIMIT = 3;

    /**
     * 校验 AI 选择结果。
     *
     * @param tier       档位标识
     * @param selected   AI 选中的候选
     * @param pool       该档候选池（用于池外检测 + 数据信息补充）
     * @return 校验后的 AiSelectionResult
     */
    public AiSelectionResult validate(String tier, List<AiSelectionResult.SelectedItem> selected,
                                       List<CandidateCardVO> pool) {
        // TODO: 实现校验逻辑
        // 1. 构建 pool programId → CandidateCardVO 映射
        // 2. 池外检测：不在 pool 中的 → blocked("候选不在系统候选池内")
        // 3. 去重检测：同一 programId 已出现 → blocked("候选重复")
        // 4. 数据不足降级：dataCompleteness == "C" → blocked("数据完整度不足")
        // 5. 档位上限裁剪：超过该档上限的 → 丢弃
        // 6. 返回 AiSelectionResult（含 selected + blocked）
        throw new UnsupportedOperationException("TODO: implement validate");
    }

    // ── private helpers (to be added) ──

    private int tierLimit(String tier) {
        return switch (tier) {
            case "reach" -> REACH_LIMIT;
            case "steady" -> STEADY_LIMIT;
            case "safe" -> SAFE_LIMIT;
            default -> 3;
        };
    }
}
