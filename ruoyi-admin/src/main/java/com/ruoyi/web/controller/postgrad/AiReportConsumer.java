package com.ruoyi.web.controller.postgrad;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.framework.config.RabbitMQConfig;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnBean(ConnectionFactory.class)
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class AiReportConsumer {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RecommendationLogMapper logMapper;

    @RabbitListener(queues = RabbitMQConfig.AI_REPORT_QUEUE, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        Long reportId = ((Number) msg.get("reportId")).longValue();
        String conversationId = (String) msg.get("conversationId");
        int estimatedScore = ((Number) msg.get("estimatedScore")).intValue();

        try {
            // 1. Read conversation from Redis
            String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
            if (convJson == null) {
                redisTemplate.opsForValue().set("ai:report:" + reportId,
                    "{\"error\": \"对话已过期\"}", 7, TimeUnit.DAYS);
                return;
            }

            // 2. Generate report via AI
            ChatModel chatModel = QwenChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .build();

            String reportPrompt = buildReportPrompt(convJson);
            String aiResponse = chatModel.chat(reportPrompt);
            JSONObject reportJson = JSON.parseObject(aiResponse);

            // 3. Calculate and inject matchScore from pool data (NOT from AI)
            String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
            injectMatchScores(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");

            // 4. Save to Redis (7 days)
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                reportJson.toJSONString(), 7, TimeUnit.DAYS);

            // 5. Update DB
            RecommendationLog log = new RecommendationLog();
            log.setId(reportId);
            log.setResultJson(reportJson.toJSONString());
            logMapper.insertRecommendationLog(log);

        } catch (Exception e) {
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                "{\"error\": \"" + e.getMessage() + "\"}", 7, TimeUnit.DAYS);
        }
    }

    private String buildReportPrompt(String convJson) {
        return """
            基于以下对话历史生成考研择校推荐报告。

            ## 对话历史
            %s

            ## 输出格式（严格 JSON）
            {
              "summary": "一句话总结",
              "tiers": [
                {
                  "level": "reach",
                  "label": "冲刺档",
                  "schools": [
                    {
                      "programId": 1,
                      "schoolName": "学校名",
                      "programName": "专业名",
                      "reason": "推荐理由",
                      "risk": "high",
                      "pros": ["优势1"],
                      "cons": ["劣势1"]
                    }
                  ]
                },
                {
                  "level": "steady",
                  "label": "稳妥档",
                  "schools": [...]
                },
                {
                  "level": "safe",
                  "label": "保底档",
                  "schools": [...]
                }
              ]
            }
            """.formatted(convJson);
    }

    private void injectMatchScores(JSONObject report, int estimatedScore, String poolJson) {
        // Parse pool data for real avgAdmittedScore values
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
        Map<Long, Double> avgScoreMap = new LinkedHashMap<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue()
                : Long.parseLong(String.valueOf(idObj));
            Object avgObj = p.get("avgAdmittedScore");
            Double avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : null;
            if (avg != null) avgScoreMap.put(pid, avg);
        }

        var tiers = report.getJSONArray("tiers");
        if (tiers == null) return;
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.getJSONObject(i);
            var schools = tier.getJSONArray("schools");
            if (schools == null) continue;
            String level = tier.getString("level");
            for (int j = 0; j < schools.size(); j++) {
                var school = schools.getJSONObject(j);
                long pid = school.getLongValue("programId");
                Double avg = avgScoreMap.get(pid);
                if (avg != null && estimatedScore > 0) {
                    double gap = Math.abs(estimatedScore - avg);
                    double weight = "reach".equals(level) ? 0.5 : 0.3;
                    int score = (int) Math.max(0, 100 - gap * weight);
                    school.put("matchScore", score);
                } else {
                    school.put("matchScore", 50);
                }
            }
        }
    }
}
