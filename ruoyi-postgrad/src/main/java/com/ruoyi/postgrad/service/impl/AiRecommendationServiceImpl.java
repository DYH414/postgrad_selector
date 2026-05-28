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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

@Service
public class AiRecommendationServiceImpl implements IAiRecommendationService {

    private static final String SYSTEM_PROMPT = ""
        + "你是考研择校顾问。回复简洁（2-4句），不自我介绍，不讲客套话。每轮聚焦一个问题。\n\n"
        + "## 用户画像\n"
        + "- 预估总分: %d\n"
        + "- 本科层次: %s\n"
        + "- 跨考: %s\n"
        + "- 目标地区: %s\n\n"
        + "## 候选学校摘要\n"
        + "%s\n\n"
        + "## 可用工具（必须使用）\n"
        + "- getProgramDetail(programId): 获取指定学校的完整录取数据（复试线、小分、招生计划、录取均分等）\n"
        + "- searchPrograms(filters): 在候选池内按城市、学校层次、分数范围等条件筛选。filters 为 JSON，如 {\"city\":\"上海\",\"tier\":\"211\",\"minScore\":290,\"maxScore\":310}\n"
        + "- comparePrograms(ids): 横向对比多所学校的详细录取数据\n\n"
        + "## 工具使用规则\n"
        + "1. 讨论具体学校时，必须先调用 getProgramDetail 获取真实数据再回复\n"
        + "2. 用户要求筛选/过滤/列清单时，必须调用 searchPrograms，不要凭摘要信息推测\n"
        + "3. 对比学校时，必须调用 comparePrograms 获取详细对比数据\n"
        + "4. 回复中引用数据时，确保数据来自工具返回结果，不要编造数字\n\n"
        + "## 对话节奏\n"
        + "第1轮: 了解最看重的维度（学校层次/专业排名/城市/上岸率）\n"
        + "第2-3轮: 用具体数据讨论 2-3 所目标校（必须调工具获取数据）\n"
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

