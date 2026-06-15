package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.ChatStartResultVO;

/**
 * AI 对话服务 —— SSE 流式对话 + 后端工具调用调整草稿。
 * <p>草稿写操作只能由 AI 原生调用后端 tool 完成，不返回前端动作指令执行。</p>
 */
public interface IAiChatService {

    /**
     * 开始/重置对话。
     *
     * @param userId 当前用户 ID
     * @return 对话 ID + 初始消息 + 快捷选项
     */
    ChatStartResultVO startChat(Long userId);

    /**
     * 流式对话（通过回调推送 token，不依赖 web 层）。
     * <p>回调事件序列：onToken(...) → ... → onDone(message, draftChanged, toolActionResult) | onError(throwable)。</p>
     *
     * @param userId   当前用户 ID
     * @param message  用户消息文本
     * @param callback 流式回调（由 Controller 层实现，桥接到 SSE）
     */
    void chat(Long userId, String message, ChatStreamCallback callback);

    /**
     * 终结当前活跃对话（status: active → finalized）。
     * <p>新草稿生成时调用，避免旧对话上下文污染新草稿。</p>
     */
    void finalizeConversation(Long userId);

    /**
     * 恢复对话（Redis 未过期时）。
     *
     * @param userId 当前用户 ID
     * @return 对话状态 + 最近消息 + 数据来源标记
     */
    ChatStartResultVO resumeChat(Long userId);
}
