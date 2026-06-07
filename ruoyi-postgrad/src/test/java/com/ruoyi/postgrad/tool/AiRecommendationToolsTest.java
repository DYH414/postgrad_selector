package com.ruoyi.postgrad.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RowMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AiRecommendationToolsTest {
    @Test
    void getProgramDetailReturnsBudgetLimitWhenDetailCallsExhausted() throws Exception {
        AiRecommendationTools tools = new AiRecommendationTools();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        // Pool with multiple programIds so budget can be exhausted with different pids
        List<RowMap> pool = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            RowMap row = new RowMap();
            row.put("programId", i);
            pool.add(row);
        }
        when(ops.get("ai:agent:pool:c1")).thenReturn(JSON.toJSONString(pool));

        Field redisField = AiRecommendationTools.class.getDeclaredField("redisTemplate");
        redisField.setAccessible(true);
        redisField.set(tools, redis);

        AiRecommendationTools.startReportContext("c1");
        // Call with different pids to exhaust detail budget (reportDefaults maxDetailCalls=12)
        for (int i = 1; i <= 12; i++) {
            tools.getProgramDetail(i);
        }
        // 13th different pid should hit budget limit
        String result = tools.getProgramDetail(13L);
        AiRecommendationTools.clear();

        assertTrue(result.contains("tool_budget_exceeded"),
            "13th getProgramDetail should be blocked, got: " + result);
    }

    @Test
    void searchProgramsReturnsBoundedSummaryWithTotalAndFacets() throws Exception {
        AiRecommendationTools tools = new AiRecommendationTools();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        List<Map<String, Object>> pool = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            RowMap row = new RowMap();
            row.put("programId", (long) i);
            row.put("schoolName", "学校" + i);
            row.put("schoolTier", i % 2 == 0 ? "211" : "普通本科");
            row.put("province", i % 3 == 0 ? "上海" : "北京");
            row.put("city", i % 3 == 0 ? "上海" : "北京");
            row.put("collegeName", "计算机学院");
            row.put("programName", "计算机科学与技术");
            row.put("avgAdmittedScore", 280 + i);
            row.put("gap", 300 - (280 + i));
            row.put("unifiedExamQuota", i);
            row.put("dataCompleteness", "B");
            row.put("sourceUrl", "https://example.com/" + i);
            pool.add(row);
        }
        when(ops.get("ai:agent:pool:c1")).thenReturn(JSON.toJSONString(pool));

        Field redisField = AiRecommendationTools.class.getDeclaredField("redisTemplate");
        redisField.setAccessible(true);
        redisField.set(tools, redis);

        AiRecommendationTools.setConversationId("c1");
        String resultJson = tools.searchPrograms("{\"maxScore\":300}");
        AiRecommendationTools.clear();

        Map<String, Object> result = JSON.parseObject(resultJson, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> facets = (Map<String, Object>) result.get("facets");

        assertEquals(20, ((Number) result.get("total")).intValue());
        assertEquals(12, ((Number) result.get("returned")).intValue());
        assertEquals(12, items.size());
        assertEquals(true, result.get("hasMore"));
        assertTrue(facets.containsKey("provinces"));
        assertFalse(items.get(0).containsKey("sourceUrl"));
        assertTrue(items.get(0).containsKey("programId"));
        assertTrue(items.get(0).containsKey("schoolName"));
        assertTrue(items.get(0).containsKey("quotaRisk"));
    }
}
