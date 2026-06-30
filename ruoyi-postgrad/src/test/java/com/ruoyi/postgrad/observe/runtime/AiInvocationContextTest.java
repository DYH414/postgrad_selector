package com.ruoyi.postgrad.observe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AiInvocationContextTest {

    @Test
    void operationKeyShouldFallbackToUnknown() {
        AiInvocationContext.clear();
        assertEquals("unknown", AiInvocationContext.getOperationKey());
    }
}
