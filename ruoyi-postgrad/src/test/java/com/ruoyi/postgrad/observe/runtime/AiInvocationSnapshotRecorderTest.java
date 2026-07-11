package com.ruoyi.postgrad.observe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AiInvocationSnapshotRecorderTest {

    @Test
    void latestSnapshotShouldTrackSuccessAndError() {
        AiInvocationSnapshotRecorder recorder = new AiInvocationSnapshotRecorder();

        recorder.recordSuccess("ai-chat", "openai", "deepseek-v4-flash", 1200L, 12, 34);
        AiInvocationSnapshot success = recorder.getLatest();
        assertEquals("ai-chat", success.getOperationKey());
        assertEquals("SUCCESS", success.getOutcome());
        assertEquals(1200L, success.getDurationMs());
        assertEquals(12, success.getInputTokens());
        assertEquals(34, success.getOutputTokens());
        assertNotNull(success.getOccurredAt());

        recorder.recordError("ai-recommend-draft", "openai", "deepseek-v4-flash", "boom");
        AiInvocationSnapshot error = recorder.getLatest();
        assertEquals("ai-recommend-draft", error.getOperationKey());
        assertEquals("ERROR", error.getOutcome());
        assertEquals("boom", error.getErrorSummary());
        assertNotNull(error.getOccurredAt());
    }

    @Test
    void latestSnapshotCarriesRunId() {
        AiInvocationSnapshotRecorder recorder = new AiInvocationSnapshotRecorder();
        recorder.recordSuccess("ai-chat", "run-1", "openai", "model", 10L, 1, 2);
        assertEquals("run-1", recorder.getLatest().getRunId());
    }
}
