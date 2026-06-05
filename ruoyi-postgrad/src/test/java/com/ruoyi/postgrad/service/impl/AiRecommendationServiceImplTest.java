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
        item.put("aiJudgement", "\u7a33\u59a5\u504f\u51b2\u523a");
        item.put("verificationStatus", "unknown");

        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) method.invoke(service, item);

        assertEquals("steady_reach", normalized.get("judgement"));
        assertEquals("\u7a33\u59a5\u504f\u51b2", normalized.get("judgementLabel"));
        assertEquals("pending", normalized.get("verificationStatus"));
        assertEquals("\u53ef\u4f5c\u4e3a\u7a33\u59a5\u504f\u51b2\u5019\u9009\uff0c\u5efa\u8bae\u6838\u9a8c\u8fd1\u5e74\u590d\u8bd5\u4e0e\u5f55\u53d6\u6ce2\u52a8", normalized.get("recommendedAction"));
    }

    @Test
    void shouldLabelProfilePreferencesForPrompt() throws Exception {
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("preferenceLabel", String.class, Object.class);
        method.setAccessible(true);

        assertEquals("\u7a33\u4e2d\u6c42\u8fdb\uff0c\u51b2\u7a33\u4fdd\u5747\u8861",
            method.invoke(null, "riskPreference", "balanced"));
        assertEquals("\u4e0d\u5f3a\u6c42\u5c42\u6b21\uff0c\u6709\u5b66\u4e0a\u66f4\u91cd\u8981",
            method.invoke(null, "schoolTierPreference", "no_strict_requirement"));
        assertEquals("\u53d1\u8fbe\u5730\u533a\u4f18\u5148\uff0c\u4f46\u8981\u517c\u987e\u7a33\u59a5",
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

        assertEquals("\u6309\u6211\u7684\u753b\u50cf\u5f00\u59cb\u7b5b\u9009", options.get(0));
        assertFalse(options.contains("\u770b\u91cd\u4e0a\u5cb8\u7387"));
        assertFalse(options.contains("\u5b66\u6821\u5c42\u6b21\u4f18\u5148"));
    }

    @Test
    void shouldHydrateChatCardsFromMessyAiTextAndCandidatePool() throws Exception {
        AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("hydrateChatCards", String.class, String.class);
        method.setAccessible(true);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("programId", 7L);
        row.put("schoolName", "\u5317\u4eac\u4fe1\u606f\u79d1\u6280\u5927\u5b66");
        row.put("programName", "\u8ba1\u7b97\u673a\u79d1\u5b66\u4e0e\u6280\u672f");
        row.put("collegeName", "\u8ba1\u7b97\u673a\u5b66\u9662");
        row.put("schoolTier", "OTHER");
        row.put("city", "\u5317\u4eac");
        row.put("avgAdmittedScore", 295);
        row.put("gap", 5);
        row.put("planCount", 33);
        row.put("canBeSafe", true);

        String message = "\u7a33\u59a5\u6863\u91cc\u53ef\u4ee5\u91cd\u70b9\u770b\uff1a------\u5317\u4eac\u4fe1\u606f\u79d1\u6280\u5927\u5b66-\u8ba1\u7b97\u673a\u79d1\u5b66\u4e0e\u6280\u672f 295 +5 \u5317\u4eac 33\u4eba\uff0c\u5206\u6570\u6709\u4f59\u91cf\u3002";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) method.invoke(
            service, message, com.alibaba.fastjson2.JSON.toJSONString(List.of(row)));

        assertEquals(1, cards.size());
        assertEquals("\u5317\u4eac\u4fe1\u606f\u79d1\u6280\u5927\u5b66", cards.get(0).get("school"));
        assertEquals("\u8ba1\u7b97\u673a\u79d1\u5b66\u4e0e\u6280\u672f", cards.get(0).get("program"));
        assertEquals("", cards.get(0).get("level"));
    }

    @Test
    void shouldHydrateChatCardsWhenAiUsesFullWidthSchoolParentheses() throws Exception {
        AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("hydrateChatCards", String.class, String.class);
        method.setAccessible(true);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("programId", 101L);
        row.put("schoolName", "\u4e2d\u56fd\u5730\u8d28\u5927\u5b66(\u6b66\u6c49)");
        row.put("programName", "\u7535\u5b50\u4fe1\u606f");
        row.put("avgAdmittedScore", 309);
        row.put("gap", -9);
        row.put("planCount", 26);

        String message = "\u51b2\u523a\u6863\u53ef\u4ee5\u770b\uff1a\u4e2d\u56fd\u5730\u8d28\u5927\u5b66\uff08\u6b66\u6c49\uff09 \u00b7 \u7535\u5b50\u4fe1\u606f\uff0c\u5747\u5206309\u3002";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) method.invoke(
            service, message, com.alibaba.fastjson2.JSON.toJSONString(List.of(row)));

        assertEquals(1, cards.size());
        assertEquals("\u4e2d\u56fd\u5730\u8d28\u5927\u5b66(\u6b66\u6c49)", cards.get(0).get("school"));
    }

    @Test
    void shouldHydrateChatCardsWhenAiUsesProgramShortName() throws Exception {
        AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("hydrateChatCards", String.class, String.class);
        method.setAccessible(true);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("programId", 823L);
        row.put("schoolName", "\u5317\u4eac\u4fe1\u606f\u79d1\u6280\u5927\u5b66");
        row.put("programName", "\u8ba1\u7b97\u673a\u79d1\u5b66\u4e0e\u6280\u672f");
        row.put("avgAdmittedScore", 295);
        row.put("gap", 5);
        row.put("planCount", 33);

        String message = "\u5317\u4eac\u4fe1\u606f\u79d1\u6280\u5927\u5b66 vs \u676d\u5dde\u5e08\u8303\u5927\u5b66\uff0c\u8868\u683c\u4e2d\u5199\u4f5c\u5317\u4fe1\u79d1\u00b7\u8ba1\u7b97\u673a\uff0c\u4f46\u6ca1\u6709\u5199\u5168\u4e13\u4e1a\u540d\u3002";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) method.invoke(
            service, message, com.alibaba.fastjson2.JSON.toJSONString(List.of(row)));

        assertEquals(1, cards.size());
        assertEquals("\u5317\u4eac\u4fe1\u606f\u79d1\u6280\u5927\u5b66", cards.get(0).get("school"));
    }

    @Test
    void shouldNotHydrateEveryProgramFromSameSchoolWhenOnlyOneFactSetIsMentioned() throws Exception {
        AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("hydrateChatCards", String.class, String.class);
        method.setAccessible(true);

        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("programId", 1731L);
        ai.put("schoolName", "\u676d\u5dde\u5e08\u8303\u5927\u5b66");
        ai.put("programName", "\u4eba\u5de5\u667a\u80fd");
        ai.put("avgAdmittedScore", 278);
        ai.put("gap", 22);
        ai.put("planCount", 22);

        Map<String, Object> software = new LinkedHashMap<>();
        software.put("programId", 2457L);
        software.put("schoolName", "\u676d\u5dde\u5e08\u8303\u5927\u5b66");
        software.put("programName", "\u7535\u5b50\u4fe1\u606f-\u8f6f\u4ef6\u65b9\u5411");
        software.put("avgAdmittedScore", 309);
        software.put("gap", -9);
        software.put("planCount", 71);

        String message = "\u7a33\u59a5\u533a\u676d\u5dde\u5e08\u8303\u5927\u5b66\uff08+22\u5206/22\u4eba\uff09\u4e5f\u503c\u5f97\u5173\u6ce8\u3002";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) method.invoke(
            service, message, com.alibaba.fastjson2.JSON.toJSONString(List.of(ai, software)));

        assertEquals(1, cards.size());
        assertEquals("1731", String.valueOf(cards.get(0).get("programId")));
    }

    @Test
    void shouldNotMatchBroadElectronicInformationAliasForDifferentDirection() throws Exception {
        AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("hydrateChatCards", String.class, String.class);
        method.setAccessible(true);

        Map<String, Object> computer = new LinkedHashMap<>();
        computer.put("programId", 1L);
        computer.put("schoolName", "\u4e2d\u56fd\u77f3\u6cb9\u5927\u5b66");
        computer.put("programName", "\u7535\u5b50\u4fe1\u606f-\u8ba1\u7b97\u673a");
        computer.put("avgAdmittedScore", 295);
        computer.put("gap", 5);
        computer.put("planCount", 16);

        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("programId", 2L);
        ai.put("schoolName", "\u4e2d\u56fd\u77f3\u6cb9\u5927\u5b66");
        ai.put("programName", "\u7535\u5b50\u4fe1\u606f-\u4eba\u5de5\u667a\u80fd");
        ai.put("avgAdmittedScore", 308);
        ai.put("gap", -8);
        ai.put("planCount", 22);

        String message = "\u4e2d\u56fd\u77f3\u6cb9\u5927\u5b66\u9752\u5c9b\uff08\u7535\u5b50\u4fe1\u606f-\u8ba1\u7b97\u673a\uff0c211\uff09\uff0c\u5747\u5206295\uff0c\u4f60\u9ad8\u51fa5\u5206\uff0c\u62db\u751f16\u4eba\u3002";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) method.invoke(
            service, message, com.alibaba.fastjson2.JSON.toJSONString(List.of(computer, ai)));

        assertEquals(1, cards.size());
        assertEquals("1", String.valueOf(cards.get(0).get("programId")));
    }
}
