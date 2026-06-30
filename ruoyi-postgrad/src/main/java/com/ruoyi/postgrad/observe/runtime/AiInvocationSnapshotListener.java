package com.ruoyi.postgrad.observe.runtime;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AiInvocationSnapshotListener implements ChatModelListener {

    private static final String OPERATION_DURATION = "gen_ai.client.operation.duration";
    private static final String START_NANO = "postgrad.ai.startNano";

    private final MeterRegistry meterRegistry;
    private final AiInvocationSnapshotRecorder recorder;

    public AiInvocationSnapshotListener(MeterRegistry meterRegistry, AiInvocationSnapshotRecorder recorder) {
        this.meterRegistry = meterRegistry;
        this.recorder = recorder;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(START_NANO, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        long durationMs = durationMillis(responseContext.attributes());
        TokenUsage tokenUsage = responseContext.chatResponse() != null ? responseContext.chatResponse().tokenUsage() : null;
        String operationKey = operationKey(responseContext.attributes());
        String provider = provider(responseContext.modelProvider());
        String model = modelName(responseContext);
        recordDurationMetric(operationKey, provider, model, "SUCCESS", durationMs);
        recorder.recordSuccess(
            operationKey,
            provider,
            model,
            durationMs,
            tokenUsage != null && tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0,
            tokenUsage != null && tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0
        );
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        String operationKey = operationKey(errorContext.attributes());
        String provider = provider(errorContext.modelProvider());
        String model = requestModelName(errorContext);
        long durationMs = durationMillis(errorContext.attributes());
        recordDurationMetric(operationKey, provider, model, "ERROR", durationMs);
        recorder.recordError(
            operationKey,
            provider,
            model,
            summarize(errorContext.error())
        );
    }

    private void recordDurationMetric(String operationKey, String provider, String model, String outcome, long durationMs) {
        Timer.builder(OPERATION_DURATION)
            .description("Measures AI operation duration")
            .tag("postgrad.ai.operation", operationKey)
            .tag("outcome", outcome)
            .tag("gen_ai.provider.name", provider)
            .tag("gen_ai.request.model", model)
            .tag("gen_ai.response.model", model)
            .tag("gen_ai.operation.name", "chat")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    private long durationMillis(Map<Object, Object> attributes) {
        Object value = attributes.get(START_NANO);
        if (!(value instanceof Long startNano)) {
            return 0L;
        }
        return Math.max(0L, (System.nanoTime() - startNano) / 1_000_000L);
    }

    private String operationKey(Map<Object, Object> attributes) {
        Object value = attributes.get(AiObserveAttributes.OPERATION_KEY);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return AiInvocationContext.getOperationKey();
    }

    private String provider(ModelProvider provider) {
        return provider != null ? provider.name().toLowerCase() : "unknown";
    }

    private String modelName(ChatModelResponseContext responseContext) {
        if (responseContext.chatResponse() != null && responseContext.chatResponse().metadata() != null
            && responseContext.chatResponse().metadata().modelName() != null) {
            return responseContext.chatResponse().metadata().modelName();
        }
        return requestModelName(responseContext);
    }

    private String requestModelName(ChatModelResponseContext responseContext) {
        if (responseContext.chatRequest() != null
            && responseContext.chatRequest().parameters() != null
            && responseContext.chatRequest().parameters().modelName() != null) {
            return responseContext.chatRequest().parameters().modelName();
        }
        if (responseContext.chatResponse() != null && responseContext.chatResponse().metadata() != null
            && responseContext.chatResponse().metadata().modelName() != null) {
            return responseContext.chatResponse().metadata().modelName();
        }
        return "unknown";
    }

    private String requestModelName(ChatModelErrorContext errorContext) {
        if (errorContext.chatRequest() != null
            && errorContext.chatRequest().parameters() != null
            && errorContext.chatRequest().parameters().modelName() != null) {
            return errorContext.chatRequest().parameters().modelName();
        }
        return "unknown";
    }

    private String summarize(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return (message == null || message.isBlank()) ? error.getClass().getSimpleName() : message;
    }
}
