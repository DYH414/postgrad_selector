package com.ruoyi.postgrad.recommend.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class V2ChatToolsSearchTest {

    @AfterEach
    void clearContext() {
        V2ChatToolContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToDatabaseForKeywordEvenWithoutRegion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RowMap row = new RowMap();
        row.put("programId", 2501L);
        row.put("schoolId", 15L);
        row.put("schoolName", "深圳大学");
        row.put("programName", "计算机科学与技术");
        row.put("schoolTier", "双非");
        row.put("city", "深圳");
        row.put("province", "广东");
        row.put("avgAdmittedScore", 305);
        row.put("unifiedExamQuota", 20);
        row.put("planCount", 25);
        row.put("dataCompleteness", "A");
        row.put("admissionLow", 290);
        row.put("admissionHigh", 320);
        when(mapper.selectCandidates(anyString(), any(), any(), anyInt(), any(), anyString()))
            .thenReturn(List.of(row));

        V2ChatToolContext.init(1L, redis, mapper);
        String result = new V2ChatTools().searchPrograms("深圳大学", null, null);

        assertTrue(result.contains("深圳大学"), result);
        assertTrue(result.contains("2501"), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsNaturalCombinedSchoolAndProgramKeyword() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RowMap row = new RowMap();
        row.put("programId", 3123L);
        row.put("schoolId", 53L);
        row.put("schoolName", "杭州电子科技大学");
        row.put("collegeName", "网络空间安全学院");
        row.put("programName", "电子信息-网安方向");
        row.put("programCode", "085400");
        row.put("schoolTier", "双非");
        row.put("city", "杭州");
        row.put("province", "浙江");
        row.put("avgAdmittedScore", 302);
        row.put("unifiedExamQuota", 58);
        row.put("dataCompleteness", "A");
        row.put("admissionLow", 290);
        row.put("admissionHigh", 320);
        when(mapper.selectCandidates(anyString(), any(), any(), anyInt(), any(), anyString()))
            .thenReturn(List.of(row));

        V2ChatToolContext.init(1L, redis, mapper);
        String result = new V2ChatTools().searchPrograms(
            "杭州电子科技大学 网安方向", null, null);

        assertTrue(result.contains("杭州电子科技大学"), result);
        assertTrue(result.contains("3123"), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void compareProgramsFallsBackToDatabaseOutsideDraftPool() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);

        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RowMap left = comparisonRow("杭州电子科技大学", "杭州", 302, 12);
        RowMap right = comparisonRow("深圳大学", "深圳", 310, 8);
        when(mapper.selectProgramForRecommendation(1001L)).thenReturn(left);
        when(mapper.selectProgramForRecommendation(1002L)).thenReturn(right);

        V2ChatToolContext.init(1L, redis, mapper);
        String result = new V2ChatTools().comparePrograms(1001L, 1002L);

        assertTrue(result.contains("杭州电子科技大学"), result);
        assertTrue(result.contains("深圳大学"), result);
        assertTrue(result.contains("302"), result);
        assertTrue(result.contains("310"), result);
        assertTrue(result.contains("12"), result);
        assertTrue(result.contains("8"), result);
    }

    private RowMap comparisonRow(String schoolName, String city, int avgScore, int quota) {
        RowMap row = new RowMap();
        row.put("schoolName", schoolName);
        row.put("schoolTier", "双非");
        row.put("city", city);
        row.put("avgAdmittedScore", avgScore);
        row.put("unifiedExamQuota", quota);
        row.put("dataCompleteness", "A");
        return row;
    }
}
