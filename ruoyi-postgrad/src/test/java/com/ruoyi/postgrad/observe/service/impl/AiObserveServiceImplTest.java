package com.ruoyi.postgrad.observe.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Timer;
import com.ruoyi.postgrad.observe.domain.AiObserveOverviewVO;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshotRecorder;
import com.ruoyi.postgrad.rag.RagDocumentService;
import com.ruoyi.postgrad.rag.RagStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AiObserveServiceImplTest {

    @Test
    void overviewShouldDegradeGracefullyWhenNoMetricsExist() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RagDocumentService ragDocumentService = mock(RagDocumentService.class);
        when(ragDocumentService.getStatus()).thenReturn(new RagStatus());

        AiObserveServiceImpl service = new AiObserveServiceImpl(
            meterRegistry,
            ragDocumentService,
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

        RagDocumentService ragDocumentService = mock(RagDocumentService.class);
        when(ragDocumentService.getStatus()).thenReturn(new RagStatus());

        AiObserveServiceImpl service = new AiObserveServiceImpl(
            meterRegistry,
            ragDocumentService,
            new AiInvocationSnapshotRecorder()
        );

        AiObserveOverviewVO overview = service.getOverview();

        assertEquals(2L, overview.getTotalCalls());
        assertEquals(50.0D, overview.getSuccessRate());
        assertEquals(200.0D, overview.getAverageDurationMs());
    }
}
