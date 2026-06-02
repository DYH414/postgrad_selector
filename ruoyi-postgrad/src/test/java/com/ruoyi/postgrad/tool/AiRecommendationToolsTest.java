package com.ruoyi.postgrad.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RowMap;
import java.lang.reflect.Field;
import java.util.List;
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

        RowMap row = new RowMap();
        row.put("programId", 1L);
        when(ops.get("ai:agent:pool:c1")).thenReturn(JSON.toJSONString(List.of(row)));

        Field redisField = AiRecommendationTools.class.getDeclaredField("redisTemplate");
        redisField.setAccessible(true);
        redisField.set(tools, redis);

        AiRecommendationTools.startReportContext("c1");
        for (int i = 0; i < 12; i++) {
            tools.getProgramDetail(1L);
        }
        String result = tools.getProgramDetail(1L);
        AiRecommendationTools.clear();

        assertTrue(result.contains("tool_budget_exceeded"));
    }
}