    @Autowired
    private AiRecommendationTools aiRecommendationTools;

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
            profile.getOrDefault("targetRegions", "不限"),
            summaryText);

        String conversationId = UUID.randomUUID().toString();

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        ChatModel chatModel = buildChatModel();
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

        RecommendationAssistant assistant = AiServices.builder(RecommendationAssistant.class)
            .chatModel(chatModel)
            .tools(aiRecommendationTools)
            .chatMemory(chatMemory)
            .systemMessageProvider(ignored -> systemPrompt)
            .build();

        String aiResponse;
        try {
            AiRecommendationTools.setConversationId(conversationId);
            aiResponse = assistant.chat("开始择校对话。不要自我介绍，直接询问用户最看重哪个维度（学校层次/专业实力/城市/上岸率）。");
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

        // Extract system prompt from history
        String systemPrompt = "";
        for (Map<String, Object> m : messages) {
            if ("system".equals(m.get("role"))) {
                systemPrompt = (String) m.get("content");
                break;
            }
        }
        final String finalSystemPrompt = systemPrompt;

        // Build chat memory from non-system messages
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        for (Map<String, Object> m : messages) {
            String role = (String) m.get("role");
            String content = (String) m.get("content");
            if ("assistant".equals(role)) {
                chatMemory.add(AiMessage.from(content));
            } else if ("user".equals(role)) {
                chatMemory.add(UserMessage.from(content));
            }
        }

        ChatModel chatModel = buildChatModel();
        RecommendationAssistant assistant = AiServices.builder(RecommendationAssistant.class)
            .chatModel(chatModel)
            .tools(aiRecommendationTools)
            .chatMemory(chatMemory)
            .systemMessageProvider(ignored -> finalSystemPrompt)
            .build();

        String aiResponse;
        try {
            AiRecommendationTools.setConversationId(conversationId);
            aiResponse = assistant.chat("<user_input>" + message + "</user_input>");
        } catch (Exception e) {
            try {
                AiRecommendationTools.setConversationId(conversationId);
                aiResponse = assistant.chat("<user_input>" + message + "</user_input>");
            } catch (Exception e2) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("fallback", true);
                fallback.put("message", "AI 服务暂时不可用，请稍后重试");
                fallback.put("options", Collections.emptyList());
                AiRecommendationTools.clear();
                return fallback;
            }
        } finally {
            AiRecommendationTools.clear();
        }

        // Rebuild messages from memory + system prompt for Redis persistence
        messages = new ArrayList<>();
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", finalSystemPrompt);
        messages.add(sysMsg);
        for (ChatMessage cm : chatMemory.messages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (cm instanceof AiMessage) {
                m.put("role", "assistant");
                m.put("content", ((AiMessage) cm).text());
            } else if (cm instanceof UserMessage) {
                m.put("role", "user");
                m.put("content", ((UserMessage) cm).singleText());
            }
            if (!m.isEmpty()) messages.add(m);
        }

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
            String reportPrompt = buildReportPrompt(convJson, poolJson != null ? poolJson : "[]");
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
            profile.put("targetRegions", up.getTargetRegions() != null ? up.getTargetRegions() : "不限");
        } else {
            profile.put("estimatedScore", 300);
            profile.put("undergradTier", "双非");
            profile.put("isCrossMajor", "否");
            profile.put("targetRegions", "不限");
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
                {"level": "reach", "label": "冲刺档", "schools": [{"programId":1,"schoolName":"...","programName":"...","reason":"推荐理由","risk":"high","pros":[],"cons":[]}]},
                {"level": "steady", "label": "稳妥档", "schools": []},
                {"level": "safe", "label": "保底档", "schools": []}
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
            int idx = 1;
            for (Map<String, Object> p : pool) {
                sb.append(idx).append(". ID:").append(p.get("programId"));
                sb.append(" | ").append(p.getOrDefault("schoolName", "?"));
                sb.append(" | ").append(p.getOrDefault("programName", ""));
                sb.append(" | ").append(p.getOrDefault("schoolTier", ""));
                sb.append(" | ").append(p.getOrDefault("city", ""));
                Object avgObj = p.get("avgAdmittedScore");
                sb.append(" | 均分:");
                sb.append(avgObj instanceof Number ? ((Number) avgObj).intValue() : avgObj);
                Object gapObj = p.get("gap");
                if (gapObj instanceof Number) {
                    int gap = ((Number) gapObj).intValue();
                    sb.append(" | 差距:").append(gap > 0 ? "+" : "").append(gap);
                }
                sb.append("\n");
                idx++;
            }
            return sb.toString();
        } catch (Exception e) {
            return "（候选学校数据解析失败）";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectMatchScores(Map<String, Object> report, int estimatedScore, String poolJson) {
        List<Map<String, Object>> pool = new ArrayList<>();
        for (Object item : JSON.parseArray(poolJson)) {
            pool.add((Map<String, Object>) item);
        }
        Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue()
                : Long.parseLong(String.valueOf(idObj));
            poolMap.put(pid, p);
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
                    copyIfNotNull(school, stats, "scoreLine");
                    copyIfNotNull(school, stats, "avgAdmittedScore");
                    copyIfNotNull(school, stats, "admissionLow");
                    copyIfNotNull(school, stats, "admissionHigh");
                    copyIfNotNull(school, stats, "admittedCount");
                    copyIfNotNull(school, stats, "planCount");
                    copyIfNotNull(school, stats, "retestCount");
                    copyIfNotNull(school, stats, "dataYear");
                    copyIfNotNull(school, stats, "dataCompleteness");
                    copyIfNotNull(school, stats, "sourceUrl");
                    copyIfNotNull(school, stats, "sourceOwner");
                }
            }
        }
    }

    private void copyIfNotNull(Map<String, Object> target, Map<String, Object> source, String key) {
        Object val = source.get(key);
        if (val != null) {
            target.put(key, val);
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

    /** langchain4j AiServices interface — enables real Tool invocation */
    private interface RecommendationAssistant {
        String chat(String message);
    }
}
