package com.ruoyi.postgrad.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.service.IAiRecommendationService;
import com.ruoyi.postgrad.tool.AiRecommendationTools;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

@Service
public class AiRecommendationServiceImpl implements IAiRecommendationService {

    private static final String SYSTEM_PROMPT = ""
        + "你是考研408计算机专业择校顾问。\n\n"
        + "## 你的角色\n"
        + "帮助考生从候选学校池中挑选最匹配的目标院校。\n"
        + "风格：数据驱动、诚实、简洁。不画饼，不说\"努力就能上\"。\n"
        + "每轮只聚焦一个维度。\n\n"
        + "## 用户画像\n"
        + "- 预估总分: %d\n"
        + "- 本科层次: %s\n"
        + "- 跨考: %s\n"
        + "- 风险偏好: %s\n"
        + "- 目标地区: %s\n"
        + "- 数学水平: %s，英语水平: %s\n\n"
        + "## 候选学校摘要\n"
        + "%s\n\n"
        + "## 可用工具\n"
        + "- getProgramDetail(programId): 获取完整录取数据\n"
        + "- searchPrograms(filters): 在候选池内筛选\n"
        + "- comparePrograms(ids): 横向对比多校\n\n"
        + "## 对话节奏\n"
        + "第1轮: 了解最看重的维度（学校层次/专业排名/城市/上岸率）\n"
        + "第2-3轮: 用具体数据讨论 2-3 所目标校\n"
        + "第4-5轮: 确认冲刺/稳妥/保底意向\n\n"
        + "## 输出格式\n"
        + "每轮回复含简短文字(2-4句)。\n"
        + "回复末尾附 2-3 个快捷选项，用 \"---OPTIONS---\" 分隔，每行一个选项。\n"
        + "用户说\"出报告\"时，只回复\"好的，正在为你生成报告...\"，不要附带选项。\n";

    private static final long TTL_SECONDS = 1800L;
    private static final long REPORT_TTL_DAYS = 7L;

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Autowired
    private RecommendationLogMapper logMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Override
    public Map<String, Object> startConversation(Long userId, Map<String, Object> request) {
        Map<String, Object> profile = loadUserProfile(userId);
        int estimatedScore = getEstimatedScore(request, profile);

        @SuppressWarnings("unchecked")
        List<Long> candidateIds = (List<Long>) request.get("candidateIds");
        if (candidateIds == null) {
            candidateIds = Collections.emptyList();
        }

        List<RowMap> pool;
        if (!candidateIds.isEmpty()) {
            pool = recommendationMapper.selectProgramsByIds(candidateIds, estimatedScore);
        } else {
            pool = Collections.emptyList();
        }

        List<Map<String, Object>> summaryList = buildSummaryList(pool, estimatedScore);
        String summaryText = buildSummaryText(summaryList);

        String systemPrompt = String.format(SYSTEM_PROMPT,
            estimatedScore,
            profile.getOrDefault("undergradTier", "双非"),
            profile.getOrDefault("isCrossMajor", "否"),
            profile.getOrDefault("riskPreference", "中等"),
            profile.getOrDefault("targetRegions", "不限"),
            profile.getOrDefault("mathLevel", "中等"),
            profile.getOrDefault("englishLevel", "中等"),
            summaryText);

        String conversationId = UUID.randomUUID().toString();

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        String fullPrompt = buildChatPrompt(messages);
        ChatModel chatModel = buildChatModel();

        String aiResponse;
        try {
            AiRecommendationTools.setConversationId(conversationId);
            aiResponse = chatModel.chat(fullPrompt);
        } finally {
            AiRecommendationTools.clear();
        }

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", aiResponse);
        messages.add(assistantMsg);

        String convJson = JSON.toJSONString(messages);
        String poolJson = JSON.toJSONString(summaryList);

        redisTemplate.opsForValue().set("ai:conv:" + conversationId, convJson, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("ai:pool:" + conversationId, poolJson, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("ai:owner:" + conversationId, userId.toString(), TTL_SECONDS, TimeUnit.SECONDS);

        String messageText = parseMessageText(aiResponse);
        List<String> options = parseOptionsList(aiResponse);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("message", messageText);
        result.put("options", options);
        return result;
    }

    @Override
    public Map<String, Object> chat(Long userId, String conversationId, String message) {
        String owner = redisTemplate.opsForValue().get("ai:owner:" + conversationId);
        if (owner == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "expired");
            err.put("message", "对话已过期，请开始新对话");
            err.put("options", Collections.emptyList());
            return err;
        }
        if (!owner.equals(userId.toString())) {
            throw new SecurityException("Conversation ownership mismatch");
        }

        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "expired");
            err.put("message", "对话已过期，请开始新对话");
            err.put("options", Collections.emptyList());
            return err;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = JSON.parseObject(convJson, List.class);

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "<user_input>" + message + "</user_input>");
        messages.add(userMsg);

