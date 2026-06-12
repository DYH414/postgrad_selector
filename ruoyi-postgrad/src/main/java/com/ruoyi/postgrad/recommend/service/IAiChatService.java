package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.ChatStartResultVO;

/**
 * AI 对话服务 —— SSE 流式对话 + 草稿调整意图解析。
 * <p>对话不直接操作草稿。AI 解析用户意图后返回 DraftAction，由前端调用 DraftService API 执行。</p>
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
     * <p>回调事件序列：onToken(...) → ... → onDone(message, draftAction) | onError(throwable)。</p>
     *
     * @param userId   当前用户 ID
     * @param message  用户消息文本
     * @param callback 流式回调（由 Controller 层实现，桥接到 SSE）
     */
    void chat(Long userId, String message, ChatStreamCallback callback);

    /**
     * 恢复对话（Redis 未过期时）。
     *
     * @param userId 当前用户 ID
     * @return 对话状态 + 最近消息 + 数据来源标记
     */
    ChatStartResultVO resumeChat(Long userId);
}
