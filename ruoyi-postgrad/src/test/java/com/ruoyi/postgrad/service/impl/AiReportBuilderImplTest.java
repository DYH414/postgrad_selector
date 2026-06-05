package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import dev.langchain4j.model.chat.ChatModel;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiReportBuilderImplTest {
    private AiReportBuilderImpl builder;

    @Mock private RecommendationMapper recommendationMapper;
    @Mock private ChatModel chatModel;

    @BeforeEach
    void setUp() throws Exception {
        builder = new AiReportBuilderImpl();
        Field field = AiReportBuilderImpl.class.getDeclaredField("recommendationMapper");
        field.setAccessible(true);
        field.set(builder, recommendationMapper);
    }

    @Test
    void shouldBuildOpinionOnlyPrompt() {
        String prompt = builder.buildConversationPrompt("[]", "[{\"programId\":123,\"avgAdmittedScore\":295}]",
            Map.of("schoolTierPreference", "prefer_211_or_better"));

        assertTrue(prompt.contains("AI 只输出观点字段"));
        assertTrue(prompt.contains("preferenceProfile"));
        assertTrue(prompt.contains("\"programId\""));
        assertTrue(prompt.contains("\"decision\""));
        assertTrue(prompt.contains("\"tradeoffs\""));
        assertTrue(prompt.contains("不要输出 schoolName"));
        assertFalse(prompt.contains("\"schoolName\":\"...\""));
    }

    @Test
    void shouldHydrateAiOpinionWithDatabaseFacts() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
            {"summary":"稳妥优先","tiers":[{"level":"steady","label":"稳妥档","schools":[{"programId":123,"judgement":"steady","risk":"medium","decision":"主力稳妥","reason":"分数和地区匹配","pros":["地区符合"],"cons":["需核验"],"tradeoffs":["稳妥优先"],"recommendedAction":"加入备选"}]}]}
            """);
        when(recommendationMapper.selectProgramsByIds(List.of(123L), 300)).thenReturn(List.of(detailRow(123L)));

        Map<String, Object> report = builder.buildConversationReport(chatModel, "[]", "[{\"programId\":123}]", 300,
            Map.of("riskPreference", "balanced"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) report.get("tiers");
        @SuppressWarnings("unchecked")
        Map<String, Object> school = ((List<Map<String, Object>>) tiers.get(0).get("schools")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> opinion = (Map<String, Object>) school.get("opinion");

        assertEquals("北京信息科技大学", school.get("schoolName"));
        assertEquals("计算机学院", school.get("collegeName"));
        assertEquals(295, school.get("avgAdmittedScore"));
        assertEquals(5, school.get("avgScoreGap"));
        assertEquals("270-331", school.get("admissionRange"));
        assertEquals("主力稳妥", opinion.get("decision"));
    }

    @Test
    void shouldDowngradeTinyQuotaSafeRecommendationToSteady() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
            {"summary":"保底推荐","tiers":[{"level":"reach","label":"冲刺档","schools":[]},{"level":"steady","label":"稳妥档","schools":[]},{"level":"safe","label":"保底档","schools":[{"programId":123,"judgement":"safe","risk":"low","decision":"适合作为保底","reason":"分差较大","pros":["分数优势"],"cons":[],"tradeoffs":[],"recommendedAction":"加入保底"}]}]}
            """);
        RowMap tinyQuota = detailRow(123L);
        tinyQuota.put("unifiedExamQuota", 1);
        tinyQuota.put("planCount", 1);
        tinyQuota.put("admissionLow", null);
        tinyQuota.put("admissionHigh", null);
        when(recommendationMapper.selectProgramsByIds(List.of(123L), 300)).thenReturn(List.of(tinyQuota));

        Map<String, Object> report = builder.buildAnalyzeReport(chatModel, "[{\"programId\":123,\"unifiedExamQuota\":1}]", 300,
            Map.of("riskPreference", "balanced"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) report.get("tiers");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steadySchools = (List<Map<String, Object>>) tiers.get(1).get("schools");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> safeSchools = (List<Map<String, Object>>) tiers.get(2).get("schools");
        assertFalse(steadySchools.isEmpty(), "tiny quota safe recommendation should be downgraded to steady");
        Map<String, Object> school = steadySchools.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> opinion = (Map<String, Object>) school.get("opinion");

        assertTrue(safeSchools.isEmpty());
        assertEquals(false, school.get("canBeSafe"));
        assertEquals("very_high", school.get("quotaRisk"));
        assertEquals("steady", opinion.get("judgement"));
        assertEquals("high", opinion.get("risk"));
        assertTrue(((String) school.get("safeBlockReason")).contains("统考名额仅1人"));
        assertTrue(((List<?>) opinion.get("cons")).contains(school.get("safeBlockReason")));
    }

    private RowMap detailRow(long programId) {
        RowMap row = new RowMap();
        row.put("programId", programId);
        row.put("schoolName", "北京信息科技大学");
        row.put("collegeName", "计算机学院");
        row.put("programName", "计算机科学与技术");
        row.put("province", "北京");
        row.put("city", "北京");
        row.put("schoolTier", "普通本科");
        row.put("examCombo", "11408");
        row.put("scoreLine", 273);
        row.put("avgAdmittedScore", new BigDecimal("295"));
        row.put("admissionLow", 270);
        row.put("admissionHigh", 331);
        row.put("unifiedExamQuota", 28);
        row.put("planCount", 33);
        row.put("dataYear", 2025);
        row.put("dataCompleteness", "C");
        row.put("sourceOwner", "N诺");
        return row;
    }
}
