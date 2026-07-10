package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

class RagMetadataFilterBuilderTest {

    @Test
    void shouldReturnNullWhenRequestHasNoMetadataFilters() {
        RagSearchTestRequest request = new RagSearchTestRequest();

        assertNull(RagMetadataFilterBuilder.fromRequest(request));
    }

    @Test
    void shouldBuildAndFilterMatchingMetadata() {
        RagSearchTestRequest request = new RagSearchTestRequest();
        request.setSchoolId("1");
        request.setProgramId("101");
        request.setYear("2025");
        request.setDocType("program_year_summary");
        request.setSection("admission_score");

        Filter filter = RagMetadataFilterBuilder.fromRequest(request);

        assertTrue(filter.test(new Metadata()
            .put("school_id", "1")
            .put("program_id", "101")
            .put("year", "2025")
            .put("doc_type", "program_year_summary")
            .put("section", "admission_score")));
        assertFalse(filter.test(new Metadata()
            .put("school_id", "2")
            .put("program_id", "101")
            .put("year", "2025")
            .put("doc_type", "program_year_summary")
            .put("section", "admission_score")));
    }
}
