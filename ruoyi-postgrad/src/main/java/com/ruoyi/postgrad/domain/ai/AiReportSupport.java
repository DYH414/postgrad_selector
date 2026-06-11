package com.ruoyi.postgrad.domain.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class AiReportSupport {
    public static final String JUDGEMENT_SAFE = "safe";
    public static final String JUDGEMENT_STEADY = "steady";
    public static final String JUDGEMENT_STEADY_REACH = "steady_reach";
    public static final String JUDGEMENT_SMALL_REACH = "small_reach";
    public static final String JUDGEMENT_HIGH_RISK_REACH = "high_risk_reach";
    public static final String JUDGEMENT_DATA_INSUFFICIENT_PENDING = "data_insufficient_pending";

    public static final String STATUS_OFFICIAL = "official";
    public static final String STATUS_THIRD_PARTY = "third_party";
    public static final String STATUS_LOCAL_DATA_ONLY = "local_data_only";
    public static final String STATUS_VERIFICATION_FAILED = "verification_failed";
    public static final String STATUS_PENDING = "pending";

    private static final List<String> JUDGEMENT_ORDER = List.of(
        JUDGEMENT_STEADY,
        JUDGEMENT_STEADY_REACH,
        JUDGEMENT_SAFE,
        JUDGEMENT_SMALL_REACH,
        JUDGEMENT_HIGH_RISK_REACH,
        JUDGEMENT_DATA_INSUFFICIENT_PENDING
    );

    private AiReportSupport() {}

    public static String normalizeJudgement(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (JUDGEMENT_ORDER.contains(text)) return text;
        if (text.contains("保底")) return JUDGEMENT_SAFE;
        if (text.contains("稳妥偏冲") || text.contains("稳中偏冲")) return JUDGEMENT_STEADY_REACH;
        if (text.contains("稳妥")) return JUDGEMENT_STEADY;
        if (text.contains("小冲")) return JUDGEMENT_SMALL_REACH;
        if (text.contains("高风险") || text.contains("冲刺")) return JUDGEMENT_HIGH_RISK_REACH;
        return JUDGEMENT_DATA_INSUFFICIENT_PENDING;
    }

    public static String judgementLabel(String judgement) {
        return switch (normalizeJudgement(judgement)) {
            case JUDGEMENT_SAFE -> "保底";
            case JUDGEMENT_STEADY -> "稳妥";
            case JUDGEMENT_STEADY_REACH -> "稳妥偏冲";
            case JUDGEMENT_SMALL_REACH -> "小冲";
            case JUDGEMENT_HIGH_RISK_REACH -> "高风险冲刺";
            default -> "数据不足待核验";
        };
    }

    public static String normalizeVerificationStatus(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (STATUS_OFFICIAL.equals(text)) return STATUS_OFFICIAL;
        if (STATUS_THIRD_PARTY.equals(text) || "third_party_only".equals(text)) return STATUS_THIRD_PARTY;
        if (STATUS_LOCAL_DATA_ONLY.equals(text)) return STATUS_LOCAL_DATA_ONLY;
        if (STATUS_VERIFICATION_FAILED.equals(text)) return STATUS_VERIFICATION_FAILED;
        if (STATUS_PENDING.equals(text)) return STATUS_PENDING;
        return STATUS_PENDING;
    }

    public static String recommendedAction(String judgement, String verificationStatus) {
        String j = normalizeJudgement(judgement);
        String s = normalizeVerificationStatus(verificationStatus);
        if (JUDGEMENT_DATA_INSUFFICIENT_PENDING.equals(j)) {
            return "数据不足，先放入待核验池，不作为主推荐";
        }
        if (JUDGEMENT_HIGH_RISK_REACH.equals(j)) {
            return "风险较高，仅建议在用户明确愿意冲刺时保留";
        }
        if (JUDGEMENT_SMALL_REACH.equals(j)) {
            return "可作为小冲目标，建议同时准备更稳妥备选";
        }
        if (JUDGEMENT_SAFE.equals(j)) {
            return "可作为保底备选，建议复查当年招生计划后加入最终名单";
        }
        if (JUDGEMENT_STEADY_REACH.equals(j)) {
            return "可作为稳妥偏冲候选，建议核验近年复试与录取波动";
        }
        if (STATUS_OFFICIAL.equals(s)) {
            return "可作为稳妥候选，建议复查当年招生计划后加入最终名单";
        }
        return "可作为稳妥候选，建议优先核验官网招生计划";
    }

    public static Comparator<Map<String, Object>> directionComparator() {
        return Comparator
            .comparingInt((Map<String, Object> row) -> judgementRank(row.get("judgement")))
            .thenComparingInt(row -> completenessRank(row.get("dataCompleteness")))
            .thenComparingInt(row -> Math.abs(intVal(row.get("avgScoreGap"), 999)))
            .thenComparing((Map<String, Object> row) -> -intVal(row.get("dataYear"), 0))
            .thenComparing(row -> String.valueOf(row.getOrDefault("collegeName", ""))
                + String.valueOf(row.getOrDefault("programName", "")));
    }

    private static int judgementRank(Object value) {
        int idx = JUDGEMENT_ORDER.indexOf(normalizeJudgement(value));
        return idx < 0 ? JUDGEMENT_ORDER.size() : idx;
    }

    private static int completenessRank(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if ("A".equalsIgnoreCase(text)) return 0;
        if ("B".equalsIgnoreCase(text)) return 1;
        if ("C".equalsIgnoreCase(text)) return 2;
        return 3;
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
