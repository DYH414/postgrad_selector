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

import com.ruoyi.postgrad.domain.ai.AiConstants;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class AiReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiReportConsumer.class);

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RecommendationLogMapper logMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AiReportBuilder aiReportBuilder;
    @Autowired private dev.langchain4j.model.chat.ChatModel chatModel;

    @RabbitListener(queues = RabbitMQConfig.AI_REPORT_QUEUE, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        Object reportIdObj = msg.get("reportId");
        Object scoreObj = msg.get("estimatedScore");
        if (!(reportIdObj instanceof Number) || !(scoreObj instanceof Number)) {
            log.error("[mq] Invalid message format, missing reportId or estimatedScore: {}", msg.keySet());
            return;
        }
        Long reportId = ((Number) reportIdObj).longValue();
        int estimatedScore = ((Number) scoreObj).intValue();

        try {
            handleConversationMessage(reportId, estimatedScore, msg);
        } catch (Exception e) {
            log.error("[mq] Report generation failed for reportId={}", reportId, e);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("status", "FAILED");
            errorResult.put("error", e.getMessage() != null ? e.getMessage() : "报告生成失败");
            String errorJson = JSON.toJSONString(errorResult);
            redisTemplate.opsForValue().set(AiConstants.keyReport(reportId), errorJson, AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
            try {
                logMapper.updateReportResult(reportId, errorJson);
            } catch (Exception dbEx) { log.warn("[mq] DB update failed (best-effort)", dbEx); }
        }
    }

    private void handleConversationMessage(Long reportId, int estimatedScore, Map<String, Object> msg) {
        String conversationId = (String) msg.get("conversationId");
        String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
        if (convJson == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "对话已过期");
            redisTemplate.opsForValue().set(AiConstants.keyReport(reportId), JSON.toJSONString(err),
                AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
            return;
        }
        String poolJson = redisTemplate.opsForValue().get(AiConstants.keyPool(conversationId));

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
        redisTemplate.opsForValue().set(AiConstants.keyReport(reportId), resultJsonStr, AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
        try {
            logMapper.updateReportResult(reportId, resultJsonStr);
        } catch (Exception dbEx) { log.warn("[mq] DB update failed (best-effort)", dbEx); }
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
            redisTemplate.opsForValue().set(AiConstants.keyReportProgress(reportId), progress, AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
        } catch (Exception ex) { log.warn("[mq] Progress update failed (non-critical)", ex); }
    }
}
