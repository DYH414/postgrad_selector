package com.ruoyi.postgrad.recommend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult.BlockedItem;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult.SelectedItem;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;

/**
 * AI 结果校验器 —— 池外检测、去重、数据不足降级、档位上限控制。
 * <p>纯函数，无状态。每个校验失败生成 BlockedItem 并记录原因。</p>
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
     * @param tier     档位标识：reach / steady / safe
     * @param selected AI 选中的候选列表
     * @param pool     该档候选池（用于池外检测 + 补充学校名）
     * @return 校验后的 AiSelectionResult（含通过的和被拦截的）
     */
    public AiSelectionResult validate(String tier, List<SelectedItem> selected,
                                       List<CandidateCardVO> pool) {
        // 1. 构建 pool programId → CandidateCardVO 映射（跳过 null fact 和无 programId）
        Map<Long, CandidateCardVO> poolMap = pool.stream()
            .filter(c -> c.getFact() != null && c.getFact().getProgramId() != null)
            .collect(Collectors.toMap(
                c -> c.getFact().getProgramId(),
                c -> c,
                (existing, replacement) -> existing // 重复 key 保留第一个
            ));

        List<SelectedItem> passed = new ArrayList<>();
        List<BlockedItem> blocked = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        int limit = tierLimit(tier);

        for (SelectedItem item : selected) {
            Long pid = item.getProgramId();
            if (pid == null) {
                blocked.add(block(pid, "未知", "programId 为空"));
                continue;
            }

            // 2. 池外检测
            CandidateCardVO poolCard = poolMap.get(pid);
            if (poolCard == null) {
                blocked.add(block(pid, "未知", "候选不在系统候选池内，AI 幻觉已拦截"));
                log.warn("[Validator] tier={} — programId={} not in pool, blocked as hallucination", tier, pid);
                continue;
            }
            String schoolName = poolCard.getFact().getSchoolName();

            // 3. 去重检测
            if (!seen.add(pid)) {
                blocked.add(block(pid, schoolName, "候选重复，已去除"));
                continue;
            }

            // 4. 数据不足降级
            SchoolFact fact = poolCard.getFact();
            if ("C".equalsIgnoreCase(fact.getDataCompleteness())) {
                blocked.add(block(pid, schoolName, "数据完整度为 C，不具备作为主推荐的条件"));
                continue;
            }

            // 5. 通过
            passed.add(item);
        }

        // 6. 档位上限裁剪
        List<SelectedItem> trimmed = passed;
        if (passed.size() > limit) {
            List<SelectedItem> overflow = passed.subList(limit, passed.size());
            for (SelectedItem item : overflow) {
                CandidateCardVO poolCard = poolMap.get(item.getProgramId());
                String name = poolCard != null ? poolCard.getFact().getSchoolName() : "未知";
                blocked.add(block(item.getProgramId(), name,
                    String.format("档位上限已满（%d/%d），该候选未入选", limit, passed.size())));
            }
            trimmed = new ArrayList<>(passed.subList(0, limit));
        }

        log.info("[Validator] tier={} — passed={}, blocked={}", tier, trimmed.size(), blocked.size());

        AiSelectionResult result = new AiSelectionResult();
        result.setTier(tier);
        result.setSelected(trimmed);
        result.setBlocked(blocked);
        return result;
    }

    // ── helpers ──

    private int tierLimit(String tier) {
        return switch (tier) {
            case "reach" -> REACH_LIMIT;
            case "steady" -> STEADY_LIMIT;
            case "safe" -> SAFE_LIMIT;
            default -> 3;
        };
    }

    private BlockedItem block(Long programId, String schoolName, String reason) {
        BlockedItem item = new BlockedItem();
        item.setProgramId(programId);
        item.setSchoolName(schoolName);
        item.setBlockReason(reason);
        return item;
    }
}
