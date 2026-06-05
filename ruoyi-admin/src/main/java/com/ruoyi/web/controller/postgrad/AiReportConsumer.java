package com.ruoyi.web.controller.postgrad;

import com.alibaba.fastjson2.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.framework.config.RabbitMQConfig;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.service.AiReportBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class AiReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiReportConsumer.class);
    private static final long REPORT_TTL_DAYS = 7L;
    private static final String PROGRESS_KEY_PREFIX = "ai:report:progress:";

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RecommendationLogMapper logMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AiReportBuilder aiReportBuilder;

    @RabbitListener(queues = RabbitMQConfig.AI_REPORT_QUEUE, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        Long reportId = ((Number) msg.get("reportId")).longValue();
        int estimatedScore = ((Number) msg.get("estimatedScore")).intValue();
        String mode = (String) msg.getOrDefault("mode", "conversation");

        try {
            if ("analyze".equals(mode)) {
                handleAnalyzeMessage(reportId, estimatedScore, msg);
            } else {
                handleConversationMessage(reportId, estimatedScore, msg);
            }
        } catch (Exception e) {
            String errorJson = "{\"status\":\"FAILED\",\"error\":\"" + safeJsonMessage(e.getMessage()) + "\"}";
            redisTemplate.opsForValue().set("ai:report:" + reportId, errorJson, REPORT_TTL_DAYS, TimeUnit.DAYS);
            try {
                logMapper.updateReportResult(reportId, errorJson);
            } catch (Exception dbEx) { /* best-effort */ }
        }
    }

    private void handleConversationMessage(Long reportId, int estimatedScore, Map<String, Object> msg) {
        String conversationId = (String) msg.get("conversationId");
        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson == null) {
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                "{\"error\": \"对话已过期\"}", REPORT_TTL_DAYS, TimeUnit.DAYS);
            return;
        }
        String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);

        ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl("https://api.deepseek.com/v1")
            .apiKey(System.getenv("DEEPSEEK_API_KEY"))
            .modelName("deepseek-v4-pro")
            .maxTokens(4096)
            .build();

        String cleanedConvJson = stripTailExchange(convJson);
        Map<String, Object> preferences = msg.get("userId") instanceof Number userId
            ? loadPreferenceProfile(userId)
            : new LinkedHashMap<>();

        updateProgress(reportId, "CALLING_AI");
        Map<String, Object> reportJson = aiReportBuilder.buildConversationReport(
            chatModel,
            cleanedConvJson,
            poolJson != null ? poolJson : "[]",
            estimatedScore,
            preferences
        );

        updateProgress(reportId, "FINALIZING");
        String resultJsonStr = JSON.toJSONString(reportJson);
        redisTemplate.opsForValue().set("ai:report:" + reportId, resultJsonStr, REPORT_TTL_DAYS, TimeUnit.DAYS);
        try {
            logMapper.updateReportResult(reportId, resultJsonStr);
        } catch (Exception dbEx) { /* best-effort */ }
    }

    @Deprecated(since = "2026-06", forRemoval = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleAnalyzeMessage(Long reportId, int estimatedScore, Map<String, Object> msg) {
        redisTemplate.opsForValue().set("ai:report:" + reportId,
            "{\"error\":\"快速推荐已迁移，请通过对话与AI讨论学校\"}", 7, TimeUnit.DAYS);
    }

    private Map<String, Object> loadProfileForAnalysis(Long userId) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("undergradTier", "双非");
        profile.put("isCrossMajor", "否");
        profile.put("targetRegions", "不限");
        profile.put("riskPreference", "balanced");
        profile.put("schoolTierPreference", "no_strict_requirement");
        profile.put("regionStrategy", "no_strict_requirement");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT estimated_score, undergrad_tier, is_cross_major, target_regions, risk_preference, " +
                "school_tier_preference, region_strategy " +
                "FROM user_profile WHERE user_id = ?", userId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                profile.put("undergradTier", row.getOrDefault("undergrad_tier", "双非"));
                profile.put("isCrossMajor", "1".equals(String.valueOf(row.getOrDefault("is_cross_major", "0"))) ? "是" : "否");
                Object regions = row.get("target_regions");
                profile.put("targetRegions", regions != null ? String.valueOf(regions) : "不限");
                profile.put("riskPreference", valueOrDefault(row.get("risk_preference"), "balanced"));
                profile.put("schoolTierPreference", valueOrDefault(row.get("school_tier_preference"), "no_strict_requirement"));
                profile.put("regionStrategy", valueOrDefault(row.get("region_strategy"), "no_strict_requirement"));
            }
        } catch (Exception e) {
            log.warn("[Report-Consumer] Failed to load profile for userId={}, using defaults", userId);
        }
        return profile;
    }

    private Map<String, Object> loadPreferenceProfile(Number userIdValue) {
        Map<String, Object> profile = userIdValue == null
            ? new LinkedHashMap<>()
            : loadProfileForAnalysis(userIdValue.longValue());
        Map<String, Object> pref = new LinkedHashMap<>();
        pref.put("riskPreference", profile.getOrDefault("riskPreference", "balanced"));
        pref.put("schoolTierPreference", profile.getOrDefault("schoolTierPreference", "no_strict_requirement"));
        pref.put("regionStrategy", profile.getOrDefault("regionStrategy", "no_strict_requirement"));
        pref.put("targetRegions", profile.getOrDefault("targetRegions", "不限"));
        return pref;
    }

    private String valueOrDefault(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    @SuppressWarnings("unchecked")
    private String stripTailExchange(String convJson) {
        try {
            List<Map<String, Object>> msgs = JSON.parseObject(convJson, List.class);
            if (msgs != null && msgs.size() >= 2) {
                Map<String, Object> last = msgs.get(msgs.size() - 1);
                Map<String, Object> prev = msgs.get(msgs.size() - 2);
                if ("user".equals(prev.get("role")) && "assistant".equals(last.get("role"))) {
                    msgs = msgs.subList(0, msgs.size() - 2);
                }
            }
            return JSON.toJSONString(msgs);
        } catch (Exception e) {
            return convJson;
        }
    }

    private void updateProgress(Long reportId, String progress) {
        try {
            redisTemplate.opsForValue().set(PROGRESS_KEY_PREFIX + reportId, progress, REPORT_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception ignored) { /* non-critical */ }
    }

    private String safeJsonMessage(String message) {
        if (message == null || message.isBlank()) return "报告生成失败";
        return message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
