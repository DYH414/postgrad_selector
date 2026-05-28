package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgramRecommendationServiceImplTest
{
    private ProgramRecommendationServiceImpl service;

    @Mock
    private RecommendationMapper recommendationMapper;

    @Mock
    private RecommendationLogMapper logMapper;

    @BeforeEach
    void setUp() throws Exception
    {
        service = new ProgramRecommendationServiceImpl();
        setField("recommendationMapper", recommendationMapper);
        setField("logMapper", logMapper);
        when(logMapper.insertRecommendationLog(any(RecommendationLog.class))).thenAnswer(invocation -> {
            RecommendationLog log = invocation.getArgument(0);
            log.setId(83L);
            return 1;
        });
    }

    @Nested
    class ScoreRangeFlatMode
    {
        @Test
        void shouldMatchByAverageScoreDiveLowerBound()
        {
            when(recommendationMapper.selectCandidates(eq("101,201,301,408"), any(), any(), eq(300), eq(15), eq("full_time")))
                .thenReturn(rows(
                    row(1L, "浙江大学", "电子信息", 315),
                    row(2L, "南京理工大学", "智能科学与技术", 316)
                ));

            Map<String, Object> result = service.generateRecommendation(1L, request(300, 15));

            List<Map<String, Object>> items = firstGroupItems(result);
            assertEquals(1, items.size());
            assertEquals("浙江大学", items.get(0).get("schoolName"));
            assertEquals(-15, items.get(0).get("avgScoreGap"));
        }

        @Test
        void shouldMatchAllAverageScoresNotAboveUpperLimit()
        {
            when(recommendationMapper.selectCandidates(eq("101,201,301,408"), any(), any(), eq(300), eq(15), eq("full_time")))
                .thenReturn(rows(
                    row(1L, "低均分大学", "电子信息", 274),
                    row(2L, "更低均分大学", "电子信息", 250),
                    row(3L, "上限大学", "电子信息", 315),
                    row(4L, "过高均分大学", "电子信息", 316)
                ));

            Map<String, Object> result = service.generateRecommendation(1L, request(300, 15));

            List<Map<String, Object>> items = firstGroupItems(result);
            assertEquals(Arrays.asList("上限大学", "低均分大学", "更低均分大学"),
                items.stream().map(item -> String.valueOf(item.get("schoolName"))).toList());
        }

        @Test
        void shouldNotLimitFlatScreeningResultsToPageSizePerGroup()
        {
            when(recommendationMapper.selectCandidates(eq("101,201,301,408"), any(), any(), eq(300), eq(15), eq("full_time")))
                .thenReturn(numberedRows(13, 280));

            Map<String, Object> result = service.generateRecommendation(1L, request(300, 15));

            assertEquals(13, firstGroupItems(result).size());
        }

        @Test
        void shouldExcludeMissingAverageScoreInScoreRangeMode()
        {
            when(recommendationMapper.selectCandidates(eq("101,201,301,408"), any(), any(), eq(300), eq(15), eq("full_time")))
                .thenReturn(rows(
                    row(1L, "缺均分大学", "电子信息", null),
                    row(2L, "有均分大学", "电子信息", 314)
                ));

            Map<String, Object> result = service.generateRecommendation(1L, request(300, 15));

            List<Map<String, Object>> items = firstGroupItems(result);
            assertEquals(1, items.size());
            assertEquals("有均分大学", items.get(0).get("schoolName"));
        }

        @Test
        void shouldSortScoreRangeByRiskDescending()
        {
            when(recommendationMapper.selectCandidates(eq("101,201,301,408"), any(), any(), eq(300), eq(15), eq("full_time")))
                .thenReturn(rows(
                    row(1L, "稳一点大学", "电子信息", 303),
                    row(2L, "刚好冲刺大学", "电子信息", 314),
                    row(3L, "更稳大学", "电子信息", 290)
                ));

            Map<String, Object> result = service.generateRecommendation(1L, request(300, 15));

            List<Map<String, Object>> items = firstGroupItems(result);
            assertEquals(Arrays.asList("刚好冲刺大学", "稳一点大学", "更稳大学"),
                items.stream().map(item -> String.valueOf(item.get("schoolName"))).toList());
        }
    }

    private Map<String, Object> request(int estimatedScore, Integer scoreRange)
    {
        return Map.of(
            "estimatedScore", estimatedScore,
            "examCombo", "11408",
            "scoreRange", scoreRange,
            "pageSizePerGroup", 12
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> firstGroupItems(Map<String, Object> result)
    {
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals("matches", groups.get(0).get("groupKey"));
        return (List<Map<String, Object>>) groups.get(0).get("items");
    }

    private List<RowMap> rows(RowMap... rows)
    {
        return Arrays.asList(rows);
    }

    private List<RowMap> numberedRows(int count, int avgScore)
    {
        List<RowMap> rows = new ArrayList<>();
        for (long i = 1; i <= count; i++)
        {
            rows.add(row(i, "测试大学" + i, "电子信息", avgScore));
        }
        return rows;
    }

    private RowMap row(Long id, String schoolName, String programName, Integer avgScore)
    {
        RowMap row = new RowMap();
        row.put("programId", id);
        row.put("schoolName", schoolName);
        row.put("province", "浙江");
        row.put("collegeName", "计算机学院");
        row.put("programCode", "085404");
        row.put("programName", programName);
        row.put("degreeType", "professional");
        row.put("subjectCodes", "101,201,301,408");
        row.put("scoreLine", 300);
        row.put("admissionLow", 300);
        row.put("avgAdmittedScore", avgScore == null ? null : BigDecimal.valueOf(avgScore));
        row.put("admissionHigh", avgScore == null ? null : avgScore + 20);
        row.put("admittedCount", 10);
        row.put("planCount", 12);
        row.put("dataCompleteness", "A");
        return row;
    }

    private void setField(String name, Object value) throws Exception
    {
        Field field = ProgramRecommendationServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
