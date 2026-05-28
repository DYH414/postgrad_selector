package com.ruoyi.web.controller.postgrad;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.framework.config.RabbitMQConfig;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
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
            // 1. Read conversation and pool from Redis
            String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
            if (convJson == null) {
                redisTemplate.opsForValue().set("ai:report:" + reportId,
                    "{\"error\": \"对话已过期\"}", 7, TimeUnit.DAYS);
                return;
            }
            String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);

            // 2. Generate report via AI (with full candidate pool in prompt)
            ChatModel chatModel = QwenChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .build();

            String reportPrompt = buildReportPrompt(convJson, poolJson != null ? poolJson : "[]");
            String aiResponse = chatModel.chat(reportPrompt);
            JSONObject reportJson = JSON.parseObject(aiResponse);

            // 3. Calculate and inject matchScore from pool data (NOT from AI)
            injectMatchScores(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");

            // 4. Save to Redis (7 days) — do this first so report is visible even if DB update fails
            String resultJsonStr = reportJson.toJSONString();
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                resultJsonStr, 7, TimeUnit.DAYS);

            // 5. Update DB — best-effort, don't clobber Redis on failure
            try {
                logMapper.updateReportResult(reportId, resultJsonStr);
            } catch (Exception dbEx) {
                // DB update failure shouldn't hide the report that's already in Redis
            }

        } catch (Exception e) {
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                "{\"error\": \"" + e.getMessage() + "\"}", 7, TimeUnit.DAYS);
        }
    }

    private String buildReportPrompt(String convJson, String poolJson) {
        String poolSummary = buildPoolSummary(poolJson);
        return """
            基于用户偏好和完整候选学校列表，生成考研择校推荐报告。

            ## 完整候选学校列表（请从这里选学校）
            %s

            ## 对话历史（用户偏好参考）
            %s

            ## 要求
            1. 从上面的候选列表中选学校，不要推荐列表之外的学校
            2. programId 必须与候选列表中的 ID 一致
            3. 按冲刺/稳妥/保底三档推荐，每档 1-3 所学校

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
            """.formatted(poolSummary, convJson);
    }

    private String buildPoolSummary(String poolJson) {
        if (poolJson == null || poolJson.isEmpty() || "[]".equals(poolJson)) {
            return "（无候选学校数据）";
        }
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Map<String, Object> p : pool) {
                Object pid = p.get("programId");
                sb.append(i).append(". ID:").append(pid);
                sb.append(" | ").append(p.getOrDefault("schoolName", "?"));
                sb.append(" | ").append(p.getOrDefault("programName", ""));
                sb.append(" | ").append(p.getOrDefault("schoolTier", ""));
                sb.append(" | ").append(p.getOrDefault("city", ""));
                Object avgObj = p.get("avgAdmittedScore");
                sb.append(" | 均分:");
                if (avgObj instanceof Number) {
                    sb.append(((Number) avgObj).intValue());
                } else {
                    sb.append(avgObj);
                }
                Object gapObj = p.get("gap");
                if (gapObj instanceof Number) {
                    int gap = ((Number) gapObj).intValue();
                    sb.append(" | 差距:").append(gap > 0 ? "+" : "").append(gap);
                }
                sb.append("\n");
                i++;
            }
            return sb.toString();
        } catch (Exception e) {
            return "（候选学校数据解析失败）";
        }
    }

    private void injectMatchScores(JSONObject report, int estimatedScore, String poolJson) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
        Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue()
                : Long.parseLong(String.valueOf(idObj));
            poolMap.put(pid, p);
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
                Map<String, Object> stats = poolMap.get(pid);

                Double avg = null;
                if (stats != null) {
                    Object avgObj = stats.get("avgAdmittedScore");
                    avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : null;
                }

                if (avg != null && estimatedScore > 0) {
                    double gap = Math.abs(estimatedScore - avg);
                    double weight = "reach".equals(level) ? 0.5 : 0.3;
                    school.put("matchScore", (int) Math.max(0, 100 - gap * weight));
                } else {
                    school.put("matchScore", 50);
                }

                if (stats != null) {
                    injectStat(school, stats, "scoreLine");
                    injectStat(school, stats, "avgAdmittedScore");
                    injectStat(school, stats, "admissionLow");
                    injectStat(school, stats, "admissionHigh");
                    injectStat(school, stats, "admittedCount");
                    injectStat(school, stats, "planCount");
                    injectStat(school, stats, "retestCount");
                    injectStat(school, stats, "dataYear");
                    injectStat(school, stats, "dataCompleteness");
                    injectStat(school, stats, "sourceUrl");
                    injectStat(school, stats, "sourceOwner");
                }
            }
        }
    }

    private void injectStat(JSONObject school, Map<String, Object> stats, String key) {
        Object val = stats.get(key);
        if (val != null) {
            school.put(key, val);
        }
    }
}
