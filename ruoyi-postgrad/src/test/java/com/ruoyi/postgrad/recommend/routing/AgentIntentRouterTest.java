package com.ruoyi.postgrad.recommend.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentIntentRouterTest {

    @Test
    void parsesStructuredRouteDecision() {
        AgentRouteDecision decision = AgentIntentRouter.parse(
            "```json\n{\"route\":\"HYBRID\",\"reason\":\"需要招生事实和学校简章\"}\n```");

        assertEquals(AgentIntentRoute.HYBRID, decision.route());
    }

    @Test
    void fallsBackSafelyWhenModelOutputIsInvalid() {
        AgentRouteDecision decision = AgentIntentRouter.parse("我觉得应该查一下");

        assertEquals(AgentIntentRoute.FALLBACK, decision.route());
    }

    @Test
    void routingPromptDefinesNonOverlappingCapabilities() {
        String prompt = AgentIntentRouter.routingPrompt();

        assertTrue(prompt.contains("KNOWLEDGE_RAG"));
        assertTrue(prompt.contains("PROGRAM_FACTS"));
        assertTrue(prompt.contains("DRAFT_READ"));
        assertTrue(prompt.contains("DRAFT_WRITE"));
        assertTrue(prompt.contains("CLARIFY"));
        assertTrue(prompt.contains("只输出 JSON"));
    }
}
