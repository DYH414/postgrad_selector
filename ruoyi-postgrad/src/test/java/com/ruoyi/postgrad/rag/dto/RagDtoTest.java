package com.ruoyi.postgrad.rag.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.knowledge.RagCitation;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

@DisplayName("RAG DTOs and Trace Model")
class RagDtoTest {

    @Test
    @DisplayName("RagSearchTestRequest defaults")
    void requestDefaults() {
        RagSearchTestRequest req = new RagSearchTestRequest();
        assertNull(req.getQuery());
        assertEquals(5, req.getMaxResults());
        assertEquals(0.6, req.getMinScore(), 0.001);
        assertEquals(3, req.getRerankTopN());
        assertTrue(req.getMetadataFilters().isEmpty());
    }

    @Test
    @DisplayName("RagSearchTestRequest supports metadata filters")
    void requestMetadataFilters() {
        RagSearchTestRequest req = new RagSearchTestRequest();
        req.setSchoolId("1");
        req.setProgramId("101");
        req.setYear("2025");
        req.setDocType("program_year_summary");
        req.setSection("admission_score");

        assertEquals(Map.of(
            "school_id", "1",
            "program_id", "101",
            "year", "2025",
            "doc_type", "program_year_summary",
            "section", "admission_score"
        ), req.getMetadataFilters());
    }

    @Test
    @DisplayName("RagChunkResult handles missing metadata fields")
    void chunkResultMissingMetadata() {
        RagChunkResult chunk = new RagChunkResult();
        assertNull(chunk.getSourceName());
        assertNull(chunk.getDocType());
        assertNull(chunk.getChunkTitle());
        assertNull(chunk.getContentHash());
        // should not throw when fields are null
        assertDoesNotThrow(() -> {
            chunk.setSourceName("test");
            chunk.setDocType("doc");
            chunk.setChunkTitle("title");
            chunk.setContentHash("abc");
            chunk.setSchoolName("南京大学");
            chunk.setProgramName("软件工程");
            chunk.setYear("2025");
            chunk.setSection("admission_score");
            chunk.setSourceUrl("https://example.com");
            chunk.setContentPreview("preview text...");
        });
    }

    @Test
    @DisplayName("RagCitation can be built from search-test chunk result metadata")
    void citationFromChunkResult() {
        RagChunkResult chunk = new RagChunkResult();
        chunk.setChunkTitle("广州大学 网络空间安全 - 历年分数趋势");
        chunk.setSourceName("program_history_22_101.md");
        chunk.setSourceUrl("https://example.com/source");
        chunk.setDocType("program_history");
        chunk.setSchoolId("22");
        chunk.setProgramId("101");
        chunk.setSchoolName("广州大学");
        chunk.setProgramName("网络空间安全");
        chunk.setYear("2025");
        chunk.setSection("score_trend");
        chunk.setContentHash("abc123");
        chunk.setContentPreview("近三年录取均分呈上升趋势。");

        RagCitation citation = RagCitation.fromChunkResult(chunk);

        assertEquals("广州大学 网络空间安全 - 历年分数趋势", citation.getTitle());
        assertEquals("program_history_22_101.md", citation.getSourceName());
        assertEquals("program_history", citation.getDocType());
        assertEquals("22", citation.getSchoolId());
        assertEquals("101", citation.getProgramId());
        assertEquals("score_trend", citation.getSection());
        assertEquals("近三年录取均分呈上升趋势。", citation.getContentPreview());
    }

    @Test
    @DisplayName("RagSearchTestResponse latency defaults")
    void responseLatencyDefaults() {
        RagSearchTestResponse resp = new RagSearchTestResponse();
        assertNotNull(resp.getLatency());
        assertNull(resp.getLatency().getEmbeddingMs());
        assertNull(resp.getLatency().getRetrievalMs());
        assertNull(resp.getLatency().getRerankMs());
        assertEquals(0L, resp.getLatency().getTotalMs());
        assertTrue(resp.getChunks().isEmpty());
    }

    @Test
    @DisplayName("RagTrace creates traceId and createdAt automatically")
    void traceAutoFields() {
        RagTrace trace = new RagTrace();
        assertNotNull(trace.getTraceId());
        assertEquals(8, trace.getTraceId().length());
        assertNotNull(trace.getCreatedAt());
    }

    @Test
    @DisplayName("RagTraceRecorder retains max 200 entries and lists in reverse chronological order")
    void traceRecorderBounded() {
        RagTraceRecorder recorder = new RagTraceRecorder();
        for (int i = 0; i < 250; i++) {
            RagTrace t = new RagTrace();
            t.setQuery("query-" + i);
            recorder.record(t);
        }
        assertTrue(recorder.size() <= 200, "should not exceed MAX_TRACES");

        List<RagTrace> recent = recorder.listRecent(5);
        assertEquals(5, recent.size());
        // most recent first
        assertTrue(recent.get(0).getQuery().startsWith("query-"));
    }

    @Test
    @DisplayName("RagTraceRecorder handles null trace gracefully")
    void traceRecorderNullSafe() {
        RagTraceRecorder recorder = new RagTraceRecorder();
        assertDoesNotThrow(() -> recorder.record(null));
        assertEquals(0, recorder.size());
    }
}
