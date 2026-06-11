package com.ruoyi.framework.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模型统一配置 —— 替代各处散落的 buildChatModel() / buildStreamingChatModel()。
 *
 * <p>更换模型只需修改 application.yml 中 app.ai.deepseek.* 配置项。</p>
 * <p>提示词通过各 Service 的 @Value("classpath:prompts/...") 直接注入。</p>
 */
@Configuration
public class AiConfig {

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

    @Bean
    @ConditionalOnProperty(name = "app.ai.deepseek.api-key")
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName(modelName)
            .maxTokens(maxTokens)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.deepseek.api-key")
    public OpenAiStreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build();
    }
}
