package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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
    void buildAnalysisPoolStratifiesByGap()
    {
        List<RowMap> rows = new ArrayList<>();
        rows.add(row(1L, "保底校A", 278));  // gap = 22 → safe
        rows.add(row(2L, "保底校B", 280));  // gap = 20 → safe
        rows.add(row(3L, "稳妥校A", 290));  // gap = 10 → steady
        rows.add(row(4L, "稳妥校B", 295));  // gap = 5  → steady
        rows.add(row(5L, "冲刺校A", 305));  // gap = -5 → reach
        rows.add(row(6L, "冲刺校B", 310));  // gap = -10 → reach
        rows.add(row(7L, "太难校", 312));   // gap = -12 → skip

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
        for (int i = 0; i < 25; i++) rows.add(row((long) (1000 + i), "保底" + i, 280));  // gap=20
        for (int i = 0; i < 25; i++) rows.add(row((long) (2000 + i), "稳妥" + i, 295));  // gap=5
        for (int i = 0; i < 25; i++) rows.add(row((long) (3000 + i), "冲刺" + i, 308));  // gap=-8

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
