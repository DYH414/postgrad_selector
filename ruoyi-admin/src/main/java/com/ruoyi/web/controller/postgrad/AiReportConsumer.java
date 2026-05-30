package com.ruoyi.web.controller.postgrad;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.framework.config.RabbitMQConfig;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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

    private static final Logger log = LoggerFactory.getLogger(AiReportConsumer.class);

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
            ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .build();

            // Strip trailing "出报告" exchange to avoid AI thinking report is already done
            String cleanedConvJson = stripTailExchange(convJson);
            String reportPrompt = buildReportPrompt(cleanedConvJson, poolJson != null ? poolJson : "[]");
            JSONObject reportJson = parseReportJson(chatModel, reportPrompt, poolJson);

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
            这不是对话。请直接输出推荐报告JSON，不要回复\"好的\"\"正在生成\"或其他确认语。

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

    private JSONObject parseReportJson(ChatModel chatModel, String reportPrompt, String poolJson) {
        String aiResponse = chatModel.chat(reportPrompt);
        log.info("[Report-Consumer] AI raw response (first 500 chars): {}",
            aiResponse != null ? aiResponse.substring(0, Math.min(500, aiResponse.length())) : "null");
        try {
            JSONObject result = JSON.parseObject(aiResponse);
            if (!result.containsKey("tiers")) {
                log.warn("[Report-Consumer] Valid JSON but missing 'tiers' — triggering retry");
                throw new IllegalArgumentException("missing tiers");
            }
            log.info("[Report-Consumer] Successfully parsed with {} tiers", result.getJSONArray("tiers").size());
            return result;
        } catch (Exception e) {
            log.warn("[Report-Consumer] Parse/validation failed: {} — retrying with fix prompt", e.getMessage());
            try {
                String fixPrompt = "你的上一次回复不是合法JSON。请只返回合法JSON，不要任何额外文字。严格按照之前要求的格式：\n\n上一次回复：\n" + aiResponse;
                String fixed = chatModel.chat(fixPrompt);
                log.info("[Report-Consumer] Fix response (first 300 chars): {}",
                    fixed != null ? fixed.substring(0, Math.min(300, fixed.length())) : "null");
                JSONObject result = JSON.parseObject(fixed);
                if (!result.containsKey("tiers")) {
                    log.error("[Report-Consumer] Fix also failed — falling back to rule-based");
                    throw new IllegalArgumentException("retry missing tiers");
                }
                log.info("[Report-Consumer] Fix succeeded");
                return result;
            } catch (Exception e2) {
                log.error("[Report-Consumer] All attempts failed, using rule-based fallback");
                return ruleBasedFallback(poolJson);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JSONObject ruleBasedFallback(String poolJson) {
        JSONObject report = new JSONObject();
        report.put("summary", "AI 报告生成失败，以下为基于分数差距自动分配的结果");

        JSONArray tiers = new JSONArray();
        JSONArray reachSchools = new JSONArray();
        JSONArray steadySchools = new JSONArray();
        JSONArray safeSchools = new JSONArray();

        if (poolJson != null && !poolJson.isEmpty() && !"[]".equals(poolJson)) {
            List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
            for (Map<String, Object> p : pool) {
                JSONObject school = new JSONObject();
                long pid = ((Number) p.get("programId")).longValue();
                school.put("programId", pid);
                school.put("schoolName", p.getOrDefault("schoolName", "?"));
                school.put("programName", p.getOrDefault("programName", ""));
                school.put("reason", "自动分配（AI 报告生成失败）");
                school.put("risk", "medium");

                JSONArray pros = new JSONArray();
                pros.add(p.getOrDefault("schoolTier", ""));
                pros.add(p.getOrDefault("city", ""));
                school.put("pros", pros);
                school.put("cons", new JSONArray());

                Object gapObj = p.get("gap");
                int gap = gapObj instanceof Number ? ((Number) gapObj).intValue() : 0;
                if (gap >= 15) {
                    safeSchools.add(school);
                } else if (gap >= 5) {
                    steadySchools.add(school);
                } else if (gap >= -10) {
                    reachSchools.add(school);
                }
                // gap < -10: skip, difficulty too high
            }
        }

        JSONObject tierReach = new JSONObject();
        tierReach.put("level", "reach");
        tierReach.put("label", "冲刺档");
        tierReach.put("schools", reachSchools);
        tiers.add(tierReach);

        JSONObject tierSteady = new JSONObject();
        tierSteady.put("level", "steady");
        tierSteady.put("label", "稳妥档");
        tierSteady.put("schools", steadySchools);
        tiers.add(tierSteady);

        JSONObject tierSafe = new JSONObject();
        tierSafe.put("level", "safe");
        tierSafe.put("label", "保底档");
        tierSafe.put("schools", safeSchools);
        tiers.add(tierSafe);

        report.put("tiers", tiers);
        return report;
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
