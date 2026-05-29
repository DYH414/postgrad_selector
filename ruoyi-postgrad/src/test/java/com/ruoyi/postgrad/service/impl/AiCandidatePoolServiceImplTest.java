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
        verify(recommendationMapper, never()).selectCandidates(eq("408"), eq(Arrays.asList("福建")), eq(null), eq(315), eq(30), eq("full_time"));
    }

    @Test
    void buildPoolUsesProfileRegionsWhenCandidateIdsAreMissing()
    {
        List<RowMap> expected = rows(2);
        when(recommendationMapper.selectCandidates("408", Arrays.asList("福建", "广东"), null, 330, 30, "full_time"))
            .thenReturn(expected);

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", "[\"福建\",\"广东\"]"),
            330);

        assertEquals(expected, pool);
        verify(recommendationMapper).selectCandidates("408", Arrays.asList("福建", "广东"), null, 330, 30, "full_time");
        verify(recommendationMapper, never()).selectProgramsByIds(eq(Arrays.asList(1L, 2L)), eq(330));
    }

    @Test
    void buildPoolUsesProfileRegionsWhenTargetRegionsAreJavaList()
    {
        List<RowMap> expected = rows(2);
        List<String> regions = Arrays.asList("福建", "广东");
        when(recommendationMapper.selectCandidates("408", regions, null, 330, 30, "full_time"))
            .thenReturn(expected);

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", regions),
            330);

        assertEquals(expected, pool);
        verify(recommendationMapper).selectCandidates("408", regions, null, 330, 30, "full_time");
    }

    @Test
    void buildPoolRejectsFractionalCandidateIdsAndFallsBackToProfile()
    {
        List<RowMap> expected = rows(1);
        when(recommendationMapper.selectCandidates("408", Collections.emptyList(), null, 330, 30, "full_time"))
            .thenReturn(expected);

        List<RowMap> pool = service.buildPool(
            Map.of("candidateIds", List.of(1.9D)),
            Map.of("targetRegions", "不限"),
            330);

        assertEquals(expected, pool);
        verify(recommendationMapper, never()).selectProgramsByIds(eq(List.of(1L)), eq(330));
        verify(recommendationMapper).selectCandidates("408", Collections.emptyList(), null, 330, 30, "full_time");
    }

    @Test
    void buildPoolFallsBackToAllRegionsWhenProfileRegionsReturnNoRows()
    {
        List<RowMap> expected = rows(1);
        when(recommendationMapper.selectCandidates("408", Arrays.asList("福建", "广东"), null, 300, 30, "full_time"))
            .thenReturn(Collections.emptyList());
        when(recommendationMapper.selectCandidates("408", Collections.emptyList(), null, 300, 30, "full_time"))
            .thenReturn(expected);

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", "福建，广东"),
            300);

        assertEquals(expected, pool);
        verify(recommendationMapper).selectCandidates("408", Arrays.asList("福建", "广东"), null, 300, 30, "full_time");
        verify(recommendationMapper).selectCandidates("408", Collections.emptyList(), null, 300, 30, "full_time");
    }

    @Test
    void buildPoolCapsDefaultPoolAtFiftyRows()
    {
        when(recommendationMapper.selectCandidates("408", Collections.emptyList(), null, 350, 30, "full_time"))
            .thenReturn(rows(55));

        List<RowMap> pool = service.buildPool(
            Collections.emptyMap(),
            Map.of("targetRegions", "不限"),
            350);

        assertEquals(50, pool.size());
    }

    private List<RowMap> rows(int count)
    {
        List<RowMap> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++)
        {
            RowMap row = new RowMap();
            row.put("programId", (long) i);
            rows.add(row);
        }
        return rows;
    }
}
