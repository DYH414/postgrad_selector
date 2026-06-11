package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.dto.ProgramSummaryDTO;
import com.ruoyi.postgrad.domain.vo.RecommendResultVO;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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

            RecommendResultVO result = service.generateRecommendation(1L, request(300, 15));

            List<ProgramSummaryDTO> items = firstGroupItems(result);
            assertEquals(1, items.size());
            assertEquals("浙江大学", items.get(0).getSchoolName());
            assertEquals(-15, items.get(0).getAvgScoreGap());
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

            RecommendResultVO result = service.generateRecommendation(1L, request(300, 15));

            List<ProgramSummaryDTO> items = firstGroupItems(result);
            assertEquals(Arrays.asList("上限大学", "低均分大学", "更低均分大学"),
                items.stream().map(ProgramSummaryDTO::getSchoolName).toList());
        }

        @Test
        void shouldNotLimitFlatScreeningResultsToPageSizePerGroup()
        {
            when(recommendationMapper.selectCandidates(eq("101,201,301,408"), any(), any(), eq(300), eq(15), eq("full_time")))
                .thenReturn(numberedRows(13, 280));

            RecommendResultVO result = service.generateRecommendation(1L, request(300, 15));

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

            RecommendResultVO result = service.generateRecommendation(1L, request(300, 15));

            List<ProgramSummaryDTO> items = firstGroupItems(result);
            assertEquals(1, items.size());
            assertEquals("有均分大学", items.get(0).getSchoolName());
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

            RecommendResultVO result = service.generateRecommendation(1L, request(300, 15));

            List<ProgramSummaryDTO> items = firstGroupItems(result);
            assertEquals(3, items.size());
            // 升序距离：距离 1 (gap=-14) > 距离 12 (gap=-3) > 距离 25 (gap=10)
            assertEquals("刚好冲刺大学", items.get(0).getSchoolName());
            assertEquals("稳一点大学", items.get(1).getSchoolName());
            assertEquals("更稳大学", items.get(2).getSchoolName());
        }

        @Nested
        class WithoutScoreRange
        {
            @Test
            void shouldReturnAllCandidatesSortedByScoreGap()
            {
                when(recommendationMapper.selectCandidates(eq("101,201,301,408"), any(), any(), eq(300), eq(null), eq("full_time")))
                    .thenReturn(rows(
                        row(1L, "保底大学", "电子信息", 270),
                        row(2L, "冲刺大学", "电子信息", 305),
                        row(3L, "稳妥大学", "电子信息", 290)
                    ));

                Map<String, Object> plainRequest = new java.util.LinkedHashMap<>();
                plainRequest.put("estimatedScore", 300);
                plainRequest.put("examCombo", "11408");
                plainRequest.put("pageSizePerGroup", 12);

                RecommendResultVO result = service.generateRecommendation(1L, plainRequest);

                List<ProgramSummaryDTO> items = firstGroupItems(result);
				assertEquals(3, items.size());
				// gap=-5 → fitLevel=steady (不低于 -5 不算冲刺), gap=10 → steady, gap=30 → safe
				assertEquals("steady", items.get(0).getFitLevel());
				assertEquals("稳妥候选", items.get(0).getFitLevelLabel());
            }
        }
    }

    private Map<String, Object> request(int estimatedScore, Integer scoreRange)
    {
        Map<String, Object> req = new java.util.LinkedHashMap<>();
        req.put("estimatedScore", estimatedScore);
        req.put("examCombo", "11408");
        req.put("scoreRange", scoreRange);
        req.put("pageSizePerGroup", 12);
        return req;
    }

    private List<ProgramSummaryDTO> firstGroupItems(RecommendResultVO result)
    {
        List<RecommendResultVO.ResultGroup> groups = result.getGroups();
        assertEquals("matches", groups.get(0).getGroupKey());
        return groups.get(0).getItems();
    }

    private List<RowMap> rows(RowMap... rows)
    {
        return Arrays.asList(rows);
    }

    private List<RowMap> numberedRows(int count, int avgScore)
    {
        List<RowMap> rows = new ArrayList<>();
        for (long i = 1; i <= count; i++)
            rows.add(row(i, "测试大学" + i, "电子信息", avgScore));
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
