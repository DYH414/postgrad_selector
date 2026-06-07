package com.ruoyi.postgrad.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared factual guardrails for AI school-selection opinions.
 * <p>
 * Two responsibilities:
 * <ol>
 *   <li>{@link #safeEligibility(Map, int)} — compute canBeSafe / quotaRisk from raw data</li>
 *   <li>{@link #finalJudgement(Map, String)} — the single backend gate that decides the final
 *       reach/steady/safe tier, regardless of what AI thinks</li>
 * </ol>
 */
public final class AiRecommendationSafety {

    private AiRecommendationSafety() {
    }

    // ── safeEligibility (unchanged) ──

    public static Map<String, Object> safeEligibility(Map<String, Object> row, int estimatedScore) {
        Map<String, Object> guard = new LinkedHashMap<>();
        Integer quota = integerValue(row.getOrDefault("unifiedExamQuota", row.get("planCount")));
        Integer avg = integerValue(row.get("avgAdmittedScore"));
        Integer avgGap = avg == null || estimatedScore <= 0 ? integerValue(row.get("gap")) : estimatedScore - avg;
        boolean hasAdmissionRange = integerValue(row.get("admissionLow")) != null
            || integerValue(row.get("admissionHigh")) != null;
        String completeness = String.valueOf(row.getOrDefault("dataCompleteness", ""));

        guard.put("quotaRisk", quotaRisk(quota));
        guard.put("canBeSafe", true);
        if (quota != null && quota <= 3) {
            guard.put("canBeSafe", false);
            guard.put("safeBlockReason", "统考名额仅" + quota + "人，录取波动极大，不能作为保底");
        } else if (quota != null && quota < 10
            && (avgGap == null || avgGap < 35 || "C".equalsIgnoreCase(completeness) || !hasAdmissionRange)) {
            guard.put("canBeSafe", false);
            guard.put("safeBlockReason", "统考名额仅" + quota + "人，且数据或分数优势不足以支撑保底判断");
        } else if (quota == null && "C".equalsIgnoreCase(completeness) && !hasAdmissionRange) {
            guard.put("canBeSafe", false);
            guard.put("safeBlockReason", "缺少统考名额和拟录取区间，数据完整度较低，不能作为保底");
        }
        return guard;
    }

    // ── finalJudgement：后端统一裁决闸门 ──

    /**
     * 裁决结果：后端计算最终档位，不信任 AI 的 judgement。
     */
    public static JudgementResult finalJudgement(Map<String, Object> fact, String aiJudgement) {
        // 1. 从 fact 提取结构化数据
        int gap = toInt(fact.get("gap"), 0);
        int quota = toInt(fact.get("unifiedExamQuota"), toInt(fact.get("planCount"), 0));
        String completeness = String.valueOf(fact.getOrDefault("dataCompleteness", ""));
        Object canBeSafeObj = fact.get("canBeSafe");
        // canBeSafe 可能尚未计算，现场算
        boolean canBeSafe;
        if (canBeSafeObj instanceof Boolean b) {
            canBeSafe = b;
        } else {
            Map<String, Object> guard = safeEligibility(fact, 0);
            canBeSafe = Boolean.TRUE.equals(guard.get("canBeSafe"));
        }

        // 2. 计算后端乐观上限 backendMaxJudgement
        //    canBeSafe=false 只禁止 safe，不禁 steady。
        //    gap<0 → 最高 reach。
        String backendMax;
        if (gap < 0) {
            backendMax = "reach";
        } else if (gap >= 15 && canBeSafe && quota >= 10 && !"C".equalsIgnoreCase(completeness)) {
            backendMax = "safe";
        } else if (gap >= 5) {
            backendMax = "steady";
        } else if (gap >= 0 && canBeSafe) {
            backendMax = "steady";
        } else {
            backendMax = "reach";
        }

        // 3. final = min(ai, backendMax) — 后端只限制过度乐观，AI 更保守时尊重 AI
        String finalJudgement;
        if (aiJudgement == null || aiJudgement.isBlank()) {
            finalJudgement = backendMax;
        } else {
            finalJudgement = moreConservative(aiJudgement, backendMax);
        }

        // 4. 只有后端把 AI 降级时才标记 adjusted
        boolean adjusted = false;
        String adjustReason = null;
        if (aiJudgement != null && !aiJudgement.isBlank()
            && rank(aiJudgement) > rank(finalJudgement)) {
            adjusted = true;
            if ("safe".equals(aiJudgement) && !canBeSafe) {
                adjustReason = "该校不满足保底条件（canBeSafe=false），系统从保底调整为" + tierLabel(finalJudgement);
            } else if ("safe".equals(aiJudgement) && gap < 0) {
                adjustReason = "录取均分高于用户估分（gap=" + gap + "），不能作为保底，调整为" + tierLabel(finalJudgement);
            } else if ("safe".equals(aiJudgement)) {
                adjustReason = "不满足严格保底的全部条件，调整为" + tierLabel(finalJudgement);
            } else if ("steady".equals(aiJudgement) && "reach".equals(finalJudgement)) {
                adjustReason = "gap=" + gap + "，分数偏低，不能作为稳妥，调整为冲刺";
            } else {
                adjustReason = "系统根据事实数据调整为" + tierLabel(finalJudgement);
            }
        }

        return new JudgementResult(finalJudgement, adjusted, adjustReason);
    }

    /** 裁决结果：最终档位 + 是否被降级 + 原因 */
    public record JudgementResult(String finalJudgement, boolean adjusted, String adjustReason) {}

    /** 档位排序：reach=0 < steady=1 < safe=2 */
    private static int rank(String tier) {
        return switch (tier) {
            case "reach" -> 0;
            case "steady" -> 1;
            case "safe" -> 2;
            default -> 1;
        };
    }

    /** 取两方中更保守的档位（数值更小） */
    private static String moreConservative(String ai, String backendMax) {
        return rank(ai) <= rank(backendMax) ? ai : backendMax;
    }

    private static String tierLabel(String tier) {
        return switch (tier) {
            case "safe" -> "保底";
            case "steady" -> "稳妥";
            case "reach" -> "冲刺";
            default -> tier;
        };
    }

    private static int toInt(Object val, int fallback) {
        if (val instanceof Number n) return n.intValue();
        if (val == null) return fallback;
        try { return Integer.parseInt(String.valueOf(val)); }
        catch (NumberFormatException e) { return fallback; }
    }

    // ── public utilities ──

    public static String quotaRisk(Integer quota) {
        if (quota == null) return "unknown";
        if (quota <= 3) return "very_high";
        if (quota < 10) return "high";
        if (quota < 20) return "medium";
        return "normal";
    }

    public static Integer integerValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
