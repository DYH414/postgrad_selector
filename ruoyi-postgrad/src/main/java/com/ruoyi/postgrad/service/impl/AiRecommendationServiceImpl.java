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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.ai.AiBookmark;
import com.ruoyi.postgrad.domain.ai.AiRecommendationSafety;
import com.ruoyi.postgrad.domain.ai.AiReportSupport;
import com.ruoyi.postgrad.domain.ai.AiToolTrace;
import com.ruoyi.postgrad.domain.ai.AiConstants;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.domain.dto.CandidateProgramDTO;
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
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;

@Service
public class AiRecommendationServiceImpl implements IAiRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationServiceImpl.class);

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/chat-system.txt")
    private org.springframework.core.io.Resource chatSystemPromptResource;

    @Autowired
    private dev.langchain4j.model.chat.ChatModel chatModel;

    @Autowired
    private dev.langchain4j.model.openai.OpenAiStreamingChatModel streamingChatModel;

    @Autowired
    private IAiCandidatePoolService aiCandidatePoolService;

    private String loadPrompt() {
        try {
            return new String(chatSystemPromptResource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load chat system prompt", e);
        }
    }

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

    @Autowired
    private PreselectService preselectService;

    @Override
    public Map<String, Object> startConversation(Long userId, Map<String, Object> request) {
        Map<String, Object> profile = loadUserProfile(userId);
        int estimatedScore = getEstimatedScore(request, profile);

        List<CandidateProgramDTO> pool = aiCandidatePoolService.buildPool(request, profile, estimatedScore);

        List<Map<String, Object>> summaryList = buildSummaryList(pool, estimatedScore);
        String summaryText = buildSummaryText(summaryList);

        String systemPrompt = String.format(loadPrompt(),
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

        redisTemplate.opsForValue().set(AiConstants.keyConv(conversationId), convJson, AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
        redisTemplate.opsForValue().set(AiConstants.keyPool(conversationId), poolJson, AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
        redisTemplate.opsForValue().set(AiConstants.keyOwner(conversationId), userId.toString(), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

        // 异步触发 AI 预选（不阻塞首轮 336ms 响应）
        final String finalConvId = conversationId;
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[Preselect] Starting background preselection for conv={}", finalConvId);
                preselectCandidates(finalConvId);
                log.info("[Preselect] Background preselection completed for conv={}", finalConvId);
            } catch (Exception e) {
                log.warn("[Preselect] Background preselection failed for conv={}: {}", finalConvId, e.getMessage(), e);
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("message", messageText);
        result.put("options", options);
        result.put("cards", Collections.emptyList());
        result.put("profileBasis", buildProfileBasis(profile, estimatedScore));
        result.put("candidateCount", summaryList.size());
        return result;
    }

    // ==================== AI 预选（background_ai）====================

    /** 委托 PreselectService 执行 AI 预选，本方法只负责 Redis 读写与书签持久化 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void preselectCandidates(String conversationId) {
        String poolJson = redisTemplate.opsForValue().get(AiConstants.keyPool(conversationId));
        if (poolJson == null || poolJson.isBlank()) {
            log.warn("[Preselect] No pool found for conv={}", conversationId);
            return;
        }

        List<CandidateProgramDTO> pool = JSON.parseArray(poolJson, CandidateProgramDTO.class);
        if (pool == null) pool = Collections.emptyList();
        List<CandidateProgramDTO> includeList = preselectService.preselect(pool, chatModel);
        if (includeList.isEmpty()) return;

        // 建 poolMap 供 fact 查找
        Map<Long, CandidateProgramDTO> poolMap = new LinkedHashMap<>();
        for (CandidateProgramDTO p : pool) {
            if (p.getProgramId() != null) poolMap.put(p.getProgramId(), p);
        }

        // 读取已有书签，合并写入
        String bookmarkKey = AiConstants.keyBookmarks(conversationId);
        List<AiBookmark> existing = new ArrayList<>();
        String existingJson = redisTemplate.opsForValue().get(bookmarkKey);
        if (existingJson != null && !existingJson.isBlank()) {
            try { existing = JSON.parseArray(existingJson, AiBookmark.class); } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
            if (existing == null) existing = new ArrayList<>();
        }

        for (CandidateProgramDTO inc : includeList) {
            Long pid = inc.getProgramId();
            if (pid == null) continue;
            CandidateProgramDTO fact = poolMap.get(pid);
            if (fact == null) { log.warn("[Preselect] Skip pid={}: not in pool", pid); continue; }

            // background_ai 不能覆盖更高优先级的已有 bookmark
            final long targetPid = pid;
            AiBookmark existingBm = existing.stream()
                .filter(b -> java.util.Objects.equals(b.getProgramId(), targetPid))
                .findFirst().orElse(null);
            if (existingBm != null && sourceRank(existingBm.getSource()) < sourceRank("background_ai")) {
                log.info("[Preselect] Skip pid={}: already covered by higher-priority source={}",
                    pid, existingBm.getSource());
                continue;
            }
            existing.removeIf(b -> java.util.Objects.equals(b.getProgramId(), targetPid));

            // tier 从引用的 fact 对象推断（preselect 已计算）
            String aiTier = "steady"; // 默认
            AiRecommendationSafety.JudgementResult jr =
                AiRecommendationSafety.finalJudgement(fact.toMap(), aiTier);

            AiBookmark bm = new AiBookmark();
            bm.setProgramId(pid);
            bm.setSchoolName(fact.getSchoolName());
            bm.setProgramName(fact.getProgramName());
            bm.setSource("background_ai");
            bm.setStatus("suggested");
            bm.setUserConfirmed(false);
            bm.setJudgement(jr.finalJudgement());
            bm.setFinalJudgement(jr.finalJudgement());
            bm.setAiJudgement(aiTier);
            bm.setAdjusted(jr.adjusted());
            bm.setAdjustReason(jr.adjustReason());
            bm.setReason("");
            bm.setCons(inc.getQuotaRisk() != null ? List.of(inc.getQuotaRisk()) : List.of());
            bm.setPros(List.of());
            bm.setTradeoffs(List.of());
            bm.setRecommendedAction("");
            existing.add(bm);
        }
        redisTemplate.opsForValue().set(bookmarkKey, JSON.toJSONString(existing),
            Duration.ofMinutes(AiConstants.TTL_CONVERSATION));
        log.info("[Preselect] Wrote {} bookmarks for conv={}", includeList.size(), conversationId);
        persistStateFromRedis(conversationId);
    }

    private static int toInt(Object val, int fallback) {
        if (val instanceof Number n) return n.intValue();
        if (val == null) return fallback;
        try { return Integer.parseInt(String.valueOf(val)); } catch (NumberFormatException e) { return fallback; }
    }

    /** source 优先级：user_confirmed(0) > conversation_ai(1) > auto_fill_discussed(2)
     *  > background_ai(3) > auto_fill_search(4) > rule_fallback(5) */
    private static int sourceRank(String source) {
        if (source == null) return 9;
        return switch (source) {
            case "user_confirmed" -> 0;
            case "conversation_ai" -> 1;
            case "auto_fill_discussed" -> 2;
            case "background_ai" -> 3;
            case "auto_fill_search" -> 4;
            case "rule_fallback" -> 5;
            default -> 9;
        };
    }

    /** P3: 每档限量（reach≤3, steady≤4, safe≤3），user_confirmed 不占名额 */
    @SuppressWarnings("unchecked")
    private void trimReportTiers(Map<String, Object> reportJson) {
        java.util.Map<String, Integer> limits = java.util.Map.of("reach", 3, "steady", 4, "safe", 3);
        Object tiersObj = reportJson.get("tiers");
        if (!(tiersObj instanceof List<?> tiers)) return;

        for (Object t : tiers) {
            if (!(t instanceof Map<?, ?> tierMapRaw)) continue;
            Map<String, Object> tierMap = (Map<String, Object>) tierMapRaw;
            String level = String.valueOf(tierMap.getOrDefault("level", ""));
            int limit = limits.getOrDefault(level, 5);
            Object schoolsObj = tierMap.get("schools");
            if (!(schoolsObj instanceof List<?> schoolsRaw)) continue;

            // opinion 对象里可能没有 source，同时尝试顶层 bookmark 的 source
            // buildFromBookmarks 把 source 放入了 opinion.source
            List<Map<String, Object>> schools = new ArrayList<>();
            for (Object s : schoolsRaw) {
                if (s instanceof Map<?, ?> sm) schools.add((Map<String, Object>) sm);
            }

            // 排序：confirmed 优先 → sourceRank
            schools.sort((a, b) -> {
                boolean aConfirmed = Boolean.TRUE.equals(a.get("userConfirmed"));
                boolean bConfirmed = Boolean.TRUE.equals(b.get("userConfirmed"));
                if (aConfirmed != bConfirmed) return aConfirmed ? -1 : 1;
                String sa = String.valueOf(a.getOrDefault("source", ""));
                String sb = String.valueOf(b.getOrDefault("source", ""));
                return Integer.compare(sourceRank(sa), sourceRank(sb));
            });

            int nonConfirmed = 0;
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Map<String, Object> school : schools) {
                if (Boolean.TRUE.equals(school.get("userConfirmed"))) {
                    kept.add(school);
                } else if (nonConfirmed < limit) {
                    kept.add(school);
                    nonConfirmed++;
                }
            }
            tierMap.put("schools", (List) kept);
        }
    }

    @Override
    public Map<String, Object> chat(Long userId, String conversationId, String message) {
        String owner = redisTemplate.opsForValue().get(AiConstants.keyOwner(conversationId));
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

        String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
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
        redisTemplate.opsForValue().set(AiConstants.keyConv(conversationId), convJson, AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
        redisTemplate.expire(AiConstants.keyPool(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
        redisTemplate.expire(AiConstants.keyOwner(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

        if (messages.size() % 6 == 0) {
            saveConversationState(userId, conversationId, messages);
        }

        String messageText = parseMessageText(aiResponse);
        List<String> options = parseOptionsList(aiResponse);
        String poolJson = redisTemplate.opsForValue().get(AiConstants.keyPool(conversationId));

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
        String owner = redisTemplate.opsForValue().get(AiConstants.keyOwner(conversationId));
        if (owner == null) {
            callback.onError(new IllegalArgumentException("对话已过期，请开始新对话"));
            return;
        }
        if (!owner.equals(userId.toString())) {
            throw new SecurityException("Conversation ownership mismatch");
        }

        String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
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
            .streamingChatModel(streamingChatModel)
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
                    List<Map<String, Object>> cards = hydrateChatCards(messageText, redisTemplate.opsForValue().get(AiConstants.keyPool(conversationId)));
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
        } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
        return 300;
    }

    @Override
    public Map<String, Object> generateReport(Long userId, String conversationId) {
        String owner = redisTemplate.opsForValue().get(AiConstants.keyOwner(conversationId));
        if (owner == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "expired");
            err.put("message", "对话已过期");
            return err;
        }
        if (!owner.equals(userId.toString())) {
            throw new SecurityException("Conversation ownership mismatch");
        }

        String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
        if (convJson == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "expired");
            err.put("message", "对话已过期");
            return err;
        }

        // 读取或自动填充书签
        String bookmarkJson = redisTemplate.opsForValue().get(AiConstants.keyBookmarks(conversationId));
        List<AiBookmark> bookmarks;
        try {
            bookmarks = bookmarkJson != null ? JSON.parseArray(bookmarkJson, AiBookmark.class) : new ArrayList<>();
        } catch (Exception e) {
            bookmarks = new ArrayList<>();
        }
        if (bookmarks == null) bookmarks = new ArrayList<>();

        // 合并：保留 addToReport 书签（完整 AI 观点），补充 autoFill 发现的未覆盖学校
        String poolJson = redisTemplate.opsForValue().get(AiConstants.keyPool(conversationId));
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

        // 兜底：旧 bookmark 可能 finalJudgement 为空（如旧版 preselect 写入的），临时补算
        Map<Long, Map<String, Object>> reportPoolMap = null;
        for (AiBookmark bm : bookmarks) {
            if (bm.getFinalJudgement() != null && !bm.getFinalJudgement().isBlank()) continue;
            if (reportPoolMap == null) {
                reportPoolMap = new LinkedHashMap<>();
                if (poolJson != null && !poolJson.isBlank()) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
                    if (pool != null) {
                        for (Map<String, Object> p : pool) {
                            Long pid = p.get("programId") instanceof Number n ? n.longValue() : null;
                            if (pid != null) reportPoolMap.put(pid, p);
                        }
                    }
                }
            }
            Map<String, Object> fact = reportPoolMap.get(bm.getProgramId());
            if (fact == null) continue;
            // 旧 bookmark 可能没有 aiJudgement，用当前的 judgement 作为 aiTier
            String aiTier = bm.getAiJudgement();
            if (aiTier == null || aiTier.isBlank()) aiTier = bm.getJudgement();
            AiRecommendationSafety.JudgementResult jr =
                AiRecommendationSafety.finalJudgement(fact, aiTier);
            bm.setFinalJudgement(jr.finalJudgement());
            bm.setJudgement(jr.finalJudgement());
            bm.setAiJudgement(aiTier);
            bm.setAdjusted(jr.adjusted());
            bm.setAdjustReason(jr.adjustReason());
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

        // P3: 每档限量（confirmed 不占名额）
        trimReportTiers(reportJson);

        // 延长 TTL
        redisTemplate.expire(AiConstants.keyConv(conversationId), AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
        redisTemplate.expire(AiConstants.keyPool(conversationId), AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
        redisTemplate.expire(AiConstants.keyBookmarks(conversationId), AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);

        String resultJson = JSON.toJSONString(reportJson);
        redisTemplate.opsForValue().set(AiConstants.keyReport(reportId), resultJson, AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
        recLog.setResultJson(resultJson);
        try { logMapper.updateReportResult(reportId, resultJson); } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }

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

            String searchedKey = AiConstants.keySearched(conversationId);
            String existing = redisTemplate.opsForValue().get(searchedKey);
            Set<Long> allIds = new LinkedHashSet<>(searchedIds);
            if (existing != null && !existing.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Long> existingList = JSON.parseArray(existing, Long.class);
                    if (existingList != null) allIds.addAll(existingList);
                } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
            }
            redisTemplate.opsForValue().set(searchedKey,
                JSON.toJSONString(new ArrayList<>(allIds)),
                Duration.ofMinutes(AiConstants.TTL_TRACE_MINUTES));
        } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
    }

    /** 持久化 AI 讨论过的学校及分析文本（用于 autoFillBookmarks 填充 opinion） */
    private void persistDiscussedProgramIds(String conversationId, List<Map<String, Object>> cards, String assistantText) {
        if (conversationId == null || conversationId.isBlank()) return;
        if (cards == null || cards.isEmpty()) return;
        try {
            String key = AiConstants.keyDiscussed(conversationId);
            List<Map<String, Object>> allTraces = new ArrayList<>();

            // 读取已有 trace
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null && !existing.isBlank()) {
                try {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    List<Map<String, Object>> parsed = (List) JSON.parseArray(existing, Map.class);
                    if (parsed != null) allTraces.addAll(parsed);
                } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
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
            redisTemplate.opsForValue().set(key, JSON.toJSONString(allTraces), Duration.ofMinutes(AiConstants.TTL_TRACE_MINUTES));
        } catch (Exception e) {
            log.warn("persistDiscussedProgramIds failed: conversationId={}", conversationId, e);
        }
    }

    /** 从 AI 回复中提取特定学校的分析片段（只取该段，不串到下一所学校） */
    private String extractSnippetAroundSchool(String text, String schoolName, int maxLen) {
        if (text == null || schoolName == null || schoolName.isBlank()) return "";
        int pos = text.indexOf(schoolName);
        if (pos < 0) {
            return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
        }

        // 向后找到下一条学校边界（--- 分隔线 或 下一个编号列表项 或 其他学校名）
        int rawEnd = Math.min(text.length(), pos + maxLen);
        int hardBoundary = -1;
        java.util.regex.Matcher nextSchool = java.util.regex.Pattern.compile(
            "(?:^|\\n)(?:---+|\\*\\*\\d+\\.\\s|\\d+\\.\\s?\\*\\*|###\\s|【)").matcher(text);
        if (nextSchool.find(pos + schoolName.length())) {
            hardBoundary = nextSchool.start();
        }
        int end = (hardBoundary > pos && hardBoundary < rawEnd) ? hardBoundary : rawEnd;

        // 向前找到当前学校的起始（编号或段落开头）
        int start = pos;
        int prevBreak = text.lastIndexOf('\n', pos);
        if (prevBreak > 0 && pos - prevBreak < 200) {
            // 再往前找更早的段落边界（编号开头 | --- | 】
            int earlier = Math.max(0, pos - 50);
            String prefix = text.substring(earlier, pos);
            java.util.regex.Matcher prefixMatch = java.util.regex.Pattern.compile(
                "(?:\\n|^)((?:\\*\\*)?\\d+\\.\\s|【|[#*\\-—]{3,})").matcher(prefix);
            while (prefixMatch.find()) {
                int absPos = earlier + prefixMatch.start();
                if (absPos > start - 80 && absPos < start) {
                    start = absPos + 1; // 从标记之后开始
                }
            }
            // 回退到上一个句号+换行处，确保句子完整
            int sentenceStart = text.lastIndexOf("。\n", pos);
            if (sentenceStart > start && sentenceStart < pos - 10) start = sentenceStart + 1;
        }

        String snippet = text.substring(Math.max(0, start), end).trim();
        // 清理 Markdown
        snippet = snippet.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        snippet = snippet.replaceAll("\\*([^*]+)\\*", "$1");
        snippet = snippet.replaceAll("`([^`]+)`", "$1");
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
                String discussedJson = redisTemplate.opsForValue().get(AiConstants.keyDiscussed(conversationId));
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
            } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
        }

        // 来源 2（兜底）：仅 ai:discussed 为空时才读 ai:searched
        if (discussedIds.isEmpty() && conversationId != null) {
            try {
                String searchedJson = redisTemplate.opsForValue().get(AiConstants.keySearched(conversationId));
                if (searchedJson != null && !searchedJson.isBlank()) {
                    @SuppressWarnings("unchecked")
                    List<Long> ids = JSON.parseArray(searchedJson, Long.class);
                    if (ids != null && !ids.isEmpty()) {
                        discussedIds.addAll(ids);
                        fillSource = "auto_fill_search";
                    }
                }
            } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
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
            } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
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
            } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
        }

        final String finalSource = fillSource != null ? fillSource : "auto_fill_search";
        final boolean isDiscussed = "auto_fill_discussed".equals(finalSource);

        List<AiBookmark> result = new ArrayList<>();
        for (Long pid : discussedIds) {
            Map<String, Object> row = poolMap.get(pid);
            if (row == null) continue;
            // 从 pool 提取结构化数据
            String schoolName = String.valueOf(row.getOrDefault("schoolName", ""));
            String programName = String.valueOf(row.getOrDefault("programName", ""));
            Integer gapVal = row.get("gap") instanceof Number n ? n.intValue() : 0;
            int gap = gapVal != null ? gapVal : 0;
            Object avgObj = row.get("avgAdmittedScore");
            int avg = avgObj instanceof Number n ? n.intValue() : 0;
            Object quotaObj = row.get("planCount");
            int quota = quotaObj instanceof Number n ? n.intValue() : 0;
            boolean canBeSafe = row.get("canBeSafe") instanceof Boolean b && b;
            String tierLabel = AiRecommendationTools.tierDisplayLabel(row.get("schoolTier"));
            String city = String.valueOf(row.getOrDefault("city", ""));
            String gapLabel = AiRecommendationTools.gapLabel(gap);
            String quotaLabel = AiRecommendationTools.quotaLabel(quota);

            // ★ 后端统一裁决：不再自己按 gap 算 judgement
            AiRecommendationSafety.JudgementResult ruling =
                AiRecommendationSafety.finalJudgement(row, null);
            String judgement = ruling.finalJudgement();

            AiBookmark bm = new AiBookmark();
            bm.setProgramId(pid);
            bm.setSchoolName(schoolName);
            bm.setProgramName(programName);
            bm.setJudgement(judgement);
            bm.setFinalJudgement(ruling.finalJudgement());
            bm.setAdjusted(false); // autoFill 没有 AI judgement，不存在调整

            // ── 结构化 reason（不再截取 AI 文本）──
            StringBuilder reasonBuilder = new StringBuilder();
            reasonBuilder.append(avg > 0
                ? String.format("录取均分%d，%s，%s", avg, gapLabel, quotaLabel)
                : String.format("%s，%s", gapLabel, quotaLabel));
            if (tierLabel != null && !tierLabel.isBlank() && !"其他".equals(tierLabel)) {
                reasonBuilder.append("，").append(tierLabel);
            }
            if (city != null && !city.isBlank()) {
                reasonBuilder.append("，").append(city);
            }
            if (canBeSafe) {
                reasonBuilder.append("，可保底");
            }
            bm.setReason(reasonBuilder.toString());

            // ── 结构化 pros ──
            List<String> pros = new ArrayList<>();
            int absGap = Math.abs(gap);
            if (gap > 14) pros.add(gapLabel + "，分数充裕");
            else if (gap > 5) pros.add(gapLabel + "，分数有余量");
            else if (gap > 0) pros.add(gapLabel + "，分数勉强够");
            if (canBeSafe) pros.add("可保底");
            if (quota > 20) pros.add(quotaLabel + "，名额充裕");
            else if (quota > 9) pros.add(quotaLabel + "，名额正常");
            if (tierLabel != null && (tierLabel.contains("211") || tierLabel.contains("双一流") || tierLabel.contains("985"))) {
                pros.add(tierLabel + "平台");
            }
            if (city != null && !city.isBlank()) pros.add(city + "地域");
            bm.setPros(pros);

            // ── 结构化 cons ──
            List<String> cons = new ArrayList<>();
            if (!canBeSafe) cons.add("条件不满足，不能作为保底");
            if (quota <= 3) cons.add(quotaLabel + "，名额风险极高");
            else if (quota <= 9) cons.add(quotaLabel + "，名额偏少");
            if (gap <= -10) cons.add(gapLabel + "，分数风险高");
            else if (gap < 0) cons.add(gapLabel + "，分数有压力");
            bm.setCons(cons);

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
        List<CandidateProgramDTO> pool = aiCandidatePoolService.buildAgentPool(estimatedScore, regions);

        // 4. Serialize pool data for Redis
        String poolJson = JSON.toJSONString(pool);

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
            AiConstants.keyAgentPool(reportId), poolJson, AiConstants.TTL_AGENT_POOL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(
            AiConstants.keyAnalyzePool(reportId), poolJson, AiConstants.TTL_AGENT_POOL_HOURS, TimeUnit.HOURS);

        // 7. Send MQ message (lightweight: no prompt in message)
        if (rabbitTemplate != null)
        {
            // 延长候选池 TTL，防止 MQ 消费时已过期
            redisTemplate.expire(AiConstants.keyAgentPool(reportId), AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);
            redisTemplate.expire(AiConstants.keyAnalyzePool(reportId), AiConstants.TTL_REPORT, AiConstants.TTL_REPORT_UNIT);

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
        String cached = redisTemplate.opsForValue().get(AiConstants.keyReport(reportId));
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
                } catch (Exception ex) {
                    log.warn("[ai] buildFromBookmarks parse failed, using raw JSON", ex);
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
        String owner = redisTemplate.opsForValue().get(AiConstants.keyOwner(conversationId));
        if (owner == null || !owner.equals(userId.toString())) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("bookmarks", Collections.emptyList());
            err.put("count", 0);
            return err;
        }
        String json = redisTemplate.opsForValue().get(AiConstants.keyBookmarks(conversationId));
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
        // ── Redis 路径：数据仍在 ──
        String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
        if (convJson != null) {
            String convOwner = redisTemplate.opsForValue().get(AiConstants.keyOwner(conversationId));
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

            // 刷新全部四个 key 的 TTL
            redisTemplate.expire(AiConstants.keyConv(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
            redisTemplate.expire(AiConstants.keyPool(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
            redisTemplate.expire(AiConstants.keyBookmarks(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
            redisTemplate.expire(AiConstants.keyOwner(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversationId", conversationId);
            result.put("message", lastMessage);
            result.put("options", options);
            result.put("source", "redis");
            return result;
        }

        // ── DB 路径：Redis 已过期，从 DB 恢复 ──
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

                // 1. 恢复 conv 到 Redis
                redisTemplate.opsForValue().set(AiConstants.keyConv(conversationId), dbState, AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

                // 2. 恢复 bookmarks 到 Redis
                String savedBookmarksJson = state != null ? (String) state.get("bookmarksJson") : null;
                if (savedBookmarksJson != null && !savedBookmarksJson.isBlank()) {
                    redisTemplate.opsForValue().set(AiConstants.keyBookmarks(conversationId),
                        savedBookmarksJson, AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
                }

                // 3. 重建 pool（如果 Redis 中不存在）
                String poolJson = redisTemplate.opsForValue().get(AiConstants.keyPool(conversationId));
                if (poolJson == null || poolJson.isBlank()) {
                    try {
                        Map<String, Object> profile = loadUserProfile(userId);
                        int estimatedScore = getEstimatedScore(Collections.emptyMap(), profile);
                        String regionsStr = formatProfileField(profile, "targetRegions", "不限");
                        List<String> regions = parseRegionsForAnalysis(regionsStr);
                        List<CandidateProgramDTO> pool = aiCandidatePoolService.buildAgentPool(estimatedScore, regions);
                        redisTemplate.opsForValue().set(AiConstants.keyPool(conversationId),
                            JSON.toJSONString(pool), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
                        log.info("[Resume] Rebuilt pool for conv={}, size={}", conversationId, pool.size());
                    } catch (Exception e) {
                        log.warn("[Resume] Failed to rebuild pool for conv={}: {}", conversationId, e.getMessage());
                    }
                }

                // 4. 设置 owner
                redisTemplate.opsForValue().set(AiConstants.keyOwner(conversationId), userId.toString(), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

                // 5. 刷新全部 TTL
                redisTemplate.expire(AiConstants.keyConv(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
                redisTemplate.expire(AiConstants.keyPool(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
                redisTemplate.expire(AiConstants.keyBookmarks(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
                redisTemplate.expire(AiConstants.keyOwner(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("conversationId", conversationId);
                result.put("message", "已恢复上次对话");
                result.put("options", options);
                result.put("source", "db");
                return result;
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("status", "conversation_expired");
                err.put("canRestart", true);
                err.put("message", "对话已过期，请开始新对话");
                return err;
            }
        }

        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", "conversation_expired");
        err.put("canRestart", true);
        err.put("message", "对话已过期，请开始新对话");
        return err;
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

    private List<Map<String, Object>> buildSummaryList(List<CandidateProgramDTO> pool, int estimatedScore) {
        List<Map<String, Object>> summary = new ArrayList<>();
        if (pool == null) return summary;
        for (CandidateProgramDTO p : pool) {
            Map<String, Object> item = p.toMap();
            // Override computed fields
            Integer avg = p.getAvgAdmittedScore();
            item.put("avgAdmittedScore", avg);
            item.put("gap", avg != null ? (estimatedScore - avg) : null);
            item.put("dataCompleteness", AiRecommendationSafety.computedCompleteness(item));
            Map<String, Object> guard = AiRecommendationSafety.safeEligibility(item, estimatedScore);
            item.put("quotaRisk", guard.get("quotaRisk"));
            item.put("canBeSafe", guard.get("canBeSafe"));
            if (guard.get("safeBlockReason") != null) item.put("safeBlockReason", guard.get("safeBlockReason"));
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
        return AiRecommendationTools.tierDisplayLabel(value);
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

        redisTemplate.opsForValue().set(AiConstants.keyConv(conversationId), JSON.toJSONString(messages), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
        redisTemplate.expire(AiConstants.keyPool(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);
        redisTemplate.expire(AiConstants.keyOwner(conversationId), AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

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
            // 书签一起持久化，避免 Redis 过期后丢失
            String bookmarkJson = redisTemplate.opsForValue().get(AiConstants.keyBookmarks(conversationId));
            if (bookmarkJson != null && !bookmarkJson.isBlank()) {
                state.put("bookmarksJson", bookmarkJson);
            }
            log.setResultJson(JSON.toJSONString(state));
            log.setRuleVersion("ai-conversation-state");
            log.setDataVersion("1.0");
            log.setIsPaid(0);
            logMapper.insertRecommendationLog(log);
        } catch (Exception ex) {
            log.warn("[ai] persistStreamConversation failed", ex);
        }
    }

    /** 从 Redis 读取当前 conv + bookmarks 并持久化到 DB（书签变更时调用） */
    private void persistStateFromRedis(String conversationId) {
        try {
            String owner = redisTemplate.opsForValue().get(AiConstants.keyOwner(conversationId));
            if (owner == null) return;
            String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
            if (convJson == null || convJson.isBlank()) return;
            String bookmarkJson = redisTemplate.opsForValue().get(AiConstants.keyBookmarks(conversationId));
            RecommendationLog log = new RecommendationLog();
            log.setUserId(Long.parseLong(owner));
            log.setProfileSnapshot(JSON.toJSONString(Map.of("userId", owner)));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("conversationId", conversationId);
            state.put("messages", JSON.parseArray(convJson, Map.class));
            if (bookmarkJson != null && !bookmarkJson.isBlank()) {
                state.put("bookmarksJson", bookmarkJson);
            }
            state.put("savedAt", System.currentTimeMillis());
            log.setResultJson(JSON.toJSONString(state));
            log.setRuleVersion("ai-conversation-state");
            log.setDataVersion("1.0");
            log.setIsPaid(0);
            logMapper.insertRecommendationLog(log);
        } catch (Exception ex) {
            log.warn("[ai] persistStreamConversation failed", ex);
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
