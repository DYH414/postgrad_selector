package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void shouldSearchProgramEvidenceWithMetadataFilters() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse searchResponse = new RagSearchTestResponse();
        searchResponse.setQuery("广州大学网络空间安全录取趋势");
        searchResponse.setRetrievedCount(1);
        searchResponse.setSelectedCount(1);
        searchResponse.setChunks(java.util.List.of(chunk("广州大学 网络空间安全 历年分数趋势", true)));
        when(retrievalService.retrieve(any(RagSearchTestRequest.class))).thenReturn(result(searchResponse));

        RagEvidenceService service = new RagEvidenceService(retrievalService);
        RagEvidenceRequest request = new RagEvidenceRequest();
        request.setQuery("录取趋势");
        request.setProgramId(101L);
        request.setSchoolId(22L);
        request.setDocType("program_history");
        request.setSection("score_trend");
        request.setMaxResults(6);
        request.setRerankTopN(2);

        RagEvidenceResponse response = service.retrieveProgramEvidence(request);

        ArgumentCaptor<RagSearchTestRequest> captor = ArgumentCaptor.forClass(RagSearchTestRequest.class);
        verify(retrievalService).retrieve(captor.capture());
        RagSearchTestRequest actual = captor.getValue();
        assertEquals("录取趋势", actual.getQuery());
        assertEquals("101", actual.getProgramId());
        assertEquals("22", actual.getSchoolId());
        assertEquals("program_history", actual.getDocType());
        assertEquals("score_trend", actual.getSection());
        assertEquals(6, actual.getMaxResults());
        assertEquals(2, actual.getRerankTopN());

        assertFalse(response.isFallbackUsed());
        assertEquals(1, response.getSelectedChunks().size());
        assertEquals(1, response.getCitations().size());
        assertEquals("program_history", response.getCitations().get(0).getDocType());
        assertEquals("101", response.getCitations().get(0).getProgramId());
    }

    @Test
    void shouldFallbackToBroaderProgramSearchWhenStrictSectionReturnsNothing() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse empty = new RagSearchTestResponse();
        empty.setRetrievedCount(0);
        empty.setSelectedCount(0);
        empty.setChunks(java.util.List.of());

        RagSearchTestResponse fallback = new RagSearchTestResponse();
        fallback.setRetrievedCount(1);
        fallback.setSelectedCount(1);
        fallback.setChunks(java.util.List.of(chunk("广州大学 网络空间安全 风险因素", true)));
        when(retrievalService.retrieve(any(RagSearchTestRequest.class))).thenReturn(result(empty), result(fallback));

        RagEvidenceService service = new RagEvidenceService(retrievalService);
        RagEvidenceRequest request = new RagEvidenceRequest();
        request.setQuery("有什么风险");
        request.setProgramId(101L);
        request.setDocType("program_history");
        request.setSection("risk_factors");

        RagEvidenceResponse response = service.retrieveProgramEvidence(request);

        assertTrue(response.isFallbackUsed());
        assertEquals("program_broad", response.getFallbackMode());
        assertEquals(1, response.getSelectedChunks().size());
        assertEquals("risk_factors", response.getSelectedChunks().get(0).getSection());
    }

    @Test
    void shouldRecordRecommendationEvidenceTrace() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse searchResponse = new RagSearchTestResponse();
        searchResponse.setRetrievedCount(1);
        searchResponse.setSelectedCount(1);
        searchResponse.setAppliedFilters(java.util.Map.of(
            "program_id", "101",
            "doc_type", "program_history"
        ));
        searchResponse.setChunks(java.util.List.of(chunk("广州大学 网络空间安全 历年分数趋势", true)));
        when(retrievalService.retrieve(any(RagSearchTestRequest.class))).thenReturn(result(searchResponse));

        RagTraceRecorder recorder = new RagTraceRecorder();
        RagEvidenceService service = new RagEvidenceService(retrievalService, recorder);
        RagEvidenceRequest request = new RagEvidenceRequest();
        request.setQuery("推荐依据");
        request.setRunId("run-1");
        request.setProgramId(101L);
        request.setDocType("program_history");

        service.retrieveProgramEvidence(request);

        RagTrace trace = recorder.listRecent(1).get(0);
        assertEquals("recommendation-evidence", trace.getOperationKey());
        assertEquals("run-1", trace.getRunId());
        assertEquals("scoped-evidence", trace.getRetrievalRoute());
        assertEquals(RagTrace.Outcome.SUCCESS, trace.getOutcome());
        assertEquals(1, trace.getRetrievedCount());
        assertEquals(1, trace.getSelectedCount());
        assertEquals("101", trace.getMetadataFilters().get("program_id"));
        assertEquals("program_history", trace.getMetadataFilters().get("doc_type"));
        assertEquals(false, trace.isFallbackUsed());
        assertEquals(java.util.List.of("数据库生成文档"), trace.getTopSources());
    }

    private static RagChunkResult chunk(String title, boolean selected) {
        RagChunkResult chunk = new RagChunkResult();
        chunk.setChunkTitle(title);
        chunk.setSourceName("数据库生成文档");
        chunk.setSourceUrl("https://example.com/source");
        chunk.setDocType("program_history");
        chunk.setSchoolId("22");
        chunk.setProgramId("101");
        chunk.setSchoolName("广州大学");
        chunk.setProgramName("网络空间安全");
        chunk.setYear("2025");
        chunk.setSection("risk_factors");
        chunk.setChunkIndex("0");
        chunk.setContentHash("abc123");
        chunk.setContentPreview("录取均分波动，复试线需结合历年趋势判断。");
        chunk.setSelectedForPrompt(selected);
        chunk.setVectorRank(1);
        chunk.setRerankRank(1);
        return chunk;
    }

    private static RagRetrievalResult result(RagSearchTestResponse response) {
        return new RagRetrievalResult(response, java.util.List.of());
    }
}
