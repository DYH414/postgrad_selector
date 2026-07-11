package com.ruoyi.postgrad.observe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AiInvocationContextTest {

    @Test
    void operationKeyShouldFallbackToUnknown() {
        AiInvocationContext.clear();
        assertEquals("unknown", AiInvocationContext.getOperationKey());
    }

    @Test
    void clearRemovesRunId() {
        AiInvocationContext.setRunId("run-1");
        assertEquals("run-1", AiInvocationContext.getRunId());
        AiInvocationContext.clear();
        assertNull(AiInvocationContext.getRunId());
    }
}
