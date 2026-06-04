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
import com.ruoyi.postgrad.domain.AiRecommendationSafety;
import com.ruoyi.postgrad.domain.AiReportSupport;
import com.ruoyi.postgrad.domain.AiToolTrace;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.service.AiReportBuilder;
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
        + "## 多维择校规则（重要）\n"
        + "候选学校中的「差距」= 用户预估分 - 学校录取均分。差距只是分数安全维度，不能单独决定冲刺/稳妥/保底。\n"
        + "必须综合判断：分数差距、统考/计划招生名额、拟录取区间、数据完整度、学校层次、地区偏好、专业方向匹配。\n"
        + "如果 canBeSafe=false，禁止称为保底；即使差距很大，也只能说“分数有余量但存在明显风险/只能作稳妥或线索”。\n"
        + "招生名额极少是强风险信号：≤3 人不能作为保底；4-9 人若数据不完整、没有拟录取区间或分数优势不足，也不能作为保底。\n"
        + "当用户说「看重上岸率」时，优先找分差为正且招生规模、数据完整度也支撑的学校，而不是只按差距排序。\n"
        + "当用户说「学校层次优先」「城市/地区优先」「专业实力最重要」时，可以接受更高风险，但必须把取舍说清楚。\n"
        + "讨论学校时必须明确说出录取均分、差距、招生名额和主要风险，不要只说学校名字。\n\n"
        + "## 地区规则\n"
        + "- 目标地区为\"不限\"时：只在候选池内推荐，不主动提及候选池外的城市，快捷选项不要主动引导用户去看某个具体城市\n"
        + "- 目标地区有具体城市时：优先推荐该城市学校，其他城市只在用户主动询问时才讨论\n\n"
        + "## 候选学校摘要（每行含分数、招生、数据和保底边界）\n"
        + "%s\n\n"
        + "## 可用工具（必须使用）\n"
        + "- getProgramDetail(programId): 获取指定学校的完整录取数据（复试线、小分、招生计划、录取均分等）\n"
        + "- searchPrograms(filters): 在候选池内按城市、学校层次、分数范围等条件筛选。filters 为 JSON，如 {\"city\":\"上海\",\"tier\":\"211\",\"minScore\":290,\"maxScore\":310}\n"
        + "- comparePrograms(ids): 横向对比多所学校的详细录取数据\n"
        + "- queryDatabase(filters): 直接查询数据库中所有院校数据，不受候选池限制。filters为JSON，支持keyword(学校/专业名称)、tier(985/211/DOUBLE_FIRST/PUBLIC_REGULAR)、province(省份)、minScore(最低均分)、maxScore(最高均分)、limit(最多返回条数)。例如查\"浙江所有211\"用{\"province\":\"浙江\",\"tier\":\"211\"}，查\"计算机相关专业\"用{\"keyword\":\"计算机\"}\n\n"
        + "## 展示规则\n"
        + "回复中绝对不要出现学校的 programId 或任何数字 ID，用户只需要看到学校名称。\n\n"
        + "## 工具使用规则\n"
        + "1. 讨论具体学校时，必须先调用 getProgramDetail 获取真实数据再回复\n"
        + "2. 用户要求筛选/过滤/列清单时，必须调用 searchPrograms，不要凭摘要信息推测\n"
        + "3. 对比学校时，必须调用 comparePrograms 获取详细对比数据\n"
        + "4. 回复中引用数据时，确保数据来自工具返回结果，不要编造数字\n"
        + "5. 每次推荐学校时，必须说明该校的录取均分、差距、招生名额和关键风险（数据来自工具返回字段）\n"
        + "6. 工具返回 canBeSafe=false 时，不得把该校描述为保底或绝对稳妥，必须解释 safeBlockReason\n\n"
        + "## 对话节奏\n"
        + "第1轮: 了解最看重的维度（学校层次/专业排名/城市/上岸率）\n"
        + "第2-3轮: 如果用户最看重上岸率，用 searchPrograms(maxScore=预估分-5，例如300分用295) 找分数有余量的候选；保底倾向可用 maxScore=预估分-15。之后还必须结合招生名额、数据完整度和 canBeSafe 判断。每次只分析1-2所\n"
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

    @Autowired
    private AiReportBuilder aiReportBuilder;

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
            log.warn("[AI-Chat] Primary chat failed, retrying. userId={}, conversationId={}, message={}",
                userId, conversationId, e.getMessage(), e);
            try {
                AiRecommendationTools.setConversationId(conversationId);
                aiResponse = assistant.chat("<user_input>" + message + "</user_input>");
            } catch (Exception e2) {
                log.error("[AI-Chat] Chat fallback failed. userId={}, conversationId={}, message={}",
                    userId, conversationId, e2.getMessage(), e2);
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
            stream.beforeToolExecution(toolRequest -> {
                    AiRecommendationTools.setConversationId(conversationId);
                    // 提取工具名称，发送进度反馈给前端
                    String toolName = toolRequest != null && toolRequest.request() != null
                        ? toolRequest.request().name() : "";
                    String thinkingMsg = switch (toolName) {
                        case "getProgramDetail" -> "正在查询学校详细数据...";
                        case "searchPrograms" -> "正在搜索符合条件的学校...";
                        case "comparePrograms" -> "正在对比学校数据...";
                        case "expandCandidatePool" -> "正在扩展候选学校范围...";
                        case "queryDatabase" -> "正在查询数据库...";
                        default -> toolName.isEmpty() ? "正在分析你的问题..." : "正在调用 " + toolName + "...";
                    };
                    callback.onThinking(thinkingMsg);
                })
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
                    log.error("[AI-Chat-Stream] Stream failed. userId={}, conversationId={}, message={}",
                        userId, conversationId, error.getMessage(), error);
                    callback.onError(error);
                })
                .start();
        } catch (Exception e) {
            AiRecommendationTools.clear();
            log.error("[AI-Chat-Stream] Stream setup failed. userId={}, conversationId={}, message={}",
                userId, conversationId, e.getMessage(), e);
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
        Map<String, Object> profile = loadUserProfile(userId);

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
            ChatModel chatModel = buildChatModel();
            Map<String, Object> reportJson = aiReportBuilder.buildConversationReport(
                chatModel,
                cleanedConvJson,
                poolJson != null ? poolJson : "[]",
                estimatedScore,
                buildPreferenceProfile(profile)
            );
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

        // 3. Query broad local working pool for AI agent exploration
        List<RowMap> pool = aiCandidatePoolService.buildAgentPool(estimatedScore, regions);

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

        // 6. Store pool in Redis (TTL 1 hour). Keep old key during rollout.
        redisTemplate.opsForValue().set(
            "ai:agent:pool:" + reportId, poolJson, 1, TimeUnit.HOURS);
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
            profile.put("riskPreference", up.getRiskPreference() != null ? up.getRiskPreference() : "balanced");
            profile.put("schoolTierPreference", up.getSchoolTierPreference() != null ? up.getSchoolTierPreference() : "no_strict_requirement");
            profile.put("regionStrategy", up.getRegionStrategy() != null ? up.getRegionStrategy() : "no_strict_requirement");
        } else {
            profile.put("estimatedScore", 300);
            profile.put("undergradTier", "双非");
            profile.put("isCrossMajor", "否");
            profile.put("targetRegions", "不限");
            profile.put("riskPreference", "balanced");
            profile.put("schoolTierPreference", "no_strict_requirement");
            profile.put("regionStrategy", "no_strict_requirement");
        }
        return profile;
    }

    private Map<String, Object> buildPreferenceProfile(Map<String, Object> profile) {
        Map<String, Object> pref = new LinkedHashMap<>();
        pref.put("riskPreference", profile.getOrDefault("riskPreference", "balanced"));
        pref.put("schoolTierPreference", profile.getOrDefault("schoolTierPreference", "no_strict_requirement"));
        pref.put("regionStrategy", profile.getOrDefault("regionStrategy", "no_strict_requirement"));
        pref.put("targetRegions", profile.getOrDefault("targetRegions", "不限"));
        return pref;
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
            item.put("province", p.get("province"));
            item.put("programName", p.get("programName"));
            item.put("collegeName", p.get("collegeName"));
            item.put("degreeType", p.get("degreeType"));
            Object avgObj = p.get("avgAdmittedScore");
            int avg = 0;
            if (avgObj instanceof Number) {
                avg = ((Number) avgObj).intValue();
            }
            item.put("avgAdmittedScore", avg);
            item.put("gap", avg > 0 ? (estimatedScore - avg) : null);
            // 以下字段供报告生成时 injectFullData 使用
            item.put("scoreLine", p.get("scoreLine"));
            item.put("admissionLow", p.get("admissionLow"));
            item.put("admissionHigh", p.get("admissionHigh"));
            item.put("planCount", p.get("planCount"));
            item.put("admittedCount", p.get("admittedCount"));
            item.put("retestCount", p.get("retestCount"));
            item.put("dataYear", p.get("dataYear"));
            item.put("dataCompleteness", p.get("dataCompleteness"));
            item.put("sourceUrl", p.get("sourceUrl"));
            item.put("sourceOwner", p.get("sourceOwner"));
            Map<String, Object> guard = AiRecommendationSafety.safeEligibility(item, estimatedScore);
            item.put("quotaRisk", guard.get("quotaRisk"));
            item.put("canBeSafe", guard.get("canBeSafe"));
            if (guard.get("safeBlockReason") != null) {
                item.put("safeBlockReason", guard.get("safeBlockReason"));
            }
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
                sb.append("分（分数维度:").append(g >= 15 ? "偏安全" : g >= 5 ? "有余量" : g >= -10 ? "可冲刺" : "难度高").append("）");
            }
            sb.append(" | 招生:").append(displaySummaryValue(item.get("planCount")));
            sb.append(" | 拟录取区间:").append(displaySummaryValue(item.get("admissionLow")))
                .append("-").append(displaySummaryValue(item.get("admissionHigh")));
            sb.append(" | 完整度:").append(displaySummaryValue(item.get("dataCompleteness")));
            sb.append(" | quotaRisk:").append(displaySummaryValue(item.get("quotaRisk")));
            sb.append(" | canBeSafe:").append(displaySummaryValue(item.get("canBeSafe")));
            if (item.get("safeBlockReason") != null) {
                sb.append(" | 不可保底原因:").append(item.get("safeBlockReason"));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String displaySummaryValue(Object value) {
        if (value == null) return "-";
        String text = String.valueOf(value);
        return text.isBlank() ? "-" : text;
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

    private ChatModel buildChatModel() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        return OpenAiChatModel.builder()
            .baseUrl("https://api.deepseek.com/v1")
            .apiKey(apiKey)
            .modelName("deepseek-v4-pro")
            .build();
    }

    private OpenAiStreamingChatModel buildStreamingChatModel() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        return OpenAiStreamingChatModel.builder()
            .baseUrl("https://api.deepseek.com/v1")
            .apiKey(apiKey)
            .modelName("deepseek-v4-pro")
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
