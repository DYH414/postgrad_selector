package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagEvidenceRequest;
import com.ruoyi.postgrad.rag.dto.RagEvidenceResponse;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RagEvidenceServiceTest {

    @Test
    void shouldSearchSchoolGuideEvidenceWithMetadataFilters() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse searchResponse = new RagSearchTestResponse();
        searchResponse.setQuery("广州大学报考条件");
        searchResponse.setRetrievedCount(1);
        searchResponse.setSelectedCount(1);
        searchResponse.setChunks(java.util.List.of(chunk("广州大学 2025 年硕士研究生招生简章", true)));
        when(retrievalService.retrieve(any(RagSearchTestRequest.class))).thenReturn(result(searchResponse));

        RagEvidenceService service = new RagEvidenceService(retrievalService);
        RagEvidenceRequest request = new RagEvidenceRequest();
        request.setQuery("报考条件");
        request.setSchoolName("广州大学");
        request.setDocType("admission_guide");
        request.setMaxResults(6);
        request.setRerankTopN(2);

        RagEvidenceResponse response = service.retrieveProgramEvidence(request);

        ArgumentCaptor<RagSearchTestRequest> captor = ArgumentCaptor.forClass(RagSearchTestRequest.class);
        verify(retrievalService).retrieve(captor.capture());
        RagSearchTestRequest actual = captor.getValue();
        assertEquals("报考条件", actual.getQuery());
        assertEquals("广州大学", actual.getSchoolName());
        assertEquals(null, actual.getProgramId());
        assertEquals(null, actual.getSchoolId());
        assertEquals("admission_guide", actual.getDocType());
        assertEquals(6, actual.getMaxResults());
        assertEquals(2, actual.getRerankTopN());

        assertFalse(response.isFallbackUsed());
        assertEquals(1, response.getSelectedChunks().size());
        assertEquals(1, response.getCitations().size());
        assertEquals("admission_guide", response.getCitations().get(0).getDocType());
        assertEquals("广州大学", response.getCitations().get(0).getSchoolName());
    }

    @Test
    void shouldFallbackToBroaderMetadataSearchWhenStrictDocTypeReturnsNothing() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse empty = new RagSearchTestResponse();
        empty.setRetrievedCount(0);
        empty.setSelectedCount(0);
        empty.setChunks(java.util.List.of());

        RagSearchTestResponse fallback = new RagSearchTestResponse();
        fallback.setRetrievedCount(1);
        fallback.setSelectedCount(1);
        fallback.setChunks(java.util.List.of(chunk("广州大学 2025 年硕士研究生招生简章", true)));
        when(retrievalService.retrieve(any(RagSearchTestRequest.class))).thenReturn(result(empty), result(fallback));

        RagEvidenceService service = new RagEvidenceService(retrievalService);
        RagEvidenceRequest request = new RagEvidenceRequest();
        request.setQuery("报考条件");
        request.setSchoolName("广州大学");
        request.setRunId("report-run-7");
        request.setDocType("admission_guide");

        RagEvidenceResponse response = service.retrieveProgramEvidence(request);

        assertTrue(response.isFallbackUsed());
        assertEquals("metadata_broad", response.getFallbackMode());
        assertEquals(1, response.getSelectedChunks().size());
        ArgumentCaptor<RagSearchTestRequest> captor = ArgumentCaptor.forClass(RagSearchTestRequest.class);
        verify(retrievalService, times(2)).retrieve(captor.capture());
        assertEquals(java.util.List.of("report-run-7", "report-run-7"), captor.getAllValues().stream()
            .map(RagSearchTestRequest::getRunId)
            .toList());
        assertEquals("admission_guide", captor.getAllValues().get(0).getDocType());
        assertEquals(null, captor.getAllValues().get(1).getDocType());
        assertEquals("广州大学", captor.getAllValues().get(1).getSchoolName());
    }

    @Test
    void shouldRecordRecommendationEvidenceTrace() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse searchResponse = new RagSearchTestResponse();
        searchResponse.setRetrievedCount(1);
        searchResponse.setSelectedCount(1);
        searchResponse.setAppliedFilters(java.util.Map.of(
            "school_name", "广州大学",
            "doc_type", "admission_guide"
        ));
        searchResponse.setChunks(java.util.List.of(chunk("广州大学 2025 年硕士研究生招生简章", true)));
        when(retrievalService.retrieve(any(RagSearchTestRequest.class))).thenReturn(result(searchResponse));

        RagTraceRecorder recorder = new RagTraceRecorder();
        RagEvidenceService service = new RagEvidenceService(retrievalService, recorder);
        RagEvidenceRequest request = new RagEvidenceRequest();
        request.setQuery("招生简章");
        request.setRunId("run-1");
        request.setSchoolName("广州大学");
        request.setDocType("admission_guide");

        service.retrieveProgramEvidence(request);

        RagTrace trace = recorder.listRecent(1).get(0);
        assertEquals("recommendation-evidence", trace.getOperationKey());
        assertEquals("run-1", trace.getRunId());
        assertEquals("scoped-evidence", trace.getRetrievalRoute());
        assertEquals(RagTrace.Outcome.SUCCESS, trace.getOutcome());
        assertEquals(1, trace.getRetrievedCount());
        assertEquals(1, trace.getSelectedCount());
        assertEquals("广州大学", trace.getMetadataFilters().get("school_name"));
        assertEquals("admission_guide", trace.getMetadataFilters().get("doc_type"));
        assertEquals(false, trace.isFallbackUsed());
        assertEquals(java.util.List.of("广州大学-招生简章.md"), trace.getTopSources());
    }

    private static RagChunkResult chunk(String title, boolean selected) {
        RagChunkResult chunk = new RagChunkResult();
        chunk.setChunkTitle(title);
        chunk.setSourceName("广州大学-招生简章.md");
        chunk.setSourceUrl("https://example.com/source");
        chunk.setDocType("admission_guide");
        chunk.setSchoolName("广州大学");
        chunk.setYear("2025");
        chunk.setSection("application_requirements");
        chunk.setChunkIndex("0");
        chunk.setContentHash("abc123");
        chunk.setContentPreview("考生须符合招生简章规定的报考条件。");
        chunk.setSelectedForPrompt(selected);
        chunk.setVectorRank(1);
        chunk.setRerankRank(1);
        return chunk;
    }

    private static RagRetrievalResult result(RagSearchTestResponse response) {
        return new RagRetrievalResult(response, java.util.List.of());
    }
}
