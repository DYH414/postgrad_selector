package com.ruoyi.postgrad.rag.trace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ruoyi.postgrad.rag.mapper.RagRetrievalTraceMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RagTraceRecorderPersistenceTest {

    @Test
    void recordPersistsBoundedSummariesAndKeepsMemoryCompatibility() {
        RagRetrievalTraceMapper mapper = mock(RagRetrievalTraceMapper.class);
        RagTraceRecorder recorder = new RagTraceRecorder(mapper);
        RagTrace trace = traceWithLargeContent();

        recorder.record(trace);

        ArgumentCaptor<RagRetrievalTrace> captor = ArgumentCaptor.forClass(RagRetrievalTrace.class);
        verify(mapper).insert(captor.capture());
        RagRetrievalTrace stored = captor.getValue();
        assertEquals(trace.getTraceId(), stored.getTraceId());
        assertTrue(stored.getQuerySummary().length() <= 512);
        assertTrue(stored.getSelectedChunksSummary().length() <= 4000);
        assertFalse(stored.getSelectedChunksSummary().contains("FULL_CHUNK_TAIL"));
        assertEquals(trace, recorder.listRecent(1).get(0));
    }

    @Test
    void recordIsFailOpenWhenPersistenceFails() {
        RagRetrievalTraceMapper mapper = mock(RagRetrievalTraceMapper.class);
        doThrow(new IllegalStateException("database unavailable")).when(mapper).insert(any());
        RagTraceRecorder recorder = new RagTraceRecorder(mapper);
        RagTrace trace = new RagTrace();

        assertDoesNotThrow(() -> recorder.record(trace));

        assertEquals(1, recorder.size());
        assertEquals(trace, recorder.listRecent(1).get(0));
    }

    private RagTrace traceWithLargeContent() {
        RagTrace trace = new RagTrace();
        trace.setOperationKey("recommend-evidence");
        trace.setRunId("run-1");
        trace.setQuery("q".repeat(700));
        trace.setOutcome(RagTrace.Outcome.SUCCESS);
        trace.setSelectedChunks(List.of(
            new RagTrace.SelectedChunk("source-a", "a".repeat(5000) + "FULL_CHUNK_TAIL")));
        return trace;
    }
}
