package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.ProgramSearchMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgramSearchServiceImplTest
{
    private ProgramSearchServiceImpl service;

    @Mock
    private ProgramSearchMapper programSearchMapper;

    @BeforeEach
    void setUp() throws Exception
    {
        service = new ProgramSearchServiceImpl();
        Field field = ProgramSearchServiceImpl.class.getDeclaredField("programSearchMapper");
        field.setAccessible(true);
        field.set(service, programSearchMapper);
    }

    @Test
    void totalIsTheTrueMatchCountNotTheClampedPageSize()
    {
        when(programSearchMapper.searchByKeyword("中国科学院大学", 500, 0)).thenReturn(rows(50));
        when(programSearchMapper.countByKeyword("中国科学院大学")).thenReturn(101);

        Map<String, Object> result = service.searchPrograms("中国科学院大学", 1000, 0);

        assertEquals(101, result.get("total"), "total must be the true full-time match count");
        assertEquals(50, ((List<?>) result.get("items")).size());
        verify(programSearchMapper).searchByKeyword("中国科学院大学", 500, 0);
    }

    @Test
    void offsetIsForwardedAndSchoolTotalSurvivesNormalization()
    {
        RowMap row = new RowMap();
        row.put("programId", 1L);
        row.put("schoolTotal", 101L);
        when(programSearchMapper.searchByKeyword("中国科学院大学", 500, 101)).thenReturn(List.of(row));
        when(programSearchMapper.countByKeyword("中国科学院大学")).thenReturn(101);

        Map<String, Object> result = service.searchPrograms("中国科学院大学", 500, 101);

        assertEquals(101, result.get("offset"));
        assertEquals(101, result.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) ((List<?>) result.get("items")).get(0);
        assertEquals(101L, item.get("schoolTotal"));
        verify(programSearchMapper).searchByKeyword("中国科学院大学", 500, 101);
    }

    @Test
    void blankKeywordSkipsTheDatabaseAndReportsZero()
    {
        Map<String, Object> result = service.searchPrograms("   ", 50, 0);

        assertEquals(0, result.get("total"));
        assertTrue(((List<?>) result.get("items")).isEmpty());
        verifyNoInteractions(programSearchMapper);
    }

    private List<RowMap> rows(int count)
    {
        List<RowMap> rows = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            RowMap row = new RowMap();
            row.put("programId", (long) i + 1);
            row.put("schoolTotal", 101L);
            rows.add(row);
        }
        return rows;
    }
}
