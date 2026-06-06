package com.ruoyi.postgrad.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.AiBookmark;
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
        + "你是独立的 AI 择校顾问。职责：在事实约束下诚实给出择校建议，不迎合用户不合理要求。\n"
        + "如果候选池不支持用户想要的档位/地区，必须诚实说明没有严格匹配项，不凑数。\n"
        + "回复简洁（2-4句），不自我介绍，不客套。每轮聚焦一个问题。\n\n"
        + "## 用户画像\n"
        + "- 预估总分: %d\n"
        + "- 本科层次: %s\n"
        + "- 跨考: %s\n"
        + "- 目标地区: %s\n"
        + "- 整体策略: %s\n"
        + "- 院校层次取舍: %s\n"
        + "- 地区取舍: %s\n\n"
        + "## 诚实推荐规则（最高优先级）\n"
        + "如果候选池中没有严格满足用户目标档位的学校，必须明确说明\"当前没有严格符合条件的候选\"，不能为了满足用户要求而降低标准凑数。\n"
        + "保底必须同时满足：gap明显为正（建议≥3）、招生规模正常（建议>9人）、数据完整度较高、canBeSafe=true。不满足则只能称\"稳妥线索\"\"低风险线索\"，不得称保底。\n"
        + "冲刺应满足：gap为负或学校层次明显高于用户本科+分差紧张。如果检索结果是分数有余量的高层次学校，应该说\"分数风险偏稳，但学校层次较高，可作为高层次主攻目标\"，不要强行称为冲刺。\n"
        + "当用户目标与候选池事实冲突时，优先尊重事实，而不是迎合用户愿望。\n\n"
        + "## 多维择校规则\n"
        + "候选学校中的「差距」= 用户预估分 - 学校录取均分。差距只是分数安全维度，不能单独决定冲刺/稳妥/保底。\n"
        + "必须综合判断：分数差距、统考/计划招生名额、拟录取区间、数据完整度、学校层次、地区偏好、专业方向匹配。\n"
        + "如果 canBeSafe=false，禁止称为保底；即使差距很大，也只能说\"分数有余量但存在明显风险/只能作稳妥或线索\"。\n"
        + "招生名额极少是强风险信号：≤3 人不能作为保底；4-9 人若数据不完整、没有拟录取区间或分数优势不足，也不能作为保底。\n"
        + "当画像显示整体策略偏稳时，优先找分差为正且招生规模、数据完整度也支撑的学校，而不是只按差距排序。\n"
        + "当画像显示院校层次或地区优先时，可以接受更高风险，但必须把取舍说清楚。\n"
        + "讨论学校时必须明确说出录取均分、差距、招生名额和主要风险，不要只说学校名字。\n\n"
        + "## 档位判断规则\n"
        + "工具返回的 canBeSafe / quotaRisk / gap 是系统事实判断依据，不得被你的主观判断覆盖。\n"
        + "如果 canBeSafe=false，不得称为保底。如果 gap 为负，不得称为保底或稳妥。\n"
        + "如果你的顾问判断与系统数据不同，必须使用\"高层次主攻\"\"稳妥线索\"\"保底线索\"等顾问标签表达，不能直接覆盖系统判断。\n"
        + "最终报告分档以后端逻辑校验为准，你在对话中的档位表达只是顾问参考意见。\n\n"
        + "## 数量规则\n"
        + "每轮最多分析1-2所学校。如果严格符合条件的学校不足，可以少推荐，不能为了凑数量推荐不合适的学校。\n"
        + "如果只能找到线索型候选，必须明确说\"这不是严格保底/稳妥，只能作为线索\"。\n"
        + "当候选不足时，优先给出调整建议（放宽地区、降低学校层次、扩大专业方向），而不是强行推荐。\n\n"
        + "## 地区规则（硬约束）\n"
        + "- 当用户明确指定城市或地区时，必须优先在该范围内筛选和推荐。\n"
        + "- 如果指定地区内没有满足目标档位的候选，必须先说明\"该地区暂未找到严格匹配候选\"，再询问是否扩展地区。\n"
        + "- 未经用户同意，不要直接推荐用户未指定的其他城市。\n"
        + "- 目标地区为\"不限\"时，只在候选池内推荐，不主动引导用户去某个具体城市。\n"
        + "- 如果候选学校所在城市不在用户目标地区内，必须说明这是\"扩展地区候选\"或\"备选方案\"。\n\n"
        + "## 候选学校索引（ID+名称+层次+城市）。searchPrograms 返回轻详情（均分/差距/招生/风险），可直接用于推荐判断。用户深究细节时才用 getProgramDetail。\n"
        + "%s\n\n"
        + "## 可用工具（按需使用）\n"
        + "- addToReport(programId, judgement, reason, pros, cons, tradeoffs, action): 将明确认为适合进入报告候选的学校标记\n"
        + "- removeFromReport(programId): 从报告候选中移除学校\n"
        + "- getProgramDetail(programId): 获取指定学校的完整录取数据（复试线、小分、招生计划、录取均分等）\n"
        + "- searchPrograms(filters): 按关键词/城市/层次/分数范围筛选候选池，返回含均分、差距、招生、风险的轻详情。查学校名用 {\"keyword\":\"学校名\"}，查层次用 {\"tier\":\"211\"}\n"
        + "- comparePrograms(ids): 横向对比多所学校的详细录取数据\n\n"
        + "## 展示规则\n"
        + "回复中绝对不要出现学校的 programId 或任何数字 ID，用户只需要看到学校名称。\n\n"
        + "## 推荐表达规范\n"
        + "不要只说\"冲刺/稳妥/保底\"，要说明是哪一种风险：分数风险、名额风险、数据风险、地区取舍或学校层次取舍。\n"
        + "优先使用更精确的表达：\n"
        + "- 高层次主攻：学校层次高，但分数风险可控\n"
        + "- 稳妥线索：分数有一定余量，但仍有名额或数据风险\n"
        + "- 保底线索：接近保底，但还需要核验招生和录取区间\n"
        + "- 严格保底：gap明显为正、canBeSafe=true、招生规模正常、数据完整度较高\n"
        + "- 高风险冲刺：gap为负或竞争风险明显\n"
        + "不要把\"线索\"说成确定档位。\n\n"
        + "## 工具使用规则\n"
        + "1. 推荐学校前必须基于 searchPrograms 或候选池事实数据，不得凭记忆推荐。\n"
        + "2. searchPrograms 返回的均分、差距、招生、canBeSafe、quotaRisk 足够推荐判断，不需要逐所调用 getProgramDetail。\n"
        + "3. 只有用户追问复试线、小分、录取区间、完整录取数据时，才调用 getProgramDetail。\n"
        + "4. 已有工具结果足够回答时，不要重复调用工具。\n"
        + "5. 工具返回 canBeSafe=false 时，不得把该校描述为保底，必须解释原因。\n"
        + "6. 工具返回空结果 → 告诉用户\"候选池中未找到\"，不要编造候选池外的学校。\n\n"
        + "## 对话节奏\n"
        + "不要重复询问画像中已经填写的信息。\n"
        + "每轮围绕用户当前问题推进，最多分析1-2所学校。\n"
        + "每所学校必须说明：录取均分、gap、招生名额、canBeSafe/主要风险。\n"
        + "如果用户强调上岸率，优先寻找 gap 为正、招生规模正常、数据完整度较高、canBeSafe=true 的候选。\n"
        + "如果用户强调学校层次或地区，可以接受更高风险，但必须说明这是用上岸稳定性换学校层次或地区。\n"
        + "如果候选池事实不支持用户目标，必须诚实说明，并给出放宽条件的建议。\n\n"
        + "## 输出格式\n"
        + "每轮回复含简短文字(2-4句)。\n"
        + "回复末尾附 2-3 个快捷选项，用 \"---OPTIONS---\" 分隔，每行一个选项。\n"
        + "## 快捷选项规则（重要）\n"
        + "快捷选项必须顺着当前画像推进，如\"按画像开始筛选\"\"提高学校层次\"\"调整地区范围\"\"先看稳妥候选\"。\n"
        + "不要在用户明确选择城市维度前，生成\"限定某城市\"这类具体城市选项。\n"
        + "选项应顺着你的分析结论往前推进，不要重复已讨论过的内容或给出与分析矛盾的选择。\n"
        + "好的选项示例: \"确认XX为稳妥目标\" \"再看看保底选择\" \"换一个城市看看\"\n"
        + "禁止将工具调用作为快捷选项。以下选项禁止出现：\n"
        + "- \"查看XX学校详细数据\" \"查看XX专业分数线\" \"对比XX和XX\" — 这些都是工具调用，由你自动完成\n"
        + "- \"🔍\" \"📊\" 等带工具图标的选项\n"
        + "## 书签规则\n"
        + "1. 分析完某所学校并认为适合推荐后，必须调用 addToReport 将该校及推荐理由标记到报告候选。\n"
        + "2. 用户说\"加入报告\"\"标记\"\"这所也要\"时，立即调用 addToReport。\n"
        + "3. 之前标记过的学校，后续又深入讨论了，应重新调用 addToReport 更新推荐理由。\n"
        + "4. 可用 removeFromReport 移除用户不需要的学校。\n\n"
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
            tierDisplayLabel(profile.get("undergradTier")),
            formatProfileField(profile, "isCrossMajor", "否"),
            formatProfileField(profile, "targetRegions", "不限"),
            preferenceLabel("riskPreference", profile.get("riskPreference")),
            preferenceLabel("schoolTierPreference", profile.get("schoolTierPreference")),
            preferenceLabel("regionStrategy", profile.get("regionStrategy")),
            summaryText);

        String conversationId = UUID.randomUUID().toString();

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        log.info("[AI-TRACE] ======== START conversationId={} userId={} ========", conversationId, userId);
        log.info("[AI-TRACE] SYSTEM PROMPT (first 500 chars):\n{}...",
            systemPrompt.length() > 500 ? systemPrompt.substring(0, 500) : systemPrompt);
        log.debug("[AI-TRACE] SYSTEM PROMPT (full):\n{}", systemPrompt);

        // 首轮模板化：不调 LLM，避免 60s+ 的工具探索
        String regionLabel = preferenceLabel("regionStrategy", profile.get("regionStrategy"));
        String riskLabel = preferenceLabel("riskPreference", profile.get("riskPreference"));
        String messageText = String.format(
            "已读取你的画像：预估 %d 分、%s、地区偏好%s、策略%s。候选池 %d 所学校。"
                + "建议从稳妥候选入手，再补充冲刺与保底。",
            estimatedScore,
            tierDisplayLabel(profile.get("undergradTier")),
            regionLabel.length() > 20 ? regionLabel.substring(0, 20) : regionLabel,
            riskLabel.length() > 16 ? riskLabel.substring(0, 16) : riskLabel,
            summaryList.size());
        List<String> options = initialPreferenceOptions(profile);

        // 保存首轮模板消息到对话历史（作为 assistant 消息）
        Map<String, Object> initUserMsg = new LinkedHashMap<>();
        initUserMsg.put("role", "user");
        initUserMsg.put("content", "开始择校对话。");
        messages.add(initUserMsg);

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", messageText + "\n---OPTIONS---\n"
            + String.join("\n", options));
        messages.add(assistantMsg);

        String convJson = JSON.toJSONString(messages);
        String poolJson = JSON.toJSONString(summaryList);

        redisTemplate.opsForValue().set("ai:conv:" + conversationId, convJson, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("ai:pool:" + conversationId, poolJson, TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("ai:owner:" + conversationId, userId.toString(), TTL_SECONDS, TimeUnit.SECONDS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("message", messageText);
        result.put("options", options);
        result.put("cards", Collections.emptyList());
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
            // Skip messages with empty/null content — these are tool-call artifacts
            if (content == null || content.isBlank()) continue;
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

        int roundNum = (int) chatMemory.messages().stream().filter(m -> m instanceof UserMessage).count();
        log.info("[AI-TRACE] ======== CHAT conversationId={} userId={} round={} ========", conversationId, userId, roundNum);
        log.info("[AI-TRACE] USER INPUT (round {}): {}", roundNum, message, conversationId);

        String aiResponse;
        try {
            AiRecommendationTools.startChatContext(conversationId);
            aiResponse = assistant.chat("<user_input>" + message + "</user_input>");
        } catch (Exception e) {
            log.warn("[AI-Chat] Primary chat failed, retrying. userId={}, conversationId={}, message={}",
                userId, conversationId, e.getMessage(), e);
            try {
                AiRecommendationTools.startChatContext(conversationId);
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
            persistSearchedProgramIds(conversationId);
            AiRecommendationTools.clear();
        }

        log.info("[AI-TRACE] AI RAW OUTPUT (round {}):\n{}", roundNum, aiResponse);

        // Rebuild messages from memory + system prompt for Redis persistence
        messages = new ArrayList<>();
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", finalSystemPrompt);
        messages.add(sysMsg);
        for (ChatMessage cm : chatMemory.messages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (cm instanceof AiMessage) {
                String text = ((AiMessage) cm).text();
                if (text == null || text.isBlank()) continue;
                m.put("role", "assistant");
                m.put("content", text);
            } else if (cm instanceof UserMessage) {
                String text = ((UserMessage) cm).singleText();
                if (text == null || text.isBlank()) continue;
                m.put("role", "user");
                m.put("content", text);
            } else {
                continue;
            }
            messages.add(m);
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
        String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", messageText);
        result.put("options", options);
        List<Map<String, Object>> cards = hydrateChatCards(messageText, poolJson);
        result.put("cards", cards);
        persistDiscussedProgramIds(conversationId, cards, messageText);
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
            // Skip messages with empty/null content — these are tool-call artifacts
            if (content == null || content.isBlank()) continue;
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

        int streamRoundNum = (int) chatMemory.messages().stream().filter(m -> m instanceof UserMessage).count();
        log.info("[AI-TRACE] ======== CHAT-STREAM conversationId={} userId={} round={} ========", conversationId, userId, streamRoundNum);
        log.info("[AI-TRACE] USER INPUT (round {}): {}", streamRoundNum, message);

        StringBuilder fullResponse = new StringBuilder();
        try {
            AiRecommendationTools.startChatContext(conversationId);
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
                    persistSearchedProgramIds(conversationId);
                    AiRecommendationTools.clear();
                    persistStreamConversation(userId, conversationId, finalSystemPrompt, chatMemory);
                    String rawText = response != null && response.aiMessage() != null && response.aiMessage().text() != null
                        ? response.aiMessage().text()
                        : fullResponse.toString();
                    log.info("[AI-TRACE] AI RAW OUTPUT (round {}):\n{}", streamRoundNum, rawText);
                    Map<String, Object> result = new LinkedHashMap<>();
                    String messageText = parseMessageText(rawText);
                    result.put("message", messageText);
                    result.put("options", parseOptionsList(rawText));
                    List<Map<String, Object>> cards = hydrateChatCards(messageText, redisTemplate.opsForValue().get("ai:pool:" + conversationId));
                    result.put("cards", cards);
                    persistDiscussedProgramIds(conversationId, cards, messageText);
                    callback.onComplete(result);
                })
                .onError(error -> {
                    persistSearchedProgramIds(conversationId);
                    AiRecommendationTools.clear();
                    log.error("[AI-Chat-Stream] Stream failed. userId={}, conversationId={}, message={}",
                        userId, conversationId, error.getMessage(), error);
                    callback.onError(error);
                })
                .start();
        } catch (Exception e) {
            persistSearchedProgramIds(conversationId);
            AiRecommendationTools.clear();
            log.error("[AI-Chat-Stream] Stream setup failed. userId={}, conversationId={}, message={}",
                userId, conversationId, e.getMessage(), e);
            callback.onError(e);
        }
    }

    private int extractEstimatedScore(String convJson) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> msgs = JSON.parseObject(convJson, List.class);
            if (msgs != null && !msgs.isEmpty()) {
                String content = (String) msgs.get(0).get("content");
                if (content != null && content.contains("预估总分:")) {
                    int s = content.indexOf("预估总分:") + 6;
                    int e = content.indexOf("\n", s);
                    if (e < 0) e = content.length();
                    return Integer.parseInt(content.substring(s, e).trim());
                }
            }
        } catch (Exception ignored) {}
        return 300;
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

        // 读取或自动填充书签
        String bookmarkJson = redisTemplate.opsForValue().get("ai:bookmarks:" + conversationId);
        List<AiBookmark> bookmarks;
        try {
            bookmarks = bookmarkJson != null ? JSON.parseArray(bookmarkJson, AiBookmark.class) : new ArrayList<>();
        } catch (Exception e) {
            bookmarks = new ArrayList<>();
        }
        if (bookmarks == null) bookmarks = new ArrayList<>();

        // 合并：保留 addToReport 书签（完整 AI 观点），补充 autoFill 发现的未覆盖学校
        String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
        List<AiBookmark> autoFilled = autoFillBookmarks(convJson, poolJson, conversationId);
        if (!autoFilled.isEmpty()) {
            Set<Long> bookmarkedIds = new LinkedHashSet<>();
            for (AiBookmark bm : bookmarks) bookmarkedIds.add(bm.getProgramId());
            for (AiBookmark filled : autoFilled) {
                if (!bookmarkedIds.contains(filled.getProgramId())) {
                    bookmarks.add(filled);
                }
            }
        }

        // 提取预估分
        int estimatedScore = extractEstimatedScore(convJson);

        // 创建 recommendation_log
        RecommendationLog recLog = new RecommendationLog();
        recLog.setUserId(userId);
        recLog.setProfileSnapshot(JSON.toJSONString(Map.of("userId", userId, "conversationId", conversationId, "mode", "bookmark")));
        recLog.setResultJson("{\"status\":\"PENDING\"}");
        recLog.setRuleVersion("ai-bookmark");
        recLog.setDataVersion("1.0");
        recLog.setIsPaid(0);
        logMapper.insertRecommendationLog(recLog);
        Long reportId = recLog.getId();

        // 直接生成报告（无 MQ，无 LLM）
        Map<String, Object> reportJson = aiReportBuilder.buildFromBookmarks(
            JSON.toJSONString(bookmarks), poolJson != null ? poolJson : "[]", estimatedScore);

        // 延长 TTL
        redisTemplate.expire("ai:conv:" + conversationId, REPORT_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.expire("ai:pool:" + conversationId, REPORT_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.expire("ai:bookmarks:" + conversationId, REPORT_TTL_DAYS, TimeUnit.DAYS);

        String resultJson = JSON.toJSONString(reportJson);
        redisTemplate.opsForValue().set("ai:report:" + reportId, resultJson, REPORT_TTL_DAYS, TimeUnit.DAYS);
        recLog.setResultJson(resultJson);
        try { logMapper.updateReportResult(reportId, resultJson); } catch (Exception ignored) {}

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("status", "DONE");
        result.put("result", reportJson);
        return result;
    }

    /** 持久化本轮 searchPrograms 返回的 programId 到 Redis，供 autoFillBookmarks 兜底使用 */
    private void persistSearchedProgramIds(String conversationId) {
        try {
            AiToolTrace trace = AiRecommendationTools.currentTrace();
            if (trace == null) return;
            Set<Long> searchedIds = trace.getSearchedProgramIds();
            if (searchedIds.isEmpty()) return;

            String searchedKey = "ai:searched:" + conversationId;
            String existing = redisTemplate.opsForValue().get(searchedKey);
            Set<Long> allIds = new LinkedHashSet<>(searchedIds);
            if (existing != null && !existing.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Long> existingList = JSON.parseArray(existing, Long.class);
                    if (existingList != null) allIds.addAll(existingList);
                } catch (Exception ignored) {}
            }
            redisTemplate.opsForValue().set(searchedKey,
                JSON.toJSONString(new ArrayList<>(allIds)),
                Duration.ofMinutes(60));
        } catch (Exception ignored) {}
    }

    /** 持久化 AI 讨论过的学校及分析文本（用于 autoFillBookmarks 填充 opinion） */
    private void persistDiscussedProgramIds(String conversationId, List<Map<String, Object>> cards, String assistantText) {
        if (conversationId == null || conversationId.isBlank()) return;
        if (cards == null || cards.isEmpty()) return;
        try {
            String key = "ai:discussed:" + conversationId;
            List<Map<String, Object>> allTraces = new ArrayList<>();

            // 读取已有 trace
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null && !existing.isBlank()) {
                try {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    List<Map<String, Object>> parsed = (List) JSON.parseArray(existing, Map.class);
                    if (parsed != null) allTraces.addAll(parsed);
                } catch (Exception ignored) {}
            }

            // 合并本轮新 trace（最新在前，覆盖同 programId）
            Set<Long> seen = new LinkedHashSet<>();
            for (int i = allTraces.size() - 1; i >= 0; i--) {
                Long pid = toLong(allTraces.get(i).get("programId"));
                if (pid != null) seen.add(pid);
            }
            for (Map<String, Object> card : cards) {
                Object pidObj = card.get("programId");
                Long pid = pidObj instanceof Number n ? n.longValue() : null;
                if (pid == null) continue;
                if (seen.contains(pid)) continue; // 已有，跳过
                seen.add(pid);

                String schoolName = String.valueOf(card.getOrDefault("school", ""));
                String programName = String.valueOf(card.getOrDefault("program", ""));
                String snippet = extractSnippetAroundSchool(assistantText, schoolName, 200);

                Map<String, Object> trace = new LinkedHashMap<>();
                trace.put("programId", pid);
                trace.put("schoolName", schoolName);
                trace.put("programName", programName);
                trace.put("assistantSnippet", snippet);
                trace.put("source", "auto_fill_discussed");
                trace.put("status", "discussed");
                allTraces.add(0, trace); // 最新在前
            }

            // 上限 20 个
            if (allTraces.size() > 20) allTraces = allTraces.subList(0, 20);
            redisTemplate.opsForValue().set(key, JSON.toJSONString(allTraces), Duration.ofMinutes(60));
        } catch (Exception e) {
            log.warn("persistDiscussedProgramIds failed: conversationId={}", conversationId, e);
        }
    }

    /** 从 AI 回复中提取学校名附近的文本片段 */
    private String extractSnippetAroundSchool(String text, String schoolName, int maxLen) {
        if (text == null || schoolName == null || schoolName.isBlank()) return "";
        int pos = text.indexOf(schoolName);
        if (pos < 0) {
            // 找不到全名，截取文本开头作为兜底
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
        }
        int start = Math.max(0, pos - 30);
        int end = Math.min(text.length(), pos + maxLen);
        String snippet = text.substring(start, end).trim();
        // 确保从完整字符开始
        if (start > 0 && snippet.indexOf('。') > 0) {
            snippet = snippet.substring(snippet.indexOf('。') + 1).trim();
        }
        if (snippet.length() > maxLen) snippet = snippet.substring(0, maxLen) + "...";
        return snippet;
    }

    private static Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val == null) return null;
        try { return Long.parseLong(String.valueOf(val)); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<AiBookmark> autoFillBookmarks(String convJson, String poolJson, String conversationId) {
        Set<Long> discussedIds = new LinkedHashSet<>();
        Map<Long, String> discussedSnippets = new LinkedHashMap<>();
        String fillSource = null;

        // 来源 1（优先）：AI 实际讨论过的学校 + 分析原文
        if (conversationId != null) {
            try {
                String discussedJson = redisTemplate.opsForValue().get("ai:discussed:" + conversationId);
                if (discussedJson != null && !discussedJson.isBlank()) {
                    // 尝试新格式 List<{programId, schoolName, assistantSnippet, ...}>
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    List<Map<String, Object>> traces = (List) JSON.parseArray(discussedJson, Map.class);
                    if (traces != null && !traces.isEmpty()) {
                        for (Map<String, Object> t : traces) {
                            Long pid = toLong(t.get("programId"));
                            if (pid != null) {
                                discussedIds.add(pid);
                                String snippet = (String) t.getOrDefault("assistantSnippet", "");
                                if (!snippet.isBlank()) discussedSnippets.put(pid, snippet);
                            }
                        }
                        fillSource = "auto_fill_discussed";
                    } else {
                        // 旧格式兼容：List<Long>
                        @SuppressWarnings("unchecked")
                        List<Long> ids = JSON.parseArray(discussedJson, Long.class);
                        if (ids != null && !ids.isEmpty()) {
                            discussedIds.addAll(ids);
                            fillSource = "auto_fill_discussed";
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 来源 2（兜底）：仅 ai:discussed 为空时才读 ai:searched
        if (discussedIds.isEmpty() && conversationId != null) {
            try {
                String searchedJson = redisTemplate.opsForValue().get("ai:searched:" + conversationId);
                if (searchedJson != null && !searchedJson.isBlank()) {
                    @SuppressWarnings("unchecked")
                    List<Long> ids = JSON.parseArray(searchedJson, Long.class);
                    if (ids != null && !ids.isEmpty()) {
                        discussedIds.addAll(ids);
                        fillSource = "auto_fill_search";
                    }
                }
            } catch (Exception ignored) {}
        }

        // 来源 3（最后兜底）：从对话文本正则提取 getProgramDetail 引用
        if (discussedIds.isEmpty() && convJson != null && !convJson.isBlank()) {
            try {
                List<Map<String, Object>> messages = JSON.parseObject(convJson, List.class);
                for (Map<String, Object> msg : messages) {
                    if ("system".equals(msg.get("role"))) continue;
                    String content = (String) msg.get("content");
                    if (content == null) continue;
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\b(?:programId[\"']?\\s*[:=]\\s*|ID[:]?\\s*|getProgramDetail\\()\\s*(\\d{1,10})\\b")
                        .matcher(content);
                    while (m.find()) { try { discussedIds.add(Long.parseLong(m.group(1))); } catch (NumberFormatException ignored) {} }
                }
            } catch (Exception ignored) {}
        }
        if (discussedIds.isEmpty()) return Collections.emptyList();

        // 数量限制：最多 6 所
        if (discussedIds.size() > 6) {
            discussedIds = new LinkedHashSet<>(new ArrayList<>(discussedIds).subList(0, 6));
        }

        Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
        if (poolJson != null && !poolJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
                for (Map<String, Object> row : pool) {
                    Long pid = row.get("programId") instanceof Number n ? n.longValue() : null;
                    if (pid != null) poolMap.put(pid, row);
                }
            } catch (Exception ignored) {}
        }

        final String finalSource = fillSource != null ? fillSource : "auto_fill_search";
        final boolean isDiscussed = "auto_fill_discussed".equals(finalSource);

        List<AiBookmark> result = new ArrayList<>();
        for (Long pid : discussedIds) {
            Map<String, Object> row = poolMap.get(pid);
            if (row == null) continue;
            Integer gapVal = row.get("gap") instanceof Number n ? n.intValue() : 0;
            int gap = gapVal != null ? gapVal : 0;
            String judgement = gap <= 0 ? "reach" : gap <= 14 ? "steady" : "safe";
            AiBookmark bm = new AiBookmark();
            bm.setProgramId(pid);
            bm.setSchoolName(String.valueOf(row.getOrDefault("schoolName", "")));
            bm.setProgramName(String.valueOf(row.getOrDefault("programName", "")));
            bm.setJudgement(judgement);
            // 优先使用 AI 实际分析原文；没有则退回兜底文案
            String snippet = isDiscussed ? discussedSnippets.get(pid) : null;
            if (snippet != null && !snippet.isBlank()) {
                bm.setReason(snippet);
            } else if (isDiscussed) {
                bm.setReason("AI 已在对话中分析过该学校，系统自动补入报告候选。");
            } else {
                bm.setReason("该校出现在筛选结果中，尚未经过 AI 深入分析，系统作为兜底候选补入。建议继续对话获取 AI 详细分析。");
            }
            bm.setPros(List.of());
            bm.setCons(List.of());
            bm.setTradeoffs(List.of());
            bm.setRecommendedAction(isDiscussed
                ? "可在对话中进一步了解该校复试线、考试科目等细节。"
                : "建议继续对话，由 AI 深入分析后再更新推荐。");
            bm.setSource(finalSource);
            bm.setStatus(isDiscussed ? "discussed" : "suggested");
            bm.setUserConfirmed(false);
            result.add(bm);
        }
        return result;
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
            // 延长候选池 TTL，防止 MQ 消费时已过期
            redisTemplate.expire("ai:agent:pool:" + reportId, REPORT_TTL_DAYS, TimeUnit.DAYS);
            redisTemplate.expire("ai:analyze:pool:" + reportId, REPORT_TTL_DAYS, TimeUnit.DAYS);

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
                // 保留 FAILED 状态，不要覆盖为 COMPLETED
                if (!"FAILED".equals(parsed.get("status"))) {
                    parsed.put("status", "COMPLETED");
                }
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
    public Map<String, Object> getBookmarks(Long userId, String conversationId) {
        String owner = redisTemplate.opsForValue().get("ai:owner:" + conversationId);
        if (owner == null || !owner.equals(userId.toString())) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("bookmarks", Collections.emptyList());
            err.put("count", 0);
            return err;
        }
        String json = redisTemplate.opsForValue().get("ai:bookmarks:" + conversationId);
        List<AiBookmark> bookmarks;
        try {
            bookmarks = json != null ? JSON.parseArray(json, AiBookmark.class) : Collections.emptyList();
        } catch (Exception e) {
            bookmarks = Collections.emptyList();
        }
        if (bookmarks == null) bookmarks = Collections.emptyList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookmarks", bookmarks);
        result.put("count", bookmarks.size());
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
        basis.put("undergradTier", tierDisplayLabel(profile.get("undergradTier")));
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
            // Recompute from actual fields — DB value is often stale/missing
            item.put("dataCompleteness", computedCompleteness(item));
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

    /**
     * 聊天系统提示用的精简版候选池摘要。
     * 刻意只保留索引信息（ID、学校、专业、层次、城市），不包含分数/招生/风险等具体数据。
     * 这迫使 AI 在讨论任何学校时必须先调用 getProgramDetail/searchPrograms 获取真实数据，
     * 而不是直接从系统提示中"偷看"数据后绕过工具调用。
     */
    private String buildSummaryText(List<Map<String, Object>> summaryList) {
        if (summaryList == null || summaryList.isEmpty()) {
            return "（无候选学校）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(summaryList.size()).append(" 所学校。获取详细数据请调用 getProgramDetail(programId)：\n");
        for (int i = 0; i < summaryList.size(); i++) {
            Map<String, Object> item = summaryList.get(i);
            sb.append(i + 1).append(". ID:").append(item.get("programId"));
            sb.append(" | ").append(item.get("schoolName"));
            sb.append(" | 专业:").append(item.get("programName"));
            sb.append(" | 层次:").append(item.get("schoolTier"));
            sb.append(" | 城市:").append(item.get("city"));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String computedCompleteness(Map<String, Object> row) {
        boolean hasScore = integerValue(row.get("scoreLine")) != null;
        boolean hasRange = integerValue(row.get("admissionLow")) != null
            && integerValue(row.get("admissionHigh")) != null;
        boolean hasAverage = integerValue(row.get("avgAdmittedScore")) != null;
        boolean hasCount = integerValue(row.get("planCount")) != null
            || integerValue(row.get("admittedCount")) != null;
        boolean hasMainExtra = hasAverage
            || integerValue(row.get("admissionLow")) != null
            || integerValue(row.get("planCount")) != null
            || integerValue(row.get("unifiedExamQuota")) != null;
        if (hasScore && hasRange && hasAverage && hasCount) return "A";
        if (hasScore && hasMainExtra) return "B";
        return "C";
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return null; }
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

    /** 匹配 ---OPTIONS--- 分隔符，容忍空格偏差（如 --- OPTIONS ---） */
    private static final java.util.regex.Pattern OPTIONS_DELIMITER =
        java.util.regex.Pattern.compile("---\\s*OPTIONS\\s*---");

    private String parseMessageText(String content) {
        if (content == null) {
            return "";
        }
        java.util.regex.Matcher m = OPTIONS_DELIMITER.matcher(content);
        if (m.find()) {
            return content.substring(0, m.start()).trim();
        }
        return content.trim();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> hydrateChatCards(String messageText, String poolJson) {
        if (messageText == null || messageText.isBlank() || poolJson == null || poolJson.isBlank()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> pool;
        try {
            pool = JSON.parseObject(poolJson, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        if (pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        for (Map<String, Object> row : pool) {
            String school = String.valueOf(row.getOrDefault("schoolName", ""));
            String program = String.valueOf(row.getOrDefault("programName", ""));
            if (school.isBlank() || !mentionedSchool(messageText, school)) {
                continue;
            }
            if (!program.isBlank() && mentionedProgram(messageText, program)) {
                cards.add(toChatCard(row, messageText));
            } else if (program.isBlank() || mentionedNearSchool(messageText, school, program)
                || mentionedSchoolWithFacts(messageText, school, row)) {
                cards.add(toChatCard(row, messageText));
            }
            if (cards.size() >= 8) {
                break;
            }
        }
        return cards;
    }

    private boolean mentionedSchool(String text, String school) {
        return normalizeMentionText(text).contains(normalizeMentionText(school));
    }

    private boolean mentionedProgram(String text, String program) {
        String normalizedText = normalizeMentionText(text);
        String normalizedProgram = normalizeMentionText(program);
        if (normalizedProgram.isBlank()) {
            return true;
        }
        if (normalizedText.contains(normalizedProgram)) {
            return true;
        }
        for (String alias : programAliases(normalizedProgram)) {
            if (!alias.isBlank() && normalizedText.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private boolean mentionedSchoolWithFacts(String text, String school, Map<String, Object> row) {
        String window = normalizeMentionText(localMentionWindow(text, school));
        if (window.isBlank() || !window.contains(normalizeMentionText(school))) {
            return false;
        }
        return mentionsNumericFact(window, row.get("avgAdmittedScore"))
            || mentionsSignedGap(window, row.get("gap"))
            || mentionsNumericFact(window, row.getOrDefault("unifiedExamQuota", row.get("planCount")));
    }

    /**
     * Digit-boundary match: a number like "295" or "33" should only match
     * standalone, not as a substring of another number or decimal like "2.5".
     */
    private boolean mentionsNumericFact(String normalizedText, Object value) {
        if (!(value instanceof Number n)) {
            return false;
        }
        int num = n.intValue();
        return Pattern.compile("(?<![\\d.])" + num + "(?!\\d)").matcher(normalizedText).find();
    }

    /**
     * Match a signed gap like "+5" or an unsigned gap "5" as a standalone
     * number.  The digit-boundary ensures "5" does not match inside "2.5:1"
     * or "25".  "差距+5" should match; "复录比2.5:1" should not.
     */
    private boolean mentionsSignedGap(String normalizedText, Object value) {
        if (!(value instanceof Number n)) {
            return false;
        }
        int gap = n.intValue();
        String unsigned = Integer.toString(gap);
        // Unsigned: standalone "5" not preceded by digit/dot/sign
        if (Pattern.compile("(?<![\\d.+\\-])" + unsigned + "(?!\\d)")
                .matcher(normalizedText).find()) {
            return true;
        }
        if (gap > 0) {
            String signed = "+" + unsigned;
            return Pattern.compile("(?<![\\d.+\\-])" + Pattern.quote(signed) + "(?!\\d)")
                    .matcher(normalizedText).find();
        }
        return false;
    }

    private boolean mentionedNearSchool(String text, String school, String program) {
        if (program == null || program.isBlank()) {
            return true;
        }
        String normalizedText = normalizeMentionText(text);
        int schoolIndex = normalizedText.indexOf(normalizeMentionText(school));
        int programIndex = normalizedText.indexOf(normalizeMentionText(program));
        return schoolIndex >= 0 && programIndex >= 0 && Math.abs(programIndex - schoolIndex) <= 80;
    }

    private List<String> programAliases(String normalizedProgram) {
        List<String> aliases = new ArrayList<>();
        if (normalizedProgram.contains("计算机")) {
            aliases.add("计算机");
        }
        if (normalizedProgram.contains("软件")) {
            aliases.add("软件");
        }
        if (normalizedProgram.contains("人工智能")) {
            aliases.add("人工智能");
        }
        if ("电子信息".equals(normalizedProgram)) {
            aliases.add("电子信息");
        }
        if (normalizedProgram.contains("网络空间安全")) {
            aliases.add("网安");
            aliases.add("网络空间安全");
        }
        return aliases;
    }

    private String normalizeMentionText(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .replace('－', '-')
            .replace('—', '-')
            .replace('·', '-')
            .replaceAll("\\s+", "");
    }

    private Map<String, Object> toChatCard(Map<String, Object> row, String messageText) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("programId", row.get("programId"));
        card.put("school", row.get("schoolName"));
        card.put("program", row.get("programName"));
        card.put("college", row.get("collegeName"));
        card.put("tier", tierDisplayLabel(row.get("schoolTier")));
        card.put("city", row.get("city"));
        card.put("province", row.get("province"));
        card.put("avg", row.get("avgAdmittedScore"));
        card.put("gap", row.get("gap"));
        card.put("quota", row.getOrDefault("unifiedExamQuota", row.get("planCount")));
        card.put("admissionLow", row.get("admissionLow"));
        card.put("admissionHigh", row.get("admissionHigh"));
        card.put("level", inferChatCardLevel(row, messageText));
        card.put("reason", inferChatCardReason(row));
        return card;
    }

    /**
     * Chat cards no longer infer a tier label from the AI's free-form text.
     * The AI's tier judgment is expressed in its natural-language reply; the card
     * only provides the factual data (均分/差距/招生) that backs that judgment.
     * Tier labels belong in the structured report, not in chat cards.
     */
    private String inferChatCardLevel(Map<String, Object> row, String messageText) {
        return "";
    }

    private String localMentionWindow(String text, String school) {
        int idx = school == null ? -1 : text.indexOf(school);
        if (idx < 0) return "";
        int start = Math.max(0, idx - 40);
        int end = Math.min(text.length(), idx + 120);
        return text.substring(start, end);
    }

    private String inferChatCardReason(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        sb.append("均分").append(displaySummaryValue(row.get("avgAdmittedScore")));
        Object gap = row.get("gap");
        if (gap instanceof Number n) {
            int g = n.intValue();
            sb.append("，差距").append(g > 0 ? "+" : "").append(g);
        }
        sb.append("，招生").append(displaySummaryValue(row.getOrDefault("unifiedExamQuota", row.get("planCount"))));
        Object reason = row.get("safeBlockReason");
        if (reason != null && !String.valueOf(reason).isBlank()) {
            sb.append("；").append(reason);
        }
        return sb.toString();
    }

    private List<String> parseOptionsList(String content) {
        if (content == null) {
            return Collections.emptyList();
        }
        java.util.regex.Matcher m = OPTIONS_DELIMITER.matcher(content);
        if (!m.find()) {
            return Collections.emptyList();
        }
        String optionsSection = content.substring(m.end());
        List<String> options = new ArrayList<>();
        for (String line : optionsSection.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                options.add(trimmed);
            }
        }
        return options;
    }

    private static List<String> initialPreferenceOptions(Map<String, Object> profile) {
        List<String> options = new ArrayList<>();
        options.add("按我的画像开始筛选");
        options.add(adjustSchoolTierOption(profile.get("schoolTierPreference")));
        options.add(adjustRegionOption(profile.get("regionStrategy")));
        return options;
    }

    private static String adjustSchoolTierOption(Object value) {
        String v = value == null ? "" : String.valueOf(value);
        if ("must_211_or_better".equals(v) || "prefer_211_or_better".equals(v)) {
            return "降低层次要求，看看更稳的学校";
        }
        return "提高学校层次，看看211以上";
    }

    private static String adjustRegionOption(Object value) {
        String v = value == null ? "" : String.valueOf(value);
        if ("developed_priority".equals(v) || "developed_balanced".equals(v)) {
            return "放宽地区，优先提高上岸率";
        }
        return "优先发达地区，看看可选学校";
    }

    private static String preferenceLabel(String key, Object value) {
        String v = value == null ? "" : String.valueOf(value);
        return switch (key) {
            case "riskPreference" -> switch (v) {
                case "safe_first" -> "稳妥优先，尽量提高上岸概率";
                case "reach_first" -> "愿意冲刺，接受更高风险";
                default -> "稳中求进，冲稳保均衡";
            };
            case "schoolTierPreference" -> switch (v) {
                case "must_211_or_better" -> "强烈希望 211/双一流及以上";
                case "prefer_211_or_better" -> "优先 211/双一流及以上";
                default -> "不强求层次，有学上更重要";
            };
            case "regionStrategy" -> switch (v) {
                case "developed_priority" -> "强意愿发达地区，愿意承受风险";
                case "developed_balanced" -> "发达地区优先，但要兼顾稳妥";
                case "target_regions_only" -> "只看目标地区";
                default -> "地区不强求，有学上更重要";
            };
            default -> v;
        };
    }

    /** 将数据库中原始 tier 值映射为用户可读的中文标签 */
    private static String tierDisplayLabel(Object value) {
        String v = value == null ? "" : String.valueOf(value);
        return switch (v) {
            case "985" -> "985";
            case "211" -> "211";
            case "DOUBLE_FIRST" -> "双一流";
            case "PUBLIC_REGULAR" -> "普通一本";
            case "PRIVATE" -> "民办";
            case "INDEPENDENT" -> "独立学院";
            case "RESEARCH_INSTITUTE" -> "科研院所";
            case "OTHER" -> "其他";
            default -> v.isBlank() ? "双非" : v;
        };
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
            .maxTokens(4096)
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
                String text = ((AiMessage) cm).text();
                if (text == null || text.isBlank()) continue;
                m.put("role", "assistant");
                m.put("content", text);
            } else if (cm instanceof UserMessage) {
                String text = ((UserMessage) cm).singleText();
                if (text == null || text.isBlank()) continue;
                m.put("role", "user");
                m.put("content", text);
            } else {
                continue;
            }
            messages.add(m);
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
