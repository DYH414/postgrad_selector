package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.School;
import com.ruoyi.postgrad.mapper.SchoolMapper;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagQueryMetadataResolverTest {

    @Test
    void shouldInferSchoolFilterFromSchoolNameInQuery() {
        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        School shenzhen = new School();
        shenzhen.setId(15L);
        shenzhen.setName("深圳大学");
        shenzhen.setShortName("深大");
        when(schoolMapper.selectSchoolAll()).thenReturn(List.of(shenzhen));

        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学", 5, 0.5, 3);

        new RagQueryMetadataResolver(schoolMapper).enrich(request);

        assertEquals("15", request.getSchoolId());
        assertEquals("15", request.getMetadataFilters().get("school_id"));
    }

    @Test
    void shouldNotOverrideExplicitSchoolFilter() {
        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学", 5, 0.5, 3);
        request.setSchoolId("99");

        new RagQueryMetadataResolver(schoolMapper).enrich(request);

        assertEquals("99", request.getSchoolId());
    }
}
