package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void shouldLabelProfilePreferencesForPrompt() throws Exception {
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("preferenceLabel", String.class, Object.class);
        method.setAccessible(true);

        assertEquals("稳中求进，冲稳保均衡",
            method.invoke(null, "riskPreference", "balanced"));
        assertEquals("不强求层次，有学上更重要",
            method.invoke(null, "schoolTierPreference", "no_strict_requirement"));
        assertEquals("发达地区优先，但要兼顾稳妥",
            method.invoke(null, "regionStrategy", "developed_balanced"));
    }

    @Test
    void initialOptionsShouldUseExistingProfileInsteadOfAskingAgain() throws Exception {
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("initialPreferenceOptions", Map.class);
        method.setAccessible(true);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("schoolTierPreference", "no_strict_requirement");
        profile.put("regionStrategy", "developed_balanced");

        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) method.invoke(null, profile);

        assertEquals("按我的画像开始筛选", options.get(0));
        assertFalse(options.contains("看重上岸率"));
        assertFalse(options.contains("学校层次优先"));
    }

    @Test
    void shouldHydrateChatCardsFromMessyAiTextAndCandidatePool() throws Exception {
        AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("hydrateChatCards", String.class, String.class);
        method.setAccessible(true);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("programId", 7L);
        row.put("schoolName", "北京信息科技大学");
        row.put("programName", "计算机科学与技术");
        row.put("collegeName", "计算机学院");
        row.put("schoolTier", "OTHER");
        row.put("city", "北京");
        row.put("avgAdmittedScore", 295);
        row.put("gap", 5);
        row.put("planCount", 33);
        row.put("canBeSafe", true);

        String message = "稳妥层里可以重点看：------北京信息科技大学-计算机科学与技术 295 +5 北京 33人，分数有余量。";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) method.invoke(
            service, message, com.alibaba.fastjson2.JSON.toJSONString(List.of(row)));

        assertEquals(1, cards.size());
        assertEquals("北京信息科技大学", cards.get(0).get("school"));
        assertEquals("计算机科学与技术", cards.get(0).get("program"));
        assertEquals("稳妥", cards.get(0).get("level"));
    }
}
