package com.ruoyi.postgrad.rag.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.postgrad.rag.trace.RagTrace;
import com.ruoyi.postgrad.rag.trace.RagTraceRecorder;
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
            chunk.setContentPreview("preview text...");
        });
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
