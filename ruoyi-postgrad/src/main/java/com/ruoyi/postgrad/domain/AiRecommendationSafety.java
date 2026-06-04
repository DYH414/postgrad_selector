package com.ruoyi.postgrad.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared factual guardrails for AI school-selection opinions.
 */
public final class AiRecommendationSafety {

    private AiRecommendationSafety() {
    }

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
