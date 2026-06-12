package com.ruoyi.postgrad.recommend.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ruoyi.postgrad.recommend.domain.ChatStartResultVO;
import com.ruoyi.postgrad.recommend.service.IAiChatService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/**
 * AI 对话服务实现 —— SSE 流式对话 + 草稿调整意图解析。
 *
 * <p>TODO: 实现 startChat / chat / resumeChat</p>
 */
@Service
public class AiChatServiceImpl implements IAiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    /** Redis key 前缀：对话会话元数据 */
    private static final String CHAT_KEY_PREFIX = "ai:v2:chat:";

    /** Redis key 前缀：对话消息历史 */
    private static final String CHAT_MSG_KEY_PREFIX = "ai:v2:chat:msg:";

    @Value("classpath:prompts/v2/chat-system.txt")
    private org.springframework.core.io.Resource chatSystemPromptResource;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // TODO: 注入 IDraftService（读取草稿上下文以构建系统提示词）

    @Override
    public ChatStartResultVO startChat(Long userId) {
        // TODO: 加载用户画像 + 当前草稿 → 构建系统提示词 → 保存到 Redis → 返回初始消息
        throw new UnsupportedOperationException("TODO: implement startChat");
    }

    @Override
    public SseEmitter chat(Long userId, String message) {
        // TODO: SSE 流式对话
        // 1. 从 Redis 加载对话历史
        // 2. 构建 ChatMemory + 系统提示词（含当前草稿上下文）
        // 3. 创建 AiServices 流式 assistant
        // 4. 流式返回 token
        // 5. 解析 AI 回复中的 ---ACTION--- 分隔部分
        // 6. 构建 DraftAction
        // 7. 推送 done(message, draftAction)
        throw new UnsupportedOperationException("TODO: implement chat");
    }

    @Override
    public ChatStartResultVO resumeChat(Long userId) {
        // TODO: 从 Redis 恢复对话状态，刷新 TTL
        throw new UnsupportedOperationException("TODO: implement resumeChat");
    }

    // ── private helpers (to be added) ──

    /**
     * 构建对话会话的 Redis key。
     *
     * @param userId 用户 ID
     * @return Redis key
     */
    private String chatKey(Long userId) {
        return CHAT_KEY_PREFIX + userId;
    }

    /**
     * 构建对话消息历史的 Redis key。
     *
     * @param userId 用户 ID
     * @return Redis key
     */
    private String chatMsgKey(Long userId) {
        return CHAT_MSG_KEY_PREFIX + userId;
    }
}
