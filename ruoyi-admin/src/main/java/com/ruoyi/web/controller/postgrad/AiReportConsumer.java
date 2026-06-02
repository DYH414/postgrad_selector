package com.ruoyi.web.controller.postgrad;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.framework.config.RabbitMQConfig;
import com.ruoyi.postgrad.domain.AiReportSupport;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class AiReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiReportConsumer.class);
    private static final int PROMPT_POOL_ROW_LIMIT = 120;

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RecommendationLogMapper logMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

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
            redisTemplate.opsForValue().set("ai:report:" + reportId, errorJson, 7, TimeUnit.DAYS);
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
                "{\"error\": \"对话已过期\"}", 7, TimeUnit.DAYS);
            return;
        }
        String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);

        ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .modelName("qwen-max")
            .build();

        String cleanedConvJson = stripTailExchange(convJson);
        String reportPrompt = buildReportPrompt(cleanedConvJson, poolJson != null ? poolJson : "[]");
        JSONObject reportJson = parseReportJson(chatModel, reportPrompt, poolJson);

        injectFullData(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");
        normalizeReport(reportJson);

        String resultJsonStr = reportJson.toJSONString();
        redisTemplate.opsForValue().set("ai:report:" + reportId, resultJsonStr, 7, TimeUnit.DAYS);
        try {
            logMapper.updateReportResult(reportId, resultJsonStr);
        } catch (Exception dbEx) { /* best-effort */ }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleAnalyzeMessage(Long reportId, int estimatedScore, Map<String, Object> msg) {
        Long userId = ((Number) msg.get("userId")).longValue();

        // 1. Read pool from Redis
        String poolJson = redisTemplate.opsForValue().get("ai:agent:pool:" + reportId);
        if (poolJson == null) {
            poolJson = redisTemplate.opsForValue().get("ai:analyze:pool:" + reportId);
        }
        if (poolJson == null || poolJson.isEmpty()) {
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                "{\"error\": \"候选学校数据已过期，请重新发起快速推荐\"}", 7, TimeUnit.DAYS);
            return;
        }

        // 2. Load profile for prompt text
        Map<String, Object> profile = loadProfileForAnalysis(userId);

        // 3. Build prompt with full school data table
        String prompt = buildAnalysisPrompt(poolJson, estimatedScore, profile);

        // 4. Call AI
        ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .modelName("qwen-max")
            .build();

        JSONObject reportJson = parseReportJson(chatModel, prompt, poolJson);

        // 5. Inject full data from DB pool
        injectFullData(reportJson, estimatedScore, poolJson);
        normalizeReport(reportJson);

        // 6. Save to Redis + DB
        String resultJsonStr = reportJson.toJSONString();
        redisTemplate.opsForValue().set("ai:report:" + reportId, resultJsonStr, 7, TimeUnit.DAYS);
        try {
            logMapper.updateReportResult(reportId, resultJsonStr);
        } catch (Exception dbEx) { /* best-effort */ }

        // 7. Clean up analysis pool key
        redisTemplate.delete("ai:agent:pool:" + reportId);
        redisTemplate.delete("ai:analyze:pool:" + reportId);
    }

    private Map<String, Object> loadProfileForAnalysis(Long userId) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("undergradTier", "双非");
        profile.put("isCrossMajor", "否");
        profile.put("targetRegions", "不限");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT estimated_score, undergrad_tier, is_cross_major, target_regions " +
                "FROM user_profile WHERE user_id = ?", userId);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                profile.put("undergradTier", row.getOrDefault("undergrad_tier", "双非"));
                profile.put("isCrossMajor", "1".equals(String.valueOf(row.getOrDefault("is_cross_major", "0"))) ? "是" : "否");
                Object regions = row.get("target_regions");
                profile.put("targetRegions", regions != null ? String.valueOf(regions) : "不限");
            }
        } catch (Exception e) {
            log.warn("[Report-Consumer] Failed to load profile for userId={}, using defaults", userId);
        }
        return profile;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String buildAnalysisPrompt(String poolJson, int estimatedScore, Map<String, Object> profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是考研择校顾问。请基于以下用户画像和候选学校数据，直接输出一份择校推荐报告。\n\n");
        sb.append("## 用户画像\n");
        sb.append("- 预估总分: ").append(estimatedScore).append("\n");
        sb.append("- 本科层次: ").append(profile.getOrDefault("undergradTier", "双非")).append("\n");
        sb.append("- 跨考: ").append(profile.getOrDefault("isCrossMajor", "否")).append("\n");
        sb.append("- 目标地区: ").append(profile.getOrDefault("targetRegions", "不限")).append("\n\n");
        sb.append("## 推荐要求（重要）\n");
        sb.append("1. 按冲刺/稳妥/保底三档推荐，每档 1-3 所学校\n");
        sb.append("2. 学校选择需综合考虑：录取均分、差距、招生人数、报录比、复试线\n");
        sb.append("3. 差距 ≥ 5 分优先稳妥/保底档，差距 ≤ -6 分优先冲刺档\n");
        sb.append("4. 差距 > -5 且 < 5 时可归入稳妥档\n");
        sb.append("5. 不要推荐差距 < -10 分的学校（难度过高）\n");
        sb.append("6. 推荐理由必须引用具体数据（均分、招生人数等），不要只说\"分数合适\"\n");
        sb.append("7. ★ programId 必须从上面候选列表中的 \"ID:\" 值精确复制，绝对不要用序号或编造ID ★\n");
        sb.append("8. 输出学校时必须使用 judgement 枚举: safe, steady, steady_reach, small_reach, high_risk_reach, data_insufficient_pending。\n");
        sb.append("9. verificationStatus 必须是: official, third_party, local_data_only, verification_failed, pending。\n");
        sb.append("10. 不要输出 matchScore。推荐理由写入 evidence 和 risks。\n\n");
        sb.append("## 候选学校数据\n");
        sb.append("候选池共 ").append(JSON.parseArray(poolJson).size())
            .append(" 条，以下为按数据完整度和分数接近度排序后的前 ")
            .append(PROMPT_POOL_ROW_LIMIT).append(" 条代表行；只能从这些 ID 中选择。\n");
        sb.append("格式: ID | 学校 | 专业 | 层次 | 城市 | 均分 | 差距 | 复试线 | 招生 | 录取 | 报录比 | 数据年份\n\n");

        List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
        int idx = 1;
        for (Map<String, Object> p : pool) {
            if (idx > PROMPT_POOL_ROW_LIMIT) {
                break;
            }
            sb.append(idx++).append(". ID:").append(p.get("programId"));
            sb.append(" | ").append(p.getOrDefault("schoolName", "?"));
            sb.append(" | ").append(p.getOrDefault("programName", ""));
            sb.append(" | ").append(p.getOrDefault("schoolTier", ""));
            sb.append(" | ").append(p.getOrDefault("city", ""));
            sb.append(" | 均分:").append(p.getOrDefault("avgAdmittedScore", "-"));
            sb.append(" | 差距:").append(formatGap(p.get("gap")));
            sb.append(" | 复试线:").append(p.getOrDefault("scoreLine", "-"));
            sb.append(" | 招生:").append(p.getOrDefault("planCount", "-"));
            sb.append(" | 录取:").append(p.getOrDefault("admittedCount", "-"));
            sb.append(" | 报录比:").append(calcRatio(p.get("planCount"), p.get("admittedCount")));
            sb.append(" | ").append(p.getOrDefault("dataYear", "-")).append("年\n");
        }

        sb.append("\n## 输出格式（严格 JSON，字段名必须完全一致）\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"一句话总结\",\n");
        sb.append("  \"tiers\": [\n");
        sb.append("    {\n");
        sb.append("      \"level\": \"reach\",\n");
        sb.append("      \"label\": \"冲刺档\",\n");
        sb.append("      \"schools\": [\n");
        sb.append("        {\n");
        sb.append("          \"programId\": 1,\n");
        sb.append("          \"schoolName\": \"学校名\",\n");
        sb.append("          \"programName\": \"专业名\",\n");
        sb.append("          \"judgement\": \"steady\",\n");
        sb.append("          \"verificationStatus\": \"local_data_only\",\n");
        sb.append("          \"evidence\": [\"推荐依据，须引用均分、招生人数等数据\"],\n");
        sb.append("          \"risks\": [\"需要核验的风险点\"]\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    },\n");
        sb.append("    { \"level\": \"steady\", \"label\": \"稳妥档\", \"schools\": [...] },\n");
        sb.append("    { \"level\": \"safe\", \"label\": \"保底档\", \"schools\": [...] }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("注意：programId/schoolName/programName/risk/pros/cons 字段名必须用英文，schoolName 直接填学校名不要额外说明。");
        return sb.toString();
    }

    private String formatGap(Object gapObj) {
        if (gapObj instanceof Number n) {
            int g = n.intValue();
            return g > 0 ? "+" + g : String.valueOf(g);
        }
        return "-";
    }

    private String calcRatio(Object planObj, Object admittedObj) {
        if (planObj instanceof Number p && admittedObj instanceof Number a && a.doubleValue() > 0) {
            return String.format("%.1f:1", p.doubleValue() / a.doubleValue());
        }
        return "-";
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
                      "judgement": "steady",
                      "verificationStatus": "local_data_only",
                      "evidence": ["推荐依据"],
                      "risks": ["风险点"]
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
                if (i > PROMPT_POOL_ROW_LIMIT) {
                    sb.append("... 已截断，仅发送前 ").append(PROMPT_POOL_ROW_LIMIT).append(" 条代表行给模型\n");
                    break;
                }
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

    /** Strip markdown code block markers (```json ... ```) from AI response. */
    private static String stripMarkdown(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private JSONObject parseReportJson(ChatModel chatModel, String reportPrompt, String poolJson) {
        String aiResponse;
        try {
            aiResponse = stripMarkdown(chatModel.chat(reportPrompt));
        } catch (Exception e) {
            log.error("[Report-Consumer] AI call failed before response, using rule-based fallback: {}", e.getMessage());
            return ruleBasedFallback(poolJson);
        }
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
                String fixed = stripMarkdown(chatModel.chat(fixPrompt));
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectFullData(JSONObject report, int estimatedScore, String poolJson) {
        List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
        Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue()
                : Long.parseLong(String.valueOf(idObj));
            poolMap.put(pid, p);
        }

        JSONArray tiers = report.getJSONArray("tiers");
        if (tiers == null) return;
        log.info("[Report-Consumer] injectFullData: pool has {} entries", poolMap.size());
        for (int i = 0; i < tiers.size(); i++) {
            JSONObject tier = tiers.getJSONObject(i);
            JSONArray schools = tier.getJSONArray("schools");
            if (schools == null) continue;
            String level = tier.getString("level");
            for (int j = 0; j < schools.size(); j++) {
                JSONObject school = schools.getJSONObject(j);
                long pid = school.getLongValue("programId");
                Map<String, Object> stats = poolMap.get(pid);
                if (stats == null) {
                    log.warn("[Report-Consumer] injectFullData: programId {} not found in pool", pid);
                    continue;
                }

                injectStat(school, stats, "scoreLine");
                injectStat(school, stats, "avgAdmittedScore");
                injectStat(school, stats, "admissionLow");
                injectStat(school, stats, "admissionHigh");
                injectStat(school, stats, "planCount");
                injectStat(school, stats, "admittedCount");
                injectStat(school, stats, "retestCount");
                injectStat(school, stats, "dataYear");
                injectStat(school, stats, "dataCompleteness");
                injectStat(school, stats, "sourceUrl");
                injectStat(school, stats, "sourceOwner");

                // gap
                Object avgObj = stats.get("avgAdmittedScore");
                if (avgObj instanceof Number n) {
                    school.put("gap", estimatedScore - n.intValue());
                }

                // retestRatio
                Object plan = stats.get("planCount");
                Object admitted = stats.get("admittedCount");
                if (plan instanceof Number p && admitted instanceof Number a && a.doubleValue() > 0) {
                    school.put("retestRatio", String.format("%.1f:1", p.doubleValue() / a.doubleValue()));
                }

                // matchScore: positive gap = user above avg (safer), negative = user below (riskier)
                school.remove("matchScore");
            }
        }
    }

    private void normalizeReport(JSONObject report) {
        JSONArray tiers = report.getJSONArray("tiers");
        if (tiers == null) return;

        JSONObject metadata = report.getJSONObject("metadata");
        if (metadata == null) {
            metadata = new JSONObject();
        }
        metadata.putIfAbsent("verificationProvider", "local_noop");
        metadata.putIfAbsent("toolTraceIncompleteCount", 0);
        report.put("metadata", metadata);

        for (int i = 0; i < tiers.size(); i++) {
            JSONObject tier = tiers.getJSONObject(i);
            JSONArray schools = tier.getJSONArray("schools");
            if (schools == null) continue;

            List<JSONObject> normalized = new ArrayList<>();
            for (int j = 0; j < schools.size(); j++) {
                JSONObject school = schools.getJSONObject(j);
                normalizeReportSchool(school, tier.getString("level"));
                normalized.add(school);
            }
            normalized.sort((left, right) -> AiReportSupport.directionComparator().compare(left, right));

            JSONArray sorted = new JSONArray();
            sorted.addAll(normalized);
            tier.put("schools", sorted);
        }
    }

    private void normalizeReportSchool(JSONObject school, String tierLevel) {
        String judgement = AiReportSupport.normalizeJudgement(
            school.getOrDefault("judgement", inferJudgement(school, tierLevel)));
        String status = AiReportSupport.normalizeVerificationStatus(
            school.getOrDefault("verificationStatus", defaultVerificationStatus(school)));

        school.put("judgement", judgement);
        school.put("judgementLabel", AiReportSupport.judgementLabel(judgement));
        school.put("verificationStatus", status);
        school.put("recommendedAction", AiReportSupport.recommendedAction(judgement, status));
        school.put("avgScoreGap", school.getOrDefault("avgScoreGap", school.get("gap")));
        school.remove("matchScore");

        if (!school.containsKey("evidence") || school.getJSONArray("evidence") == null || school.getJSONArray("evidence").isEmpty()) {
            JSONArray evidence = new JSONArray();
            Object reason = school.get("reason");
            if (reason != null && !String.valueOf(reason).isBlank()) {
                evidence.add(reason);
            }
            JSONArray pros = school.getJSONArray("pros");
            if (pros != null) {
                evidence.addAll(pros);
            }
            school.put("evidence", evidence);
        }

        if (!school.containsKey("risks") || school.getJSONArray("risks") == null) {
            JSONArray risks = new JSONArray();
            JSONArray cons = school.getJSONArray("cons");
            if (cons != null) {
                risks.addAll(cons);
            }
            school.put("risks", risks);
        }
    }

    private String inferJudgement(JSONObject school, String tierLevel) {
        Object raw = school.getOrDefault("risk", school.get("judgement"));
        String normalized = AiReportSupport.normalizeJudgement(raw);
        if (!AiReportSupport.JUDGEMENT_DATA_INSUFFICIENT_PENDING.equals(normalized)) {
            return normalized;
        }
        if ("safe".equals(tierLevel)) return AiReportSupport.JUDGEMENT_SAFE;
        if ("steady".equals(tierLevel)) return AiReportSupport.JUDGEMENT_STEADY;
        if ("reach".equals(tierLevel)) return AiReportSupport.JUDGEMENT_HIGH_RISK_REACH;
        return AiReportSupport.JUDGEMENT_DATA_INSUFFICIENT_PENDING;
    }

    private String defaultVerificationStatus(JSONObject school) {
        if (school.get("avgAdmittedScore") != null || school.get("scoreLine") != null) {
            return AiReportSupport.STATUS_LOCAL_DATA_ONLY;
        }
        return AiReportSupport.STATUS_PENDING;
    }

    private void injectStat(JSONObject school, Map<String, Object> stats, String key) {
        Object val = stats.get(key);
        if (val != null) {
            school.put(key, val);
        }
    }

    private String safeJsonMessage(String message) {
        if (message == null || message.isBlank()) return "报告生成失败";
        return message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
