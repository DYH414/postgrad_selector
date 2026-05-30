package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCandidatePoolServiceImplTest
{
    // Matches the full subject-code strings the service now uses
    private static final String SUBJ_22408 = "101,204,302,408";
    private static final String SUBJ_11408 = "101,201,301,408";

    private AiCandidatePoolServiceImpl service;

    @Mock
    private RecommendationMapper recommendationMapper;

    @BeforeEach
    void setUp() throws Exception
    {
        service = new AiCandidatePoolServiceImpl();
        Field field = AiCandidatePoolServiceImpl.class.getDeclaredField("recommendationMapper");
        field.setAccessible(true);
        field.set(service, recommendationMapper);
    }

    @Test
    void buildPoolUsesExplicitCandidateIdsWhenProvided()
    {
        List<RowMap> expected = rows(2);
        when(recommendationMapper.selectProgramsByIds(Arrays.asList(12L, 34L), 315)).thenReturn(expected);

        List<RowMap> pool = service.buildPool(
            Map.of("candidateIds", Arrays.asList("12", -1, "abc", 34L, 0)),
            Map.of("targetRegions", "福建"),
            315);

        assertEquals(expected, pool);
        verify(recommendationMapper).selectProgramsByIds(Arrays.asList(12L, 34L), 315);
        verify(recommendationMapper, never()).selectCandidates(eq(SUBJ_22408), eq(Arrays.asList("福建")), eq(null), eq(315), eq(30), eq("full_time"));
        verify(recommendationMapper, never()).selectCandidates(eq(SUBJ_11408), eq(Arrays.asList("福建")), eq(null), eq(315), eq(30), eq("full_time"));
    }

    @Test
    void buildPoolUsesProfileRegionsWhenCandidateIdsAreMissing()
    {
        List<RowMap> expected22408 = rows(2);
        when(recommendationMapper.selectCandidates(SUBJ_22408, Arrays.asList("福建", "广东"), null, 330, 30, "full_time"))
            .thenReturn(expected22408);
        when(recommendationMapper.selectCandidates(SUBJ_11408, Arrays.asList("福建", "广东"), null, 330, 30, "full_time"))
            .thenReturn(Collections.emptyList());

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", "[\"福建\",\"广东\"]"),
            330);

        assertEquals(expected22408, pool);
        verify(recommendationMapper).selectCandidates(SUBJ_22408, Arrays.asList("福建", "广东"), null, 330, 30, "full_time");
        verify(recommendationMapper).selectCandidates(SUBJ_11408, Arrays.asList("福建", "广东"), null, 330, 30, "full_time");
        verify(recommendationMapper, never()).selectProgramsByIds(eq(List.of()), eq(330));
    }

    @Test
    void buildPoolUsesProfileRegionsWhenTargetRegionsAreJavaList()
    {
        List<String> regions = Arrays.asList("福建", "广东");
        List<RowMap> expected = rows(2);
        when(recommendationMapper.selectCandidates(SUBJ_22408, regions, null, 330, 30, "full_time"))
            .thenReturn(expected);
        when(recommendationMapper.selectCandidates(SUBJ_11408, regions, null, 330, 30, "full_time"))
            .thenReturn(Collections.emptyList());

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", regions),
            330);

        assertEquals(expected, pool);
        verify(recommendationMapper).selectCandidates(SUBJ_22408, regions, null, 330, 30, "full_time");
        verify(recommendationMapper).selectCandidates(SUBJ_11408, regions, null, 330, 30, "full_time");
    }

    @Test
    void buildPoolRejectsFractionalCandidateIdsAndFallsBackToProfile()
    {
        // Both exam combos return empty for regions → falls back to all-regions
        when(recommendationMapper.selectCandidates(SUBJ_22408, Collections.emptyList(), null, 330, 30, "full_time"))
            .thenReturn(Collections.emptyList());
        when(recommendationMapper.selectCandidates(SUBJ_11408, Collections.emptyList(), null, 330, 30, "full_time"))
            .thenReturn(rows(1));

        List<RowMap> pool = service.buildPool(
            Map.of("candidateIds", List.of(1.9D)),
            Map.of("targetRegions", "不限"),
            330);

        assertEquals(1, pool.size());
        verify(recommendationMapper, never()).selectProgramsByIds(eq(List.of(1L)), eq(330));
    }

    @Test
    void buildPoolFallsBackToAllRegionsWhenProfileRegionsReturnNoRows()
    {
        // Regional queries return empty for both combos
        when(recommendationMapper.selectCandidates(SUBJ_22408, Arrays.asList("福建", "广东"), null, 300, 30, "full_time"))
            .thenReturn(Collections.emptyList());
        when(recommendationMapper.selectCandidates(SUBJ_11408, Arrays.asList("福建", "广东"), null, 300, 30, "full_time"))
            .thenReturn(Collections.emptyList());
        // Fallback to all regions
        List<RowMap> expected = rows(1);
        when(recommendationMapper.selectCandidates(SUBJ_22408, Collections.emptyList(), null, 300, 30, "full_time"))
            .thenReturn(expected);
        when(recommendationMapper.selectCandidates(SUBJ_11408, Collections.emptyList(), null, 300, 30, "full_time"))
            .thenReturn(Collections.emptyList());

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", "福建，广东"),
            300);

        assertEquals(expected, pool);
        verify(recommendationMapper).selectCandidates(SUBJ_22408, Arrays.asList("福建", "广东"), null, 300, 30, "full_time");
        verify(recommendationMapper).selectCandidates(SUBJ_11408, Arrays.asList("福建", "广东"), null, 300, 30, "full_time");
        verify(recommendationMapper).selectCandidates(SUBJ_22408, Collections.emptyList(), null, 300, 30, "full_time");
        verify(recommendationMapper).selectCandidates(SUBJ_11408, Collections.emptyList(), null, 300, 30, "full_time");
    }

    @Test
    void buildPoolCapsDefaultPoolAtFiftyRows()
    {
        // 22408 returns IDs 1..30, 11408 returns IDs 31..60 → merged=60, capped at 50
        when(recommendationMapper.selectCandidates(SUBJ_22408, Collections.emptyList(), null, 350, 30, "full_time"))
            .thenReturn(rows(1, 30));
        when(recommendationMapper.selectCandidates(SUBJ_11408, Collections.emptyList(), null, 350, 30, "full_time"))
            .thenReturn(rows(31, 60));

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", "不限"),
            350);

        assertEquals(50, pool.size());
    }

    @Test
    void buildPoolDeduplicatesAcrossExamCombos()
    {
        // Same programId appears in both combos → dedup keeps first
        List<RowMap> first = rows(1);
        when(recommendationMapper.selectCandidates(SUBJ_22408, Collections.emptyList(), null, 350, 30, "full_time"))
            .thenReturn(first);
        when(recommendationMapper.selectCandidates(SUBJ_11408, Collections.emptyList(), null, 350, 30, "full_time"))
            .thenReturn(rows(1)); // same id

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", "不限"),
            350);

        assertEquals(1, pool.size());
    }

    private List<RowMap> rows(long... ids)
    {
        List<RowMap> rows = new ArrayList<>();
        for (long id : ids)
        {
            RowMap row = new RowMap();
            row.put("programId", id);
            rows.add(row);
        }
        return rows;
    }

    private List<RowMap> rows(int count)
    {
        return rows(1, count);
    }

    private List<RowMap> rows(int from, int to)
    {
        List<RowMap> rows = new ArrayList<>();
        for (long i = from; i <= to; i++)
        {
            RowMap row = new RowMap();
            row.put("programId", i);
            rows.add(row);
        }
        return rows;
    }

    @Test
    void buildAnalysisPoolStratifiesByGap()
    {
        List<RowMap> rows = new ArrayList<>();
        rows.add(row(1L, "保底校A", 278));  // gap=22 → safe
        rows.add(row(2L, "保底校B", 280));  // gap=20 → safe
        rows.add(row(3L, "稳妥校A", 290));  // gap=10 → steady
        rows.add(row(4L, "稳妥校B", 295));  // gap=5  → steady
        rows.add(row(5L, "冲刺校A", 305));  // gap=-5 → reach
        rows.add(row(6L, "冲刺校B", 310));  // gap=-10 → reach
        rows.add(row(7L, "太难校", 312));   // gap=-12 → skip

        when(recommendationMapper.selectForAnalysis(300, List.of("福建"), 280, 320))
            .thenReturn(rows);

        List<RowMap> pool = service.buildAnalysisPool(300, List.of("福建"));

        assertTrue(pool.stream().anyMatch(r -> r.get("schoolName").equals("保底校A")));
        assertTrue(pool.stream().anyMatch(r -> r.get("schoolName").equals("稳妥校A")));
        assertTrue(pool.stream().anyMatch(r -> r.get("schoolName").equals("冲刺校B")));
        assertFalse(pool.stream().anyMatch(r -> r.get("schoolName").equals("太难校")));
        assertEquals(6, pool.size());
    }

    @Test
    void buildAnalysisPoolCapsPerStratum()
    {
        List<RowMap> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) rows.add(row((long) (1000 + i), "保底" + i, 280));
        for (int i = 0; i < 25; i++) rows.add(row((long) (2000 + i), "稳妥" + i, 295));
        for (int i = 0; i < 25; i++) rows.add(row((long) (3000 + i), "冲刺" + i, 308));

        when(recommendationMapper.selectForAnalysis(300, List.of("福建"), 280, 320))
            .thenReturn(rows);

        List<RowMap> pool = service.buildAnalysisPool(300, List.of("福建"));

        assertEquals(50, pool.size());
        long safeCount = pool.stream().filter(r -> r.get("schoolName").toString().startsWith("保底")).count();
        long steadyCount = pool.stream().filter(r -> r.get("schoolName").toString().startsWith("稳妥")).count();
        long reachCount = pool.stream().filter(r -> r.get("schoolName").toString().startsWith("冲刺")).count();
        assertEquals(15, safeCount);
        assertEquals(20, steadyCount);
        assertEquals(15, reachCount);
    }

    private RowMap row(long programId, String schoolName, int avgScore)
    {
        RowMap row = new RowMap();
        row.put("programId", programId);
        row.put("schoolName", schoolName);
        row.put("avgAdmittedScore", avgScore);
        row.put("schoolTier", "双非");
        row.put("city", "测试市");
        row.put("programName", "计算机科学与技术");
        return row;
    }
}
