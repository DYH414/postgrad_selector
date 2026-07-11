package com.ruoyi.postgrad.recommend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AgentRunContextTest {

    @Test
    void createsUniqueRunIdsForAcceptedMessages() {
        AgentRunContext first = AgentRunContext.start(7L);
        AgentRunContext second = AgentRunContext.start(7L);

        assertEquals(7L, first.getUserId());
        assertNotNull(first.getRunId());
        assertNotEquals(first.getRunId(), second.getRunId());
    }
}
