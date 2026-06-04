package com.ruoyi.postgrad.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiRecommendationSafetyTest {

    @Test
    void blocksTinyQuotaFromSafeEvenWhenScoreGapIsLarge() {
        Map<String, Object> row = row(1, 260, 220, 280, "A", 40);

        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(row, 300);

        assertEquals(false, guard.get("canBeSafe"));
        assertEquals("very_high", guard.get("quotaRisk"));
        assertEquals("统考名额仅1人，录取波动极大，不能作为保底", guard.get("safeBlockReason"));
    }

    @Test
    void usesExistingGapWhenEstimatedScoreIsUnavailable() {
        Map<String, Object> row = row(5, 260, 220, 280, "A", 40);

        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(row, 0);

        assertEquals(true, guard.get("canBeSafe"));
        assertEquals("high", guard.get("quotaRisk"));
    }

    private Map<String, Object> row(int quota, int avg, int low, int high, String completeness, int gap) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("planCount", quota);
        row.put("avgAdmittedScore", avg);
        row.put("admissionLow", low);
        row.put("admissionHigh", high);
        row.put("dataCompleteness", completeness);
        row.put("gap", gap);
        return row;
    }
}
