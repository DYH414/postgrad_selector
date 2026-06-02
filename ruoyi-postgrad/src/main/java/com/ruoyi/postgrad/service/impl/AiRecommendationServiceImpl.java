package com.ruoyi.postgrad.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.ruoyi.postgrad.domain.AiReportSupport;
import com.ruoyi.postgrad.domain.AiToolTrace;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.service.IAiCandidatePoolService;
import com.ruoyi.postgrad.service.IAiRecommendationService;
import com.ruoyi.postgrad.tool.AiRecommendationTools;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;

@Service
public class AiRecommendationServiceImpl implements IAiRecommendationService {

    private static final String SYSTEM_PROMPT = ""
        + "你是独立的 AI 择校顾问。当前对话主要依据用户画像和系统自动候选池，不依赖筛选页或对比页的临时条件。回复简洁（2-4句），不自我介绍，不讲客套话。每轮聚焦一个问题。\n\n"
        + "## 用户画像\n"
        + "- 预估总分: %d\n"
        + "- 本科层次: %s\n"
        + "- 跨考: %s\n"
        + "- 目标地区: %s\n\n"
        + "## 分数差距与上岸率规则（重要）\n"
        + "候选学校中的「差距」= 用户预估分 - 学校录取均分。正数越大上岸率越高。\n"
        + "| 差距 | 分类 | 上岸率 |\n"
        + "| ≥ +15 | 保底 | 高，推荐给看重上岸率的用户 |\n"
        + "| +5 ~ +14 | 稳妥 | 中高 |\n"
        + "| -10 ~ +4 | 可冲刺 | 中等，需努力 |\n"
        + "| < -10 | 难度高 | 低，风险大 |\n"
        + "当用户说「看重上岸率」时，必须优先推荐差距 ≥ +5 的学校（稳妥/保底档）。\n"
        + "讨论学校时必须明确说出其录取均分和差距，不要只说学校名字。\n\n"
        + "## 地区规则\n"
        + "- 目标地区为\"不限\"时：只在候选池内推荐，不主动提及候选池外的城市，快捷选项不要主动引导用户去看某个具体城市\n"
        + "- 目标地区有具体城市时：优先推荐该城市学校，其他城市只在用户主动询问时才讨论\n\n"
        + "## 候选学校摘要（每行含均分和差距，差距越大上岸率越高）\n"
        + "%s\n\n"
        + "## 可用工具（必须使用）\n"
        + "- getProgramDetail(programId): 获取指定学校的完整录取数据（复试线、小分、招生计划、录取均分等）\n"
        + "- searchPrograms(filters): 在候选池内按城市、学校层次、分数范围等条件筛选。filters 为 JSON，如 {\"city\":\"上海\",\"tier\":\"211\",\"minScore\":290,\"maxScore\":310}\n"
        + "- comparePrograms(ids): 横向对比多所学校的详细录取数据\n\n"
        + "## 展示规则\n"
        + "回复中绝对不要出现学校的 programId 或任何数字 ID，用户只需要看到学校名称。\n\n"
        + "## 工具使用规则\n"
        + "1. 讨论具体学校时，必须先调用 getProgramDetail 获取真实数据再回复\n"
        + "2. 用户要求筛选/过滤/列清单时，必须调用 searchPrograms，不要凭摘要信息推测\n"
        + "3. 对比学校时，必须调用 comparePrograms 获取详细对比数据\n"
        + "4. 回复中引用数据时，确保数据来自工具返回结果，不要编造数字\n"
        + "5. 每次推荐学校时，必须说明该校的录取均分和差距（数据来自工具返回的 avgAdmittedScore 和 gap 字段）\n\n"
        + "## 对话节奏\n"
        + "第1轮: 了解最看重的维度（学校层次/专业排名/城市/上岸率）\n"
        + "第2-3轮: 如果用户最看重上岸率，用 searchPrograms(maxScore=预估分+5) 筛稳妥/保底校；如果用户愿意冲刺，用 searchPrograms(minScore=预估分-10) 筛冲刺校。每次只分析1-2所\n"
        + "第4-5轮: 确认冲刺/稳妥/保底意向\n\n"
        + "## 输出格式\n"
        + "每轮回复含简短文字(2-4句)。\n"
        + "回复末尾附 2-3 个快捷选项，用 \"---OPTIONS---\" 分隔，每行一个选项。\n"
        + "## 快捷选项规则（重要）\n"
        + "快捷选项必须是用户偏好/决策类，如\"看重上岸率\"\"愿意冲刺\"\"稳妥为主\"\"优先211\"\"城市/地区优先\"。\n"
        + "不要在用户明确选择城市维度前，生成\"限定某城市\"这类具体城市选项。\n"
        + "选项应顺着你的分析结论往前推进，不要重复已讨论过的内容或给出与分析矛盾的选择。\n"
        + "好的选项示例: \"确认XX为稳妥目标\" \"再看看保底选择\" \"换一个城市看看\"\n"
        + "禁止将工具调用作为快捷选项。以下选项禁止出现：\n"
        + "- \"查看XX学校详细数据\" \"查看XX专业分数线\" \"对比XX和XX\" — 这些都是工具调用，由你自动完成\n"
        + "- \"🔍\" \"📊\" 等带工具图标的选项\n"
        + "用户说\"出报告\"时，只回复\"好的，正在为你生成报告...\"，不要附带选项。\n";

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationServiceImpl.class);
    private static final long TTL_SECONDS = 1800L;
    private static final long REPORT_TTL_DAYS = 7L;

    @Autowired
    private IAiCandidatePoolService aiCandidatePoolService;

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

        List<RowMap> pool = aiCandidatePoolService.buildPool(request, profile, estimatedScore);

        List<Map<String, Object>> summaryList = buildSummaryList(pool, estimatedScore);
        String summaryText = buildSummaryText(summaryList);

        String systemPrompt = String.format(SYSTEM_PROMPT,
            estimatedScore,
            formatProfileField(profile, "undergradTier", "双非"),
            formatProfileField(profile, "isCrossMajor", "否"),
            formatProfileField(profile, "targetRegions", "不限"),
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
        List<String> options = initialPreferenceOptions();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("message", messageText);
        result.put("options", options);
        result.put("profileBasis", buildProfileBasis(profile, estimatedScore));
        result.put("candidateCount", summaryList.size());
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
    public void chatStream(Long userId, String conversationId, String message, StreamCallback callback) {
        String owner = redisTemplate.opsForValue().get("ai:owner:" + conversationId);
        if (owner == null) {
            callback.onError(new IllegalArgumentException("对话已过期，请开始新对话"));
            return;
        }
        if (!owner.equals(userId.toString())) {
            throw new SecurityException("Conversation ownership mismatch");
        }

        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson == null) {
            callback.onError(new IllegalArgumentException("对话已过期，请开始新对话"));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = JSON.parseObject(convJson, List.class);

        String systemPrompt = "";
        for (Map<String, Object> m : messages) {
            if ("system".equals(m.get("role"))) {
                systemPrompt = (String) m.get("content");
                break;
            }
        }
        final String finalSystemPrompt = systemPrompt;

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

        StreamRecommendationAssistant assistant = AiServices.builder(StreamRecommendationAssistant.class)
            .streamingChatModel(buildStreamingChatModel())
            .tools(aiRecommendationTools)
            .chatMemory(chatMemory)
            .systemMessageProvider(ignored -> finalSystemPrompt)
            .build();

        StringBuilder fullResponse = new StringBuilder();
        try {
            AiRecommendationTools.setConversationId(conversationId);
            TokenStream stream = assistant.chat("<user_input>" + message + "</user_input>");
            stream.beforeToolExecution(ignored -> AiRecommendationTools.setConversationId(conversationId))
                .onPartialResponse(token -> {
                    fullResponse.append(token);
                    callback.onToken(token);
                })
                .onCompleteResponse(response -> {
                    AiRecommendationTools.clear();
                    persistStreamConversation(userId, conversationId, finalSystemPrompt, chatMemory);
                    String rawText = response != null && response.aiMessage() != null && response.aiMessage().text() != null
                        ? response.aiMessage().text()
                        : fullResponse.toString();
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("message", parseMessageText(rawText));
                    result.put("options", parseOptionsList(rawText));
                    callback.onComplete(result);
                })
                .onError(error -> {
                    AiRecommendationTools.clear();
                    callback.onError(error);
                })
                .start();
        } catch (Exception e) {
            AiRecommendationTools.clear();
            callback.onError(e);
        }
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
            // 裁剪掉最后两轮（"出报告" + "好的，正在为你生成报告..."）
            // 避免 AI 看到这段后误以为报告已经生成完毕
            String cleanedConvJson = stripTailExchange(convJson);
            String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
            String reportPrompt = buildReportPrompt(cleanedConvJson, poolJson != null ? poolJson : "[]");
            ChatModel chatModel = buildChatModel();
            Map<String, Object> reportJson = parseReportJson(chatModel, reportPrompt,
                poolJson != null ? poolJson : "[]");
            injectMatchScores(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");
            Map<String, Object> validated = validateAndNormalizeReport(reportJson, AiRecommendationTools.currentTrace());
            String resultJson = JSON.toJSONString(validated);

            redisTemplate.opsForValue().set("ai:report:" + reportId, resultJson, REPORT_TTL_DAYS, TimeUnit.DAYS);
            log.setResultJson(resultJson);
            logMapper.insertConversationState(log.getId(), conversationId, resultJson);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reportId", reportId);
            result.put("status", "DONE");
            result.put("result", validated);
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
    public Map<String, Object> analyze(Long userId)
    {
        // 1. Load user profile
        Map<String, Object> profile = loadUserProfile(userId);
        int estimatedScore = getEstimatedScore(Collections.emptyMap(), profile);
        String targetRegionsStr = formatProfileField(profile, "targetRegions", "不限");

        // 2. Parse regions from profile
        List<String> regions = parseRegionsForAnalysis(targetRegionsStr);

        // 3. Query and stratify schools
        List<RowMap> pool = aiCandidatePoolService.buildAnalysisPool(estimatedScore, regions);

        // 4. Serialize pool data for Redis (full fields needed for injectFullData)
        List<Map<String, Object>> poolList = new ArrayList<>();
        for (RowMap row : pool)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("programId", row.get("programId"));
            item.put("schoolName", row.get("schoolName"));
            item.put("schoolTier", row.get("schoolTier"));
            item.put("city", row.get("city"));
            item.put("province", row.get("province"));
            item.put("collegeName", row.get("collegeName"));
            item.put("programName", row.get("programName"));
            item.put("degreeType", row.get("degreeType"));
            item.put("scoreLine", row.get("scoreLine"));
            item.put("avgAdmittedScore", row.get("avgAdmittedScore"));
            item.put("admissionLow", row.get("admissionLow"));
            item.put("admissionHigh", row.get("admissionHigh"));
            item.put("planCount", row.get("planCount"));
            item.put("admittedCount", row.get("admittedCount"));
            item.put("retestCount", row.get("retestCount"));
            item.put("dataYear", row.get("dataYear"));
            item.put("dataCompleteness", row.get("dataCompleteness"));
            item.put("sourceUrl", row.get("sourceUrl"));
            item.put("sourceOwner", row.get("sourceOwner"));
            Object avgObj = row.get("avgAdmittedScore");
            item.put("gap", avgObj instanceof Number n ? estimatedScore - n.intValue() : 0);
            poolList.add(item);
        }
        String poolJson = JSON.toJSONString(poolList);

        // 5. Insert PENDING recommendation_log
        RecommendationLog log = new RecommendationLog();
        log.setUserId(userId);
        log.setProfileSnapshot(JSON.toJSONString(Map.of(
            "userId", userId,
            "estimatedScore", estimatedScore,
            "targetRegions", targetRegionsStr
        )));
        log.setResultJson("{\"status\":\"PENDING\"}");
        log.setIsPaid(0);
        logMapper.insertRecommendationLog(log);
        long reportId = log.getId();

        // 6. Store pool in Redis (TTL 1 hour)
        redisTemplate.opsForValue().set(
            "ai:analyze:pool:" + reportId, poolJson, 1, TimeUnit.HOURS);

        // 7. Send MQ message (lightweight: no prompt in message)
        if (rabbitTemplate != null)
        {
            Map<String, Object> mqMsg = new LinkedHashMap<>();
            mqMsg.put("reportId", reportId);
            mqMsg.put("estimatedScore", estimatedScore);
            mqMsg.put("userId", userId);
            mqMsg.put("mode", "analyze");
            rabbitTemplate.convertAndSend("ai.report.queue", mqMsg);
        }

        // 8. Return reportId
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("msg", "报告生成中，请稍候");
        return result;
    }

    private List<String> parseRegionsForAnalysis(String targetRegions)
    {
        if (targetRegions == null || targetRegions.isEmpty() || "不限".equals(targetRegions))
            return Collections.emptyList();
        try
        {
            return JSON.parseArray(targetRegions, String.class);
        }
        catch (Exception e)
        {
            return Collections.emptyList();
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

    private String formatProfileField(Map<String, Object> profile, String key, String defaultText) {
        Object val = profile.getOrDefault(key, defaultText);
        if (val == null) return defaultText;
        String s = val.toString();
        if (s.isBlank() || "[]".equals(s) || "null".equals(s)) return defaultText;
        // Parse JSON array like ["福建","上海"] → "福建、上海"
        if (s.startsWith("[") && s.endsWith("]")) {
            try {
                List<String> items = JSON.parseArray(s, String.class);
                if (items == null || items.isEmpty()) return defaultText;
                return String.join("、", items);
            } catch (Exception e) {
                return s;
            }
        }
        return s;
    }

    private Map<String, Object> buildProfileBasis(Map<String, Object> profile, int estimatedScore) {
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("estimatedScore", estimatedScore);
        basis.put("targetRegions", formatProfileField(profile, "targetRegions", "不限"));
        basis.put("undergradTier", formatProfileField(profile, "undergradTier", "双非"));
        basis.put("isCrossMajor", formatProfileField(profile, "isCrossMajor", "否"));
        basis.put("candidateScope", "系统按画像自动选择最多 50 个具备录取数据的 408 项目作为 AI 初始候选池");
        return basis;
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
            if (gap instanceof Number num) {
                int g = num.intValue();
                sb.append(" | 差距:").append(g > 0 ? "+" : "").append(g);
                sb.append("分（").append(g >= 15 ? "保底" : g >= 5 ? "稳妥" : g >= -10 ? "可冲刺" : "难度高").append("）");
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

    private static List<String> initialPreferenceOptions() {
        return List.of("看重上岸率", "学校层次优先", "专业实力最重要", "城市/地区优先");
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
                    int g = estimatedScore - (int) Math.round(avg);
                    int score;
                    if (g >= 0) {
                        score = (int) Math.min(98, 75 + g * 1.5);
                    } else {
                        score = (int) Math.max(15, 75 + g * 4);
                    }
                    school.put("matchScore", score);
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

    private Map<String, Object> normalizeReportItem(Map<String, Object> item) {
        Map<String, Object> normalized = new LinkedHashMap<>(item);
        String judgement = AiReportSupport.normalizeJudgement(
            normalized.getOrDefault("judgement", normalized.get("aiJudgement")));
        String status = AiReportSupport.normalizeVerificationStatus(normalized.get("verificationStatus"));
        normalized.put("judgement", judgement);
        normalized.put("judgementLabel", AiReportSupport.judgementLabel(judgement));
        normalized.put("verificationStatus", status);
        normalized.putIfAbsent("verificationProvider", null);
        normalized.put("recommendedAction", AiReportSupport.recommendedAction(judgement, status));
        return normalized;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> validateAndNormalizeReport(Map<String, Object> report, AiToolTrace trace) {
        Map<String, Object> result = new LinkedHashMap<>(report);
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) result.get("tiers");
        int removed = 0;
        boolean enforceTrace = trace != null && !trace.getCalls().isEmpty();
        if (tiers != null) {
            for (Map<String, Object> tier : tiers) {
                List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
                if (schools == null) continue;
                List<Map<String, Object>> kept = new ArrayList<>();
                for (Map<String, Object> school : schools) {
                    Object pidObj = school.get("programId");
                    long pid = pidObj instanceof Number n ? n.longValue() : -1L;
                    if (enforceTrace && pid > 0 && !trace.hasDetail(pid)) {
                        removed++;
                        continue;
                    }
                    kept.add(normalizeReportItem(school));
                }
                kept.sort(AiReportSupport.directionComparator());
                tier.put("schools", kept);
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("toolTraceIncompleteCount", removed);
        meta.put("explorationLimited", trace != null && trace.isExplorationLimited());
        if (trace != null) {
            trace.setRemovedIncompleteCount(removed);
        }
        result.put("metadata", meta);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> parseReportJson(ChatModel chatModel, String reportPrompt, String poolJson) {
        String aiResponse = stripMarkdown(chatModel.chat(reportPrompt));
        log.info("[Report] AI raw response (first 500 chars): {}",
            aiResponse != null ? aiResponse.substring(0, Math.min(500, aiResponse.length())) : "null");
        try {
            Map<String, Object> result = JSON.parseObject(aiResponse);
            if (!result.containsKey("tiers")) {
                log.warn("[Report] Valid JSON but missing 'tiers' — triggering retry");
                throw new IllegalArgumentException("missing tiers");
            }
            log.info("[Report] Successfully parsed with {} tiers",
                ((List<?>) result.get("tiers")).size());
            return result;
        } catch (Exception e) {
            log.warn("[Report] Parse/validation failed: {} — retrying with fix prompt", e.getMessage());
            try {
                String fixPrompt = "你的上一次回复不是合法JSON。请只返回合法JSON，不要任何额外文字。严格按照以下格式：\n{\"summary\":\"...\",\"tiers\":[{\"level\":\"reach\",\"label\":\"冲刺档\",\"schools\":[...]},...]}。\n\n上一次回复：\n" + aiResponse;
                String fixed = stripMarkdown(chatModel.chat(fixPrompt));
                log.info("[Report] Fix response (first 300 chars): {}",
                    fixed != null ? fixed.substring(0, Math.min(300, fixed.length())) : "null");
                Map<String, Object> result = JSON.parseObject(fixed);
                if (!result.containsKey("tiers")) {
                    log.error("[Report] Fix also failed — falling back to rule-based");
                    throw new IllegalArgumentException("retry missing tiers");
                }
                log.info("[Report] Fix succeeded");
                return result;
            } catch (Exception e2) {
                log.error("[Report] All attempts failed, using rule-based fallback");
                return ruleBasedFallback(poolJson);
            }
        }
    }

    /** Strip markdown code block markers (```json ... ```) from AI response. */
    private static String stripMarkdown(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> ruleBasedFallback(String poolJson) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", "AI 报告生成失败，以下为基于分数差距自动分配的结果");

        List<Map<String, Object>> reachList = new ArrayList<>();
        List<Map<String, Object>> steadyList = new ArrayList<>();
        List<Map<String, Object>> safeList = new ArrayList<>();

        if (poolJson != null && !poolJson.isEmpty() && !"[]".equals(poolJson)) {
            List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
            for (Map<String, Object> p : pool) {
                Map<String, Object> school = new LinkedHashMap<>();
                school.put("programId", p.get("programId"));
                school.put("schoolName", p.getOrDefault("schoolName", "?"));
                school.put("programName", p.getOrDefault("programName", ""));
                school.put("reason", "自动分配（AI 报告生成失败）");
                school.put("risk", "medium");
                school.put("pros", Arrays.asList(p.getOrDefault("schoolTier",""), p.getOrDefault("city","")));
                school.put("cons", Collections.emptyList());

                Object gapObj = p.get("gap");
                int gap = gapObj instanceof Number ? ((Number) gapObj).intValue() : 0;
                if (gap >= 15) {
                    safeList.add(school);
                } else if (gap >= 5) {
                    steadyList.add(school);
                } else if (gap >= -10) {
                    reachList.add(school);
                }
                // gap < -10: skip, difficulty too high
            }
        }

        Map<String, Object> tierReach = new LinkedHashMap<>();
        tierReach.put("level", "reach");
        tierReach.put("label", "冲刺档");
        tierReach.put("schools", reachList);
        Map<String, Object> tierSteady = new LinkedHashMap<>();
        tierSteady.put("level", "steady");
        tierSteady.put("label", "稳妥档");
        tierSteady.put("schools", steadyList);
        Map<String, Object> tierSafe = new LinkedHashMap<>();
        tierSafe.put("level", "safe");
        tierSafe.put("label", "保底档");
        tierSafe.put("schools", safeList);

        report.put("tiers", Arrays.asList(tierReach, tierSteady, tierSafe));
        return report;
    }

    /** 裁剪对话最后两轮（用户"出报告" + AI"好的..."），避免 AI 误以为报告已生成 */
    @SuppressWarnings("unchecked")
    private String stripTailExchange(String convJson) {
        try {
            List<Map<String, Object>> msgs = JSON.parseObject(convJson, List.class);
            if (msgs != null && msgs.size() >= 2) {
                // 移除最后一条 user 消息和最后一条 assistant 消息
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

    private void copyIfNotNull(Map<String, Object> target, Map<String, Object> source, String key) {
        Object val = source.get(key);
        if (val != null) {
            target.put(key, val);
        }
    }

    private ChatModel buildChatModel() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        return OpenAiChatModel.builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .apiKey(apiKey)
            .modelName("qwen-max")
            .build();
    }

    private OpenAiStreamingChatModel buildStreamingChatModel() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        return OpenAiStreamingChatModel.builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .apiKey(apiKey)
            .modelName("qwen-max")
            .build();
    }

    private void persistStreamConversation(Long userId, String conversationId, String systemPrompt, ChatMemory chatMemory) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
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

        redisTemplate.opsForValue().set("ai:conv:" + conversationId, JSON.toJSONString(messages), TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire("ai:pool:" + conversationId, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.expire("ai:owner:" + conversationId, TTL_SECONDS, TimeUnit.SECONDS);

        if (messages.size() % 6 == 0) {
            saveConversationState(userId, conversationId, messages);
        }
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

    /** Streaming variant of the AI service; keeps the same prompt and tools. */
    private interface StreamRecommendationAssistant {
        TokenStream chat(String message);
    }
}
