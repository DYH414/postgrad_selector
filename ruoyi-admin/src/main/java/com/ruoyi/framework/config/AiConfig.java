package com.ruoyi.framework.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshotListener;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshotRecorder;
import com.ruoyi.postgrad.observe.llm.service.AiLlmTracePersistenceService;
import com.ruoyi.postgrad.recommend.service.ConversationChatModelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * AI 模型统一配置 —— 替代各处散落的 buildChatModel() / buildStreamingChatModel()。
 *
 * <p>更换模型只需修改 application.yml 中 app.ai.deepseek.* 配置项。</p>
 * <p>提示词通过各 Service 的 @Value("classpath:prompts/...") 直接注入。</p>
 */
@Configuration
public class AiConfig {

    private static final Map<String, Object> DISABLED_THINKING = Map.of("thinking", Map.of("type", "disabled"));

    @Value("${app.ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${app.ai.deepseek.api-key}")
    private String apiKey;

    @Value("${app.ai.deepseek.model-name}")
    private String modelName;

    @Value("${app.ai.deepseek.max-tokens:4096}")
    private int maxTokens;

    @Value("${app.ai.deepseek.temperature:0.7}")
    private double temperature;

    @Value("${app.ai.deepseek.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${app.ai.rag.embedding.base-url:https://api.siliconflow.cn/v1}")
    private String ragEmbeddingBaseUrl;

    @Value("${app.ai.rag.embedding.api-key:}")
    private String ragEmbeddingApiKey;

    @Value("${app.ai.rag.embedding.model-name:BAAI/bge-m3}")
    private String ragEmbeddingModelName;

    @Value("${app.ai.rag.embedding.timeout-seconds:120}")
    private int ragEmbeddingTimeoutSeconds;

    private final MeterRegistry meterRegistry;
    private final AiInvocationSnapshotRecorder snapshotRecorder;
    private final AiLlmTracePersistenceService tracePersistenceService;

    public AiConfig(MeterRegistry meterRegistry,
                    AiInvocationSnapshotRecorder snapshotRecorder,
                    AiLlmTracePersistenceService tracePersistenceService) {
        this.meterRegistry = meterRegistry;
        this.snapshotRecorder = snapshotRecorder;
        this.tracePersistenceService = tracePersistenceService;
    }

    public boolean isChatConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.deepseek.api-key")
    public ChatModel chatModel() {
        var builder = OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName(modelName)
            .maxTokens(maxTokens)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .listeners(chatModelListeners());
        if (usesOpenCodeGo()) {
            builder.customHeaders(() -> openCodeHeaders(UUID.randomUUID()))
                .customParameters(openCodeParameters());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.deepseek.api-key")
    public ConversationChatModelFactory conversationChatModelFactory() {
        if (!usesOpenCodeGo()) {
            var model = streamingChatModelBuilder().build();
            return conversationId -> model;
        }
        UUID instanceId = UUID.randomUUID();
        return conversationId -> {
            UUID sessionId = UUID.nameUUIDFromBytes(
                (instanceId + ":" + conversationId).getBytes(StandardCharsets.UTF_8));
            return streamingChatModelBuilder()
                .customHeaders(openCodeHeaders(sessionId))
                .customParameters(openCodeParameters())
                .build();
        };
    }

    private OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder streamingChatModelBuilder() {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(temperature)
            .strictTools(true)
            .parallelToolCalls(false)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .listeners(chatModelListeners());
    }

    private boolean usesOpenCodeGo() {
        URI endpoint = URI.create(baseUrl);
        String path = endpoint.getPath();
        return "https".equalsIgnoreCase(endpoint.getScheme())
            && "opencode.ai".equalsIgnoreCase(endpoint.getHost())
            && (path.startsWith("/zen/go/") || path.startsWith("/inference/go/"));
    }

    private Map<String, String> openCodeHeaders(UUID sessionId) {
        return Map.of("User-Agent", "PostgradSelector/1.0", "x-opencode-session", sessionId.toString());
    }

    private Map<String, Object> openCodeParameters() {
        return "deepseek-v4.1-flash".equals(modelName) ? DISABLED_THINKING : Map.of();
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.ai.rag.embedding.api-key:}')")
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
            .baseUrl(ragEmbeddingBaseUrl)
            .apiKey(ragEmbeddingApiKey)
            .modelName(ragEmbeddingModelName)
            .timeout(Duration.ofSeconds(ragEmbeddingTimeoutSeconds))
            .build();
    }

    private List<dev.langchain4j.model.chat.listener.ChatModelListener> chatModelListeners() {
        return List.of(
            new MicrometerMetricsChatModelListener(meterRegistry),
            new AiInvocationSnapshotListener(meterRegistry, snapshotRecorder, tracePersistenceService)
        );
    }
}
