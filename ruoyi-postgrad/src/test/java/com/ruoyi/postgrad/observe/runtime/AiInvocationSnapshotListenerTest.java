package com.ruoyi.postgrad.observe.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import com.ruoyi.postgrad.observe.llm.service.AiLlmTracePersistenceService;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;

class AiInvocationSnapshotListenerTest {

    @Test
    void onResponseShouldRecordDurationMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiInvocationSnapshotRecorder recorder = new AiInvocationSnapshotRecorder();
        AiLlmTracePersistenceService persistence = mock(AiLlmTracePersistenceService.class);
        AiInvocationSnapshotListener listener = new AiInvocationSnapshotListener(meterRegistry, recorder, persistence);

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(attributes);
        AiInvocationContext.setOperationKey("ai-chat");
        AiInvocationContext.setRunId("run-1");

        ChatRequestParameters parameters = mock(ChatRequestParameters.class);
        when(parameters.modelName()).thenReturn("deepseek-v4-flash");
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.parameters()).thenReturn(parameters);

        TokenUsage tokenUsage = mock(TokenUsage.class);
        when(tokenUsage.inputTokenCount()).thenReturn(12);
        when(tokenUsage.outputTokenCount()).thenReturn(34);

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.modelName()).thenReturn("deepseek-v4-flash");
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.tokenUsage()).thenReturn(tokenUsage);
        when(chatResponse.metadata()).thenReturn(metadata);

        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        when(responseContext.attributes()).thenReturn(attributes);
        when(responseContext.chatRequest()).thenReturn(chatRequest);
        when(responseContext.chatResponse()).thenReturn(chatResponse);

        listener.onRequest(requestContext);
        listener.onResponse(responseContext);
        AiInvocationContext.clear();

        Timer timer = meterRegistry.find("gen_ai.client.operation.duration")
            .tag("postgrad.ai.operation", "ai-chat")
            .tag("outcome", "SUCCESS")
            .tag("gen_ai.request.model", "deepseek-v4-flash")
            .timer();

        assertTrue(timer != null);
        assertEquals(1L, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 0L);
        assertEquals("ai-chat", recorder.getLatest().getOperationKey());
        assertEquals("run-1", recorder.getLatest().getRunId());
        verify(persistence).persist(recorder.getLatest());
    }

    @Test
    void onErrorShouldRecordDurationMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiInvocationSnapshotRecorder recorder = new AiInvocationSnapshotRecorder();
        AiLlmTracePersistenceService persistence = mock(AiLlmTracePersistenceService.class);
        AiInvocationSnapshotListener listener = new AiInvocationSnapshotListener(meterRegistry, recorder, persistence);

        Map<Object, Object> attributes = new HashMap<>();
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(attributes);
        listener.onRequest(requestContext);

        ChatRequestParameters parameters = mock(ChatRequestParameters.class);
        when(parameters.modelName()).thenReturn("deepseek-v4-flash");
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.parameters()).thenReturn(parameters);

        ChatModelErrorContext errorContext = mock(ChatModelErrorContext.class);
        when(errorContext.attributes()).thenReturn(attributes);
        when(errorContext.chatRequest()).thenReturn(chatRequest);
        when(errorContext.error()).thenReturn(new IllegalStateException("boom"));

        listener.onError(errorContext);

        Timer timer = meterRegistry.find("gen_ai.client.operation.duration")
            .tag("postgrad.ai.operation", "unknown")
            .tag("outcome", "ERROR")
            .tag("gen_ai.request.model", "deepseek-v4-flash")
            .timer();

        assertTrue(timer != null);
        assertEquals(1L, timer.count());
        verify(persistence).persist(recorder.getLatest());
    }
}
