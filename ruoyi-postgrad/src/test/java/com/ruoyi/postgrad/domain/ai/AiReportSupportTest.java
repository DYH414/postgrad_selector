package com.ruoyi.postgrad.domain.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiReportSupportTest {
    @Test
    void mapsJudgementTextToLegalEnum() {
        assertEquals("steady_reach", AiReportSupport.normalizeJudgement("稳妥偏冲刺"));
        assertEquals("small_reach", AiReportSupport.normalizeJudgement("小冲一下"));
        assertEquals("data_insufficient_pending", AiReportSupport.normalizeJudgement("无法判断"));
    }

    @Test
    void mapsVerificationStatusToLegalEnum() {
        assertEquals("official", AiReportSupport.normalizeVerificationStatus("official"));
        assertEquals("third_party", AiReportSupport.normalizeVerificationStatus("third_party_only"));
        assertEquals("pending", AiReportSupport.normalizeVerificationStatus("unknown-source"));
    }

    @Test
    void recommendedActionUsesTemplate() {
        assertEquals("可作为稳妥候选，建议优先核验官网招生计划",
            AiReportSupport.recommendedAction("steady", "local_data_only"));
        assertEquals("数据不足，先放入待核验池，不作为主推荐",
            AiReportSupport.recommendedAction("data_insufficient_pending", "official"));
    }

    @Test
    void directionComparatorPrefersSteadyThenCompletenessThenGap() {
        Map<String, Object> steadyB = row("steady", "B", -8, 2024, "软件学院", "软件工程");
        Map<String, Object> safeA = row("safe", "A", 30, 2025, "计算机学院", "计算机技术");
        Map<String, Object> steadyA = row("steady", "A", 12, 2023, "人工智能学院", "人工智能");

        assertTrue(AiReportSupport.directionComparator().compare(steadyA, steadyB) < 0);
        assertTrue(AiReportSupport.directionComparator().compare(steadyB, safeA) < 0);
    }

    private Map<String, Object> row(String judgement, String completeness, int gap, int year,
        String college, String program) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("judgement", judgement);
        row.put("dataCompleteness", completeness);
        row.put("avgScoreGap", gap);
        row.put("dataYear", year);
        row.put("collegeName", college);
        row.put("programName", program);
        return row;
    }
}
