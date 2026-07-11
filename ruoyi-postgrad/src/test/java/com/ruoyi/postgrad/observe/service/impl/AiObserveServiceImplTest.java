package com.ruoyi.postgrad.observe.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Timer;
import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshotRecorder;
import com.ruoyi.postgrad.rag.RagStatus;
import com.ruoyi.postgrad.rag.status.RagIndexStatusService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AiObserveServiceImplTest {

    @Test
    void overviewShouldExposePersistedRagIndexStatus() {
        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        RagStatus status = new RagStatus();
        status.setEnabled(true);
        status.setIndexed(true);
        status.setDocumentCount(2090);
        status.setChunkCount(12515);
        when(statusService.getStatus()).thenReturn(status);

        AiObserveOverviewVO overview = new AiObserveServiceImpl(
            new SimpleMeterRegistry(), statusService, new AiInvocationSnapshotRecorder()).getOverview();

        assertTrue(overview.getRag().isIndexed());
        assertEquals(2090, overview.getRag().getDocumentCount());
        assertEquals(12515, overview.getRag().getChunkCount());
    }

    @Test
    void overviewShouldDegradeGracefullyWhenNoMetricsExist() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        when(statusService.getStatus()).thenReturn(new RagStatus());

        AiObserveServiceImpl service = new AiObserveServiceImpl(
            meterRegistry,
            statusService,
            new AiInvocationSnapshotRecorder()
        );

        AiObserveOverviewVO overview = service.getOverview();

        assertEquals(0L, overview.getTotalCalls());
        assertEquals(0.0D, overview.getSuccessRate());
        assertEquals(0.0D, overview.getAverageDurationMs());
        assertEquals(0.0D, overview.getInputTokens());
        assertEquals(0.0D, overview.getOutputTokens());
        assertNotNull(overview.getLatest());
        assertNotNull(overview.getRag());
    }

    @Test
    void overviewShouldAggregateDurationMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        meterRegistry.timer("gen_ai.client.operation.duration",
            "postgrad.ai.operation", "ai-chat",
            "outcome", "SUCCESS",
            "gen_ai.request.model", "deepseek-v4-flash",
            "gen_ai.response.model", "deepseek-v4-flash",
            "gen_ai.provider.name", "openai",
            "gen_ai.operation.name", "chat")
            .record(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        meterRegistry.timer("gen_ai.client.operation.duration",
            "postgrad.ai.operation", "ai-chat",
            "outcome", "ERROR",
            "gen_ai.request.model", "deepseek-v4-flash",
            "gen_ai.response.model", "deepseek-v4-flash",
            "gen_ai.provider.name", "openai",
            "gen_ai.operation.name", "chat")
            .record(300, java.util.concurrent.TimeUnit.MILLISECONDS);

        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        when(statusService.getStatus()).thenReturn(new RagStatus());

        AiObserveServiceImpl service = new AiObserveServiceImpl(
            meterRegistry,
            statusService,
            new AiInvocationSnapshotRecorder()
        );

        AiObserveOverviewVO overview = service.getOverview();

        assertEquals(2L, overview.getTotalCalls());
        assertEquals(50.0D, overview.getSuccessRate());
        assertEquals(200.0D, overview.getAverageDurationMs());
    }
}