        String fullPrompt = buildChatPrompt(messages);
        ChatModel chatModel = buildChatModel();

        String aiResponse;
        try {
            AiRecommendationTools.setConversationId(conversationId);
            aiResponse = chatModel.chat(fullPrompt);
        } catch (Exception e) {
            try {
                aiResponse = chatModel.chat(fullPrompt);
            } catch (Exception e2) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("fallback", true);
                fallback.put("message", "AI 服务暂时不可用，请稍后重试");
                fallback.put("options", Collections.emptyList());
                return fallback;
            }
        } finally {
            AiRecommendationTools.clear();
        }

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", aiResponse);
        messages.add(assistantMsg);

        convJson = JSON.toJSONString(messages);
        redisTemplate.opsForValue().set("ai:conv:" + conversationId, convJson, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire("ai:pool:" + conversationId, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire("ai:owner:" + conversationId, TTL_SECONDS, TimeUnit.SECONDS);

        if (messages.size() % 6 == 0) {
            saveConversationState(userId, conversationId, messages);
        }

        String messageText = parseMessageText(aiResponse);
        List<String> options = parseOptionsList(aiResponse);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", messageText);
        result.put("options", options);
        return result;
    }

    @Override
    public Map<String, Object> generateReport(Long userId, String conversationId) {
        String owner = redisTemplate.opsForValue().get("ai:owner:" + conversationId);
        if (owner == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "expired");
            err.put("message", "对话已过期");
            return err;
        }
        if (!owner.equals(userId.toString())) {
            throw new SecurityException("Conversation ownership mismatch");
        }

        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "expired");
            err.put("message", "对话已过期");
            return err;
        }

        int estimatedScore = 300;
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = JSON.parseObject(convJson, List.class);
            if (messages != null && !messages.isEmpty()) {
                Map<String, Object> sysMsg = messages.get(0);
                String content = (String) sysMsg.get("content");
                if (content != null && content.contains("预估总分:")) {
                    int start = content.indexOf("预估总分:") + 6;
                    int end = content.indexOf("\n", start);
                    if (end < 0) end = content.length();
                    try {
                        estimatedScore = Integer.parseInt(content.substring(start, end).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }

        RecommendationLog log = new RecommendationLog();
        log.setUserId(userId);
        log.setProfileSnapshot(JSON.toJSONString(Map.of("userId", userId, "conversationId", conversationId)));
        log.setResultJson("{\"status\":\"PENDING\"}");
        log.setRuleVersion("ai-conversation");
        log.setDataVersion("1.0");
        log.setIsPaid(0);
        logMapper.insertRecommendationLog(log);
        Long reportId = log.getId();

        // 异步投递 RabbitMQ；MQ 不可用时同步降级
        if (rabbitTemplate != null) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("reportId", reportId);
            msg.put("conversationId", conversationId);
            msg.put("userId", userId);
            msg.put("estimatedScore", estimatedScore);
            rabbitTemplate.convertAndSend("ai.report.queue", msg);

            redisTemplate.opsForValue().set("ai:report:" + reportId, "PENDING", REPORT_TTL_DAYS, TimeUnit.DAYS);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reportId", reportId);
            result.put("status", "PENDING");
            return result;
        }

        // 降级：同步生成
        try {
            String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
            String reportPrompt = buildReportPrompt(convJson);
            ChatModel chatModel = buildChatModel();
            String aiResponse = chatModel.chat(reportPrompt);
            Map<String, Object> reportJson = JSON.parseObject(aiResponse);
            injectMatchScores(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");
            String resultJson = JSON.toJSONString(reportJson);

            redisTemplate.opsForValue().set("ai:report:" + reportId, resultJson, REPORT_TTL_DAYS, TimeUnit.DAYS);
            log.setResultJson(resultJson);
            logMapper.insertConversationState(log.getId(), conversationId, resultJson);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reportId", reportId);
            result.put("status", "DONE");
            result.put("result", reportJson);
            return result;
        } catch (Exception e) {
            redisTemplate.opsForValue().set("ai:report:" + reportId, "{\"error\":\"" + e.getMessage() + "\"}", REPORT_TTL_DAYS, TimeUnit.DAYS);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reportId", reportId);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> getReport(Long userId, Long reportId) {
        String cached = redisTemplate.opsForValue().get("ai:report:" + reportId);
        if ("PENDING".equals(cached)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reportId", reportId);
            result.put("status", "PENDING");
            return result;
        }
        if (cached != null && !"PENDING".equals(cached)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JSON.parseObject(cached, LinkedHashMap.class);
                if (parsed == null) {
                    parsed = new LinkedHashMap<>();
                }
                parsed.put("reportId", reportId);
                parsed.put("status", "COMPLETED");
                return parsed;
            } catch (Exception e) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("reportId", reportId);
                result.put("status", "COMPLETED");
                result.put("resultJson", cached);
                return result;
            }
        }

        RowMap row = logMapper.selectLogByIdAndUserId(reportId, userId);
        if (row == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "not_found");
            err.put("message", "报告不存在");
            return err;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", row.get("id"));
        result.put("createdAt", row.get("created_at"));

        String resultJson = (String) row.get("result_json");
        if (resultJson != null && resultJson.contains("PENDING")) {
            result.put("status", "PENDING");
        } else {
            result.put("status", "COMPLETED");
            if (resultJson != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = JSON.parseObject(resultJson, LinkedHashMap.class);
                    if (parsed != null) {
                        result.putAll(parsed);
                    }
                } catch (Exception ignored) {
                    result.put("resultJson", resultJson);
                }
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> getReports(Long userId) {
        List<RowMap> reports = logMapper.selectAiReportListByUserId(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", reports != null ? reports : Collections.emptyList());
        return result;
    }

    @Override
    public Map<String, Object> resumeConversation(Long userId, String conversationId) {
        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson != null) {
            String convOwner = redisTemplate.opsForValue().get("ai:owner:" + conversationId);
            if (convOwner != null && !convOwner.equals(userId.toString())) {
                throw new SecurityException("Conversation ownership mismatch");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = JSON.parseObject(convJson, List.class);
            String lastMessage = "";
            List<String> options = Collections.emptyList();
            if (messages != null && !messages.isEmpty()) {
                Map<String, Object> lastMsg = messages.get(messages.size() - 1);
                if ("assistant".equals(lastMsg.get("role"))) {
                    String content = (String) lastMsg.get("content");
                    lastMessage = parseMessageText(content);
                    options = parseOptionsList(content);
                }
            }

            redisTemplate.expire("ai:conv:" + conversationId, TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.expire("ai:pool:" + conversationId, TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.expire("ai:owner:" + conversationId, TTL_SECONDS, TimeUnit.SECONDS);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversationId", conversationId);
            result.put("message", lastMessage);
            result.put("options", options);
            result.put("source", "redis");
            return result;
        }

        String dbState = logMapper.selectConversationState(conversationId);
        if (dbState != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> state = JSON.parseObject(dbState, LinkedHashMap.class);
                List<String> options = Collections.emptyList();
                if (state != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> savedMessages = (List<Map<String, Object>>) state.get("messages");
                    if (savedMessages != null && !savedMessages.isEmpty()) {
                        Map<String, Object> lastMsg = savedMessages.get(savedMessages.size() - 1);
                        if ("assistant".equals(lastMsg.get("role"))) {
                            String content = (String) lastMsg.get("content");
                            options = parseOptionsList(content);
                        }
                    }
                }

                redisTemplate.opsForValue().set("ai:conv:" + conversationId, dbState, TTL_SECONDS, TimeUnit.SECONDS);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("conversationId", conversationId);
                result.put("message", "已恢复上次对话");
                result.put("options", options);
                result.put("source", "db");
                return result;
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "expired");
                err.put("message", "对话已过期，请开始新对话");
                return err;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "expired");
        result.put("message", "对话已过期，请开始新对话");
        return result;
    }

    private Map<String, Object> loadUserProfile(Long userId) {
        Map<String, Object> profile = new LinkedHashMap<>();
        UserProfile up = userProfileMapper.selectUserProfileByUserId(userId);
        if (up != null) {
            profile.put("estimatedScore", up.getEstimatedScore() != null ? up.getEstimatedScore() : 300);
            profile.put("undergradTier", up.getUndergradTier() != null ? up.getUndergradTier() : "双非");
            profile.put("isCrossMajor", (up.getIsCrossMajor() != null && up.getIsCrossMajor() == 1) ? "是" : "否");
            profile.put("riskPreference", up.getRiskPreference() != null ? up.getRiskPreference() : "中等");
            profile.put("targetRegions", up.getTargetRegions() != null ? up.getTargetRegions() : "不限");
            profile.put("mathLevel", up.getMathLevel() != null ? up.getMathLevel() : "中等");
            profile.put("englishLevel", up.getEnglishLevel() != null ? up.getEnglishLevel() : "中等");
        } else {
            profile.put("estimatedScore", 300);
            profile.put("undergradTier", "双非");
            profile.put("isCrossMajor", "否");
            profile.put("riskPreference", "中等");
            profile.put("targetRegions", "不限");
            profile.put("mathLevel", "中等");
            profile.put("englishLevel", "中等");
        }
        return profile;
    }

    private int getEstimatedScore(Map<String, Object> request, Map<String, Object> profile) {
        if (request != null && request.containsKey("estimatedScore")) {
            Object scoreObj = request.get("estimatedScore");
            if (scoreObj instanceof Number) {
                return ((Number) scoreObj).intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(scoreObj));
            } catch (NumberFormatException ignored) {
            }
        }
        Object profileScore = profile.get("estimatedScore");
        if (profileScore instanceof Number) {
            return ((Number) profileScore).intValue();
        }
        return 300;
    }

    private List<Map<String, Object>> buildSummaryList(List<RowMap> pool, int estimatedScore) {
        List<Map<String, Object>> summary = new ArrayList<>();
        if (pool == null) {
            return summary;
        }
        for (RowMap p : pool) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("programId", p.get("programId"));
            item.put("schoolName", p.get("schoolName"));
            item.put("schoolTier", p.get("schoolTier"));
            item.put("city", p.get("city"));
            item.put("programName", p.get("programName"));
            Object avgObj = p.get("avgAdmittedScore");
            int avg = 0;
            if (avgObj instanceof Number) {
                avg = ((Number) avgObj).intValue();
            }
            item.put("avgAdmittedScore", avg);
            item.put("gap", avg > 0 ? (estimatedScore - avg) : null);
            item.put("collegeName", p.get("collegeName"));
            item.put("degreeType", p.get("degreeType"));
            summary.add(item);
        }
        return summary;
    }

    private String buildSummaryText(List<Map<String, Object>> summaryList) {
        if (summaryList == null || summaryList.isEmpty()) {
            return "（无候选学校）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < summaryList.size(); i++) {
            Map<String, Object> item = summaryList.get(i);
            sb.append(i + 1).append(". ID:").append(item.get("programId"));
            sb.append(" | ").append(item.get("schoolName"));
            sb.append(" | ").append(item.get("programName"));
            sb.append(" | ").append(item.get("schoolTier"));
            sb.append(" | ").append(item.get("city"));
            sb.append(" | 均分:").append(item.get("avgAdmittedScore"));
            Object gap = item.get("gap");
            if (gap != null) {
                sb.append(" | 差距:").append(gap);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildChatPrompt(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            sb.append(role).append(": ").append(content).append("\n\n");
        }
        return sb.toString();
    }

    private String parseMessageText(String content) {
        if (content == null) {
            return "";
        }
        int idx = content.indexOf("---OPTIONS---");
        if (idx >= 0) {
            return content.substring(0, idx).trim();
        }
        return content.trim();
    }

    private List<String> parseOptionsList(String content) {
        if (content == null) {
            return Collections.emptyList();
        }
        int idx = content.indexOf("---OPTIONS---");
        if (idx < 0) {
            return Collections.emptyList();
        }
        String optionsSection = content.substring(idx + "---OPTIONS---".length());
        List<String> options = new ArrayList<>();
        for (String line : optionsSection.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                options.add(trimmed);
            }
        }
        return options;
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
                {"level": "reach", "label": "冲刺档", "schools": [{"programId":1,"schoolName":"...","programName":"...","reason":"推荐理由","risk":"high","pros":[],"cons":[]}]},
                {"level": "steady", "label": "稳妥档", "schools": []},
                {"level": "safe", "label": "保底档", "schools": []}
              ]
            }
            """.formatted(convJson);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectMatchScores(Map<String, Object> report, int estimatedScore, String poolJson) {
        List<Map<String, Object>> pool = new ArrayList<>();
        for (Object item : JSON.parseArray(poolJson)) {
            pool.add((Map<String, Object>) item);
        }
        Map<Long, Double> avgScoreMap = new LinkedHashMap<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue()
                : Long.parseLong(String.valueOf(idObj));
            Object avgObj = p.get("avgAdmittedScore");
            Double avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : null;
            if (avg != null) avgScoreMap.put(pid, avg);
        }

        List<Map<String, Object>> tiers = (List<Map<String, Object>>) report.get("tiers");
        if (tiers == null) return;
        for (Map<String, Object> tier : tiers) {
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
            if (schools == null) continue;
            String level = (String) tier.getOrDefault("level", "steady");
            for (Map<String, Object> school : schools) {
                Object pidObj = school.get("programId");
                long pid = pidObj instanceof Number ? ((Number) pidObj).longValue()
                    : Long.parseLong(String.valueOf(pidObj));
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

    private ChatModel buildChatModel() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        return QwenChatModel.builder()
            .apiKey(apiKey)
            .modelName("qwen-plus")
            .build();
    }

    private void saveConversationState(Long userId, String conversationId, List<Map<String, Object>> messages) {
        try {
            RecommendationLog log = new RecommendationLog();
            log.setUserId(userId);
            log.setProfileSnapshot(JSON.toJSONString(Map.of("userId", userId)));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("conversationId", conversationId);
            state.put("messages", messages);
            state.put("savedAt", System.currentTimeMillis());
            log.setResultJson(JSON.toJSONString(state));
            log.setRuleVersion("ai-conversation-state");
            log.setDataVersion("1.0");
            log.setIsPaid(0);
            logMapper.insertRecommendationLog(log);
        } catch (Exception ignored) {
        }
    }
}
