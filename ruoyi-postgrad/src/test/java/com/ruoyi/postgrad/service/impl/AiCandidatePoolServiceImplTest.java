package com.ruoyi.postgrad.service.impl;

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
}
