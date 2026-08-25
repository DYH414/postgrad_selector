package com.ruoyi.postgrad.recommend.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.postgrad.recommend.domain.ChatMessageVO;
import java.lang.reflect.Method;
import java.util.List;
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

    @Test
    void shortContinuationIncludesLatestAssistantReplyForReferenceResolution() throws Exception {
        List<ChatMessageVO> history = List.of(
            new ChatMessageVO("user", "帮我添加湘潭大学和宁夏大学"),
            new ChatMessageVO("assistant", "待确认提案：添加湘潭大学。确认后继续添加宁夏大学。"));

        Method method = AgentIntentRouter.class.getDeclaredMethod(
            "buildRoutingInput", String.class, List.class);
        method.setAccessible(true);
        String input = (String) method.invoke(null, "继续", history);

        assertTrue(input.contains("最近助手回复（仅用于解析指代"));
        assertTrue(input.contains("确认后继续添加宁夏大学"));
    }
}
