package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationContext;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationResult;
import com.ruoyi.postgrad.recommend.routing.AgentIntentRoute;
import com.ruoyi.postgrad.recommend.routing.AgentIntentRouter;
import com.ruoyi.postgrad.recommend.routing.AgentRouteDecision;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class EvaluationAgentInvocationServiceTest {

    @Test
    void draftWriteRouteShortCircuitsBeforeModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        AgentIntentRouter router = mock(AgentIntentRouter.class);
        when(router.route(any(), any())).thenReturn(
            new AgentRouteDecision(AgentIntentRoute.DRAFT_WRITE, "user wants to add a school"));

        EvaluationAgentInvocationService service = new EvaluationAgentInvocationService(
            chatModel, mock(StringRedisTemplate.class), mock(V2ChatTools.class),
            mock(AgentToolRuntime.class), router, mock(RecommendationMapper.class),
            mock(IDraftService.class));

        EvaluationAgentInvocationResult result = service.invoke(
            EvaluationAgentInvocationContext.of("run-1", 1L, "invoke:run-1:1", 999L, 2L,
                "帮我把深圳大学加入草稿", "系统提示词 {profileContext}"));

        // 写路由在进模型前短路
        assertEquals("UNSUPPORTED_SIDE_EFFECT", result.status());
        assertEquals("DRAFT_WRITE", result.route());
        assertNull(result.answer());
        // 关键：模型从未被调用
        verify(chatModel, never()).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
    }
}
