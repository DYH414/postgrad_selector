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
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.ChatMessageVO;
import com.ruoyi.postgrad.recommend.domain.ChatStartResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftAction;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
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

    private interface StreamAssistant {
        dev.langchain4j.service.TokenStream chat(String message);
    }

    @Override
    public ChatStartResultVO startChat(Long userId) {
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
        result.setConversationId(userId.toString());
        result.setMessage("你好！我已了解你的画像和当前草稿。你可以问我：为什么推荐某所学校、某校的风险是什么、或者让我帮忙对比两所学校。");
        result.setOptions(List.of("分析草稿中的学校", "对比两所学校", "解释推荐理由", "推荐替代选择"));
        result.setSource("new");
        return result;
    }

    @Override
    public void chat(Long userId, String message, ChatStreamCallback callback) {
        // 1. 加载/初始化对话历史
        List<ChatMessageVO> history = loadHistory(userId);

        // 2. 提取系统提示词
        String systemPrompt = "";
        for (ChatMessageVO m : history) {
            if ("system".equals(m.getRole())) {
                systemPrompt = m.getContent();
                break;
            }
        }
        if (systemPrompt.isEmpty()) {
            systemPrompt = buildSystemPrompt(userId);
        }

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
                        String actionJson = aiText.substring(actionIdx + ACTION_DELIMITER.length()).trim();
                        try {
                            action = JSON.parseObject(actionJson, DraftAction.class);
                        } catch (Exception e) {
                            log.warn("[AiChat] Failed to parse DraftAction: {}", actionJson);
                        }
                    }

                    // 保存 AI 回复
                    history.add(new ChatMessageVO("assistant", displayText));
                    saveHistory(userId, history);

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
        String systemPrompt = redisTemplate.opsForValue().get(chatKey(userId));
        if (systemPrompt == null) {
            ChatStartResultVO expired = new ChatStartResultVO();
            expired.setConversationId(userId.toString());
            expired.setMessage("对话已过期，请开始新对话");
            expired.setSource("expired");
            return expired;
        }

        // 刷新 TTL
        redisTemplate.expire(chatKey(userId), CHAT_TTL);
        redisTemplate.expire(chatMsgKey(userId), CHAT_TTL);

        ChatStartResultVO result = new ChatStartResultVO();
        result.setConversationId(userId.toString());
        result.setMessage("对话已恢复");
        result.setSource("redis");
        return result;
    }

    // ── private helpers ──

    private String chatKey(Long userId) { return CHAT_KEY_PREFIX + userId; }
    private String chatMsgKey(Long userId) { return CHAT_MSG_KEY_PREFIX + userId; }

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
     * 构建系统提示词：加载模板 + 注入用户草稿上下文。
     */
    private String buildSystemPrompt(Long userId) {
        String template = loadPromptTemplate();
        DraftVO draft = draftService.getDraft(userId);
        String draftCtx = buildDraftContextText(draft);

        // 简单替换占位符
        return template
            .replace("{draftContext}", draftCtx)
            .replace("{draftSummary}", draftCtx.isEmpty() ? "尚未生成草稿" : draftCtx);
    }

    private String loadPromptTemplate() {
        try {
            return new String(chatSystemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[AiChat] Failed to load chat system prompt: {}", e.getMessage());
            return "你是考研择校助手，帮助用户分析和调整择校草稿。\n当前草稿：{draftContext}";
        }
    }

    private String buildDraftContextText(DraftVO draft) {
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
                sb.append("  - ID:").append(f.getProgramId())
                    .append(" ").append(f.getSchoolName())
                    .append(" ").append(f.getProgramName());
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
