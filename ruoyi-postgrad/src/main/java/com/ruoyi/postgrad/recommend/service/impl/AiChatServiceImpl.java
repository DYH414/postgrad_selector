package com.ruoyi.postgrad.recommend.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.ChatStartResultVO;
import com.ruoyi.postgrad.recommend.service.ChatStreamCallback;
import com.ruoyi.postgrad.recommend.service.IAiChatService;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;

/**
 * AI 对话服务实现 —— SSE 流式对话 + 草稿调整意图解析。
 * <p>工具调用通过 {@link V2ChatTools} 实现，上下文由 {@link V2ChatToolContext} 管理。
 * 对话不直接操作草稿——AI 解析用户意图后返回 DraftAction，由前端调用 DraftService API 执行。</p>
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

    /** 对话历史最大消息数 */
    private static final int MAX_CHAT_MESSAGES = 20;

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

    // TODO: 注入 IDraftService（读取草稿上下文以构建系统提示词）

    /**
     * LangChain4j AiServices 流式对话接口。
     * <p>声明在这里（非独立文件），因为它是 AiChatServiceImpl 的内部实现细节。</p>
     */
    private interface StreamAssistant {
        dev.langchain4j.service.TokenStream chat(String message);
    }

    @Override
    public ChatStartResultVO startChat(Long userId) {
        // TODO: 加载用户画像 + 当前草稿 → 构建系统提示词 → 保存到 Redis → 返回 ChatStartResultVO
        throw new UnsupportedOperationException("TODO: implement startChat");
    }

    @Override
    public void chat(Long userId, String message, ChatStreamCallback callback) {
        // TODO: 流式对话，通过 callback 推送 token 和 done
        //
        // 关键实现步骤：
        // 1. 从 Redis 加载对话历史（ai:v2:chat:msg:{userId}）
        // 2. 构建 ChatMemory（MessageWindowChatMemory.withMaxMessages(20)）
        // 3. 构建系统提示词（含用户画像 + 当前草稿上下文）
        // 4. V2ChatToolContext.init(userId, redisTemplate, recommendationMapper)
        // 5. 创建 AiServices 流式 assistant：
        //    AiServices.builder(StreamAssistant.class)
        //        .streamingChatModel(streamingChatModel)
        //        .tools(v2ChatTools)           ← 新工具集，不复用旧 AiRecommendationTools
        //        .chatMemory(chatMemory)
        //        .systemMessageProvider(...)
        //        .build()
        // 6. TokenStream 配置：
        //    - onPartialResponse → callback.onToken(token)
        //    - onCompleteResponse → 解析 AI 回复中的 ---ACTION--- JSON
        //    - 构建 DraftAction → callback.onDone(fullMessage, draftAction)
        //    - onError → callback.onError(e)
        // 7. finally: V2ChatToolContext.clear()
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
     */
    private String chatKey(Long userId) {
        return CHAT_KEY_PREFIX + userId;
    }

    /**
     * 构建对话消息历史的 Redis key。
     */
    private String chatMsgKey(Long userId) {
        return CHAT_MSG_KEY_PREFIX + userId;
    }

    /**
     * 加载对话系统提示词，注入用户画像和当前草稿上下文。
     */
    private String buildSystemPrompt(Long userId) {
        // TODO: 读取 prompt 模板 → 填充画像字段 → 填充草稿摘要
        return "";
    }
}
