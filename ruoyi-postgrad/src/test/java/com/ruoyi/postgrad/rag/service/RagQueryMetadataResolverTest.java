package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.School;
import com.ruoyi.postgrad.mapper.SchoolMapper;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import java.util.List;
import java.util.Map;
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

        assertEquals("深圳大学", request.getSchoolName());
        assertEquals(null, request.getSchoolId());
        assertEquals(Map.of(
            "source_type", "school_guide",
            "school_name", "深圳大学",
            "doc_type", "admission_guide"
        ), request.getMetadataFilters());
    }

    @Test
    void shouldConvertExplicitSchoolIdToCorpusSchoolName() {
        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        School shenzhen = new School();
        shenzhen.setId(15L);
        shenzhen.setName("深圳大学");
        when(schoolMapper.selectSchoolAll()).thenReturn(List.of(shenzhen));
        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学", 5, 0.5, 3);
        request.setSchoolId("15");

        new RagQueryMetadataResolver(schoolMapper).enrich(request);

        assertEquals(null, request.getSchoolId());
        assertEquals("深圳大学", request.getSchoolName());
        assertEquals(Map.of(
            "source_type", "school_guide",
            "school_name", "深圳大学",
            "doc_type", "admission_guide"
        ), request.getMetadataFilters());
    }

    @Test
    void schoolNameShouldRemoveIncompatibleSchoolIdFilter() {
        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学学费", 5, 0.5, 3);
        request.setSchoolName("深圳大学");
        request.setSchoolId("15");

        new RagQueryMetadataResolver(mock(SchoolMapper.class)).enrich(request);

        assertEquals("深圳大学", request.getSchoolName());
        assertEquals(null, request.getSchoolId());
        assertEquals(Map.of(
            "source_type", "school_guide",
            "school_name", "深圳大学",
            "doc_type", "admission_guide"
        ), request.getMetadataFilters());
    }

    @Test
    void generalQuestionShouldExcludeSchoolGuidesByDefault() {
        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        when(schoolMapper.selectSchoolAll()).thenReturn(List.of());
        RagSearchTestRequest request = new RagSearchTestRequest("没过国家线是不是就没戏了", 5, 0.5, 3);

        new RagQueryMetadataResolver(schoolMapper).enrich(request);

        assertEquals(Map.of("source_type", "general"), request.getMetadataFilters());
    }

    @Test
    void explicitDocumentTypeShouldNotBeOverwritten() {
        RagSearchTestRequest request = new RagSearchTestRequest("复试政策", 5, 0.5, 3);
        request.setDocType("custom_type");

        new RagQueryMetadataResolver(mock(SchoolMapper.class)).enrich(request);

        assertEquals("custom_type", request.getDocType());
        assertEquals("general", request.getSourceType());
    }
}
