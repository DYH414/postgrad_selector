package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiRecommendationServiceImplTest {
    @Test
    void shouldNormalizeReportJudgementAndAction() throws Exception {
        AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("normalizeReportItem", Map.class);
        method.setAccessible(true);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("aiJudgement", "稳妥偏冲刺");
        item.put("verificationStatus", "unknown");

        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) method.invoke(service, item);

        assertEquals("steady_reach", normalized.get("judgement"));
        assertEquals("稳妥偏冲", normalized.get("judgementLabel"));
        assertEquals("pending", normalized.get("verificationStatus"));
        assertEquals("可作为稳妥偏冲候选，建议核验近年复试与录取波动", normalized.get("recommendedAction"));
    }
}
