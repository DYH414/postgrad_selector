package com.ruoyi.postgrad.observe.llm.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshot;
import org.junit.jupiter.api.Test;

class AiLlmCallTraceTest {
    @Test
    void errorSummaryShouldBeRedactedAndBounded() {
        AiInvocationSnapshot snapshot = new AiInvocationSnapshot();
        snapshot.setErrorSummary("Bearer abcdefghijklmnop " + "x".repeat(600));
        AiLlmCallTrace trace = AiLlmCallTrace.from(snapshot);
        assertTrue(trace.getErrorSummary().length() <= 512);
        assertFalse(trace.getErrorSummary().contains("abcdefghijklmnop"));
    }
}
