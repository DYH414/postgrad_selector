package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ChatRagRouteResolverTest {
    private final ChatRagRouteResolver resolver = new ChatRagRouteResolver();

    @Test
    void routesCandidateRiskQuestionToScopedEvidence() {
        assertEquals(ChatRagRouteResolver.Route.SCOPED_EVIDENCE,
            resolver.resolve("杭州电子科技大学为什么被推荐，有什么风险"));
    }

    @Test
    void routesPolicyQuestionToGeneralKnowledge() {
        ChatRagRouteResolver.Route route = resolver.resolve("408 考什么科目");
        assertEquals(ChatRagRouteResolver.Route.GENERAL, route);
        assertEquals("general", route.traceValue());
    }
}
