package com.ruoyi.postgrad.recommend.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.postgrad.mapper.AiChatMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.AiChatConversation;
import com.ruoyi.postgrad.recommend.domain.AiChatMessage;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.ChatMessageVO;
import com.ruoyi.postgrad.recommend.domain.ChatStartResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftAction;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.ChatStreamCallback;
import com.ruoyi.postgrad.recommend.service.IAiChatService;
import com.ruoyi.postgrad.recommend.service.IDraftService;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;

/**
 * AI 对话服务实现 —— SSE 流式对话 + 草稿调整意图解析。
 */
@Service
public class AiChatServiceImpl implements IAiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private static final String CHAT_KEY_PREFIX = "ai:v2:chat:";
    private static final String CHAT_MSG_KEY_PREFIX = "ai:v2:chat:msg:";
    private static final int MAX_MESSAGES = 20;
    private static final Duration CHAT_TTL = Duration.ofMinutes(30);
    private static final String ACTION_DELIMITER = "---ACTION---";

    @Value("classpath:prompts/v2/chat-system.txt")
    private org.springframework.core.io.Resource chatSystemPromptResource;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private V2ChatTools v2ChatTools;

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Autowired
    private IDraftService draftService;

    @Autowired
    private AiChatMapper aiChatMapper;

    private interface StreamAssistant {
        dev.langchain4j.service.TokenStream chat(String message);
    }

    @Override
    public ChatStartResultVO startChat(Long userId) {
        AiChatConversation conversation = getOrCreateActiveConversation(userId);

        // 清除旧对话历史
        redisTemplate.delete(chatMsgKey(userId));

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt(userId);

        // 保存系统提示词到 Redis（作为对话元数据）
        redisTemplate.opsForValue().set(chatKey(userId), systemPrompt, CHAT_TTL);

        // 保存初始系统消息到对话历史
        List<ChatMessageVO> messages = new ArrayList<>();
        messages.add(new ChatMessageVO("system", systemPrompt));
        redisTemplate.opsForValue().set(chatMsgKey(userId), JSON.toJSONString(messages), CHAT_TTL);

        ChatStartResultVO result = new ChatStartResultVO();
        result.setConversationId(String.valueOf(conversation.getId()));
        result.setMessage("你好！我已了解你的画像和当前草稿。你可以问我：为什么推荐某所学校、某校的风险是什么、或者让我帮忙对比两所学校。");
        result.setOptions(List.of("分析草稿中的学校", "对比两所学校", "解释推荐理由", "推荐替代选择"));
        result.setMessages(loadPersistedDisplayMessages(conversation.getId()));
        result.setSource("new");
        return result;
    }

    @Override
    public void chat(Long userId, String message, ChatStreamCallback callback) {
        AiChatConversation conversation = getOrCreateActiveConversation(userId);

        // 1. 加载/初始化对话历史
        List<ChatMessageVO> loadedHistory = loadHistory(userId);
        if (loadedHistory.isEmpty()) {
            loadedHistory = loadPersistedMemoryMessages(conversation.getId());
        }
        final List<ChatMessageVO> history = loadedHistory;

        // 2. 每轮都重建系统提示词，确保草稿调整后的档位和操作ID是最新的。
        String systemPrompt = buildSystemPrompt(userId);
        redisTemplate.opsForValue().set(chatKey(userId), systemPrompt, CHAT_TTL);

        // 3. 构建 ChatMemory
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES);
        for (ChatMessageVO m : history) {
            if ("system".equals(m.getRole())) continue;
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            if ("assistant".equals(m.getRole())) {
                chatMemory.add(AiMessage.from(m.getContent()));
            } else if ("user".equals(m.getRole())) {
                chatMemory.add(UserMessage.from(m.getContent()));
            }
        }

        // 4. 保存用户消息
        persistMessage(userId, conversation.getId(), "user", message, message);
        history.add(new ChatMessageVO("user", message));
        saveHistory(userId, history);

        final String finalSystemPrompt = systemPrompt;

        // 5. 初始化工具上下文 + 创建流式 assistant
        try {
            V2ChatToolContext.init(userId, redisTemplate, recommendationMapper);
            StreamAssistant assistant = AiServices.builder(StreamAssistant.class)
                .streamingChatModel(streamingChatModel)
                .tools(v2ChatTools)
                .chatMemory(chatMemory)
                .systemMessageProvider(ignored -> finalSystemPrompt)
                .build();

            StringBuilder fullResponse = new StringBuilder();
            assistant.chat("<user_input>" + message + "</user_input>")
                .onPartialResponse(token -> {
                    fullResponse.append(token);
                    callback.onToken(token);
                })
                .onCompleteResponse(response -> {
                    V2ChatToolContext.clear();
                    String aiText = response.aiMessage().text();
                    if (aiText == null || aiText.isBlank()) {
                        aiText = fullResponse.toString();
                    }

                    // 6. 解析 ---ACTION--- 分隔的 DraftAction
                    String displayText = aiText;
                    DraftAction action = null;
                    int actionIdx = aiText.indexOf(ACTION_DELIMITER);
                    if (actionIdx >= 0) {
                        displayText = aiText.substring(0, actionIdx).trim();
                        String actionText = aiText.substring(actionIdx + ACTION_DELIMITER.length()).trim();
                        action = parseDraftAction(actionText);
                        if (action == null) {
                            log.warn("[AiChat] Failed to parse DraftAction: {}", actionText);
                        }
                    }
                    if (action == null) {
                        action = inferDraftActionFromDisplayText(displayText, draftService.getDraft(userId));
                        if (action != null) {
                            log.info("[AiChat] Inferred DraftAction from assistant text: type={}, programId={}",
                                action.getType(), action.getProgramId());
                        }
                    }

                    // 保存 AI 回复
                    history.add(new ChatMessageVO("assistant", displayText));
                    saveHistory(userId, history);
                    persistMessage(userId, conversation.getId(), "assistant", aiText, displayText);

                    callback.onDone(displayText, action);
                })
                .onError(error -> {
                    V2ChatToolContext.clear();
                    log.error("[AiChat] Stream error for userId={}: {}", userId, error.getMessage());
                    callback.onError(error);
                })
                .start();
        } catch (Exception e) {
            V2ChatToolContext.clear();
            log.error("[AiChat] Failed to start stream for userId={}: {}", userId, e.getMessage());
            callback.onError(e);
        }
    }

    @Override
    public ChatStartResultVO resumeChat(Long userId) {
        AiChatConversation conversation = getOrCreateActiveConversation(userId);

        // 刷新 TTL
        String systemPrompt = redisTemplate.opsForValue().get(chatKey(userId));
        if (systemPrompt == null) {
            redisTemplate.opsForValue().set(chatKey(userId), buildSystemPrompt(userId), CHAT_TTL);
        }
        redisTemplate.expire(chatKey(userId), CHAT_TTL);
        redisTemplate.expire(chatMsgKey(userId), CHAT_TTL);

        ChatStartResultVO result = new ChatStartResultVO();
        result.setConversationId(String.valueOf(conversation.getId()));
        result.setMessage("对话已恢复");
        result.setSource("db");
        result.setMessages(loadPersistedDisplayMessages(conversation.getId()));
        return result;
    }

    // ── private helpers ──

    private String chatKey(Long userId) { return CHAT_KEY_PREFIX + userId; }
    private String chatMsgKey(Long userId) { return CHAT_MSG_KEY_PREFIX + userId; }

    private AiChatConversation getOrCreateActiveConversation(Long userId) {
        AiChatConversation conversation = aiChatMapper.selectActiveConversation(userId);
        if (conversation != null) return conversation;

        conversation = new AiChatConversation();
        conversation.setUserId(userId);
        conversation.setDraftKey("active:" + userId);
        conversation.setTitle("AI 择校对话");
        conversation.setStatus("active");
        aiChatMapper.insertConversation(conversation);
        return conversation;
    }

    private void persistMessage(Long userId, Long conversationId, String role, String content, String displayContent) {
        AiChatMessage message = new AiChatMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setDisplayContent(displayContent != null ? displayContent : content);
        message.setMessageType("text");
        message.setStatus("completed");
        Integer seq = aiChatMapper.selectNextSeq(conversationId);
        message.setSeq(seq != null ? seq : 1);
        message.setMetadataJson(null);
        aiChatMapper.insertMessage(message);
        aiChatMapper.touchConversation(conversationId);
    }

    private List<ChatMessageVO> loadPersistedDisplayMessages(Long conversationId) {
        List<AiChatMessage> rows = aiChatMapper.selectMessages(conversationId, 200);
        List<ChatMessageVO> result = new ArrayList<>();
        for (AiChatMessage row : rows) {
            if ("system".equals(row.getRole())) continue;
            ChatMessageVO vo = new ChatMessageVO(row.getRole(),
                row.getDisplayContent() != null ? row.getDisplayContent() : row.getContent());
            vo.setMessageType(row.getMessageType());
            vo.setStatus(row.getStatus());
            vo.setSeq(row.getSeq());
            vo.setMetadataJson(row.getMetadataJson());
            result.add(vo);
        }
        return result;
    }

    private List<ChatMessageVO> loadPersistedMemoryMessages(Long conversationId) {
        List<AiChatMessage> rows = aiChatMapper.selectMessages(conversationId, MAX_MESSAGES);
        List<ChatMessageVO> result = new ArrayList<>();
        for (AiChatMessage row : rows) {
            if ("system".equals(row.getRole())) continue;
            if (!"user".equals(row.getRole()) && !"assistant".equals(row.getRole())) continue;
            String content = row.getDisplayContent() != null ? row.getDisplayContent() : row.getContent();
            if (content == null || content.isBlank()) continue;
            result.add(new ChatMessageVO(row.getRole(), content));
        }
        return result;
    }

    static DraftAction parseDraftAction(String actionText) {
        String actionJson = firstJsonObject(actionText);
        if (actionJson == null) return null;
        try {
            return JSON.parseObject(actionJson, DraftAction.class);
        } catch (Exception e) {
            return null;
        }
    }

    static DraftAction inferDraftActionFromDisplayText(String displayText, DraftVO draft) {
        if (!hasRemoveIntent(displayText) || draft == null || draft.getTiers() == null) {
            return null;
        }

        CandidateCardVO lastMentioned = null;
        String[] lines = displayText.split("\\R");
        for (String line : lines) {
            if (line == null || line.isBlank() || isAlreadyAbsentLine(line)) {
                continue;
            }
            for (TierCandidates tier : draft.getTiers()) {
                if (tier.getCandidates() == null) continue;
                for (CandidateCardVO candidate : tier.getCandidates()) {
                    if (!mentionsCandidate(line, candidate)) continue;
                    if (hasRemoveIntent(line)) {
                        return removeAction(candidate);
                    }
                    lastMentioned = candidate;
                }
            }
        }

        return lastMentioned != null ? removeAction(lastMentioned) : null;
    }

    private static boolean hasRemoveIntent(String text) {
        if (text == null || text.isBlank()) return false;
        String[] terms = {"移除", "移出", "删除", "删掉", "去掉", "剔除", "拿掉", "移走"};
        for (String term : terms) {
            int idx = text.indexOf(term);
            while (idx >= 0) {
                if (!isNegatedAction(text, idx)) {
                    return true;
                }
                idx = text.indexOf(term, idx + term.length());
            }
        }
        return false;
    }

    private static boolean isNegatedAction(String text, int actionIndex) {
        int start = Math.max(0, actionIndex - 8);
        String prefix = text.substring(start, actionIndex);
        String[] negations = {"不要", "不用", "无需", "不必", "不能", "无法", "暂不", "先不", "别", "勿"};
        for (String negation : negations) {
            if (prefix.contains(negation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlreadyAbsentLine(String line) {
        return line.contains("已不在草稿")
            || line.contains("不在草稿中")
            || line.contains("已经不在草稿")
            || line.contains("不用管")
            || line.contains("无需处理");
    }

    private static boolean mentionsCandidate(String text, CandidateCardVO candidate) {
        if (candidate == null || candidate.getFact() == null) return false;
        SchoolFact fact = candidate.getFact();
        return containsNonBlank(text, fact.getSchoolName())
            || containsNonBlank(text, fact.getProgramName())
            || containsNonBlank(text, fact.getCollegeName());
    }

    private static boolean containsNonBlank(String text, String needle) {
        return text != null && needle != null && !needle.isBlank() && text.contains(needle);
    }

    private static DraftAction removeAction(CandidateCardVO candidate) {
        if (candidate == null || candidate.getFact() == null || candidate.getFact().getProgramId() == null) {
            return null;
        }
        DraftAction action = new DraftAction();
        action.setType("remove");
        action.setProgramId(candidate.getFact().getProgramId());
        return action;
    }

    private static String firstJsonObject(String text) {
        if (text == null || text.isBlank()) return null;

        String normalized = text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        }

        int start = normalized.indexOf('{');
        if (start < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return normalized.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessageVO> loadHistory(Long userId) {
        String json = redisTemplate.opsForValue().get(chatMsgKey(userId));
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return JSON.parseArray(json, ChatMessageVO.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveHistory(Long userId, List<ChatMessageVO> history) {
        redisTemplate.opsForValue().set(chatMsgKey(userId), JSON.toJSONString(history), CHAT_TTL);
    }

    /**
     * 构建系统提示词：加载模板 + 注入用户画像 + 草稿上下文。
     */
    private String buildSystemPrompt(Long userId) {
        String template = loadPromptTemplate();
        DraftVO draft = draftService.getDraft(userId);
        String draftCtx = buildDraftContextText(draft);
        String profileCtx = buildProfileContextText(draft);

        return template
            .replace("{profileContext}", profileCtx)
            .replace("{draftContext}", draftCtx);
    }

    /**
     * 从草稿的 ProfileBasisVO 中提取用户画像上下文。
     */
    private String buildProfileContextText(DraftVO draft) {
        if (draft == null || draft.getProfileBasis() == null) {
            return "用户画像未设置。";
        }
        var p = draft.getProfileBasis();
        StringBuilder sb = new StringBuilder();
        sb.append("预估分数：").append(p.getEstimatedScore() != null ? p.getEstimatedScore() : "未知").append(" 分\n");
        sb.append("本科层次：").append(p.getUndergradTier() != null ? p.getUndergradTier() : "未知").append("\n");
        sb.append("目标地区：").append(p.getTargetRegions() != null ? p.getTargetRegions() : "不限").append("\n");
        sb.append("跨专业：").append(p.getIsCrossMajor() != null ? p.getIsCrossMajor() : "否").append("\n");
        sb.append("风险偏好：").append(p.getRiskPreference() != null ? p.getRiskPreference() : "均衡").append("\n");
        sb.append("层次偏好：").append(p.getSchoolTierPreference() != null ? p.getSchoolTierPreference() : "不限").append("\n");
        sb.append("地区策略：").append(p.getRegionStrategy() != null ? p.getRegionStrategy() : "不限").append("\n");
        return sb.toString();
    }

    private String loadPromptTemplate() {
        try {
            return new String(chatSystemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[AiChat] Failed to load chat system prompt: {}", e.getMessage());
            return "你是考研择校助手，帮助用户分析和调整择校草稿。\n当前草稿：{draftContext}";
        }
    }

    static String buildDraftContextText(DraftVO draft) {
        if (draft == null || draft.getTiers() == null) return "尚未生成草稿。";

        StringBuilder sb = new StringBuilder();
        for (TierCandidates t : draft.getTiers()) {
            if (t.getCandidates() == null || t.getCandidates().isEmpty()) {
                sb.append(t.getLabel()).append("：暂无候选\n");
                continue;
            }
            sb.append(t.getLabel()).append("（").append(t.getCandidates().size())
                .append("/").append(t.getTargetCount()).append("）：\n");
            for (var c : t.getCandidates()) {
                var f = c.getFact();
                sb.append("  - ").append(f.getSchoolName())
                    .append(" ").append(f.getProgramName())
                    .append(" 【操作ID:").append(f.getProgramId()).append("】");
                if (f.getAvgAdmittedScore() != null) {
                    sb.append(" 均分").append(f.getAvgAdmittedScore());
                }
                if (f.getScoreGap() != null) {
                    sb.append(" 差距").append(f.getScoreGap() >= 0 ? "+" : "").append(f.getScoreGap());
                }
                if (c.getOpinion() != null && c.getOpinion().getReason() != null) {
                    sb.append(" 理由:").append(c.getOpinion().getReason());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
