package com.ruoyi.postgrad.recommend.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
     * SSE 流式对话。
     * <p>事件序列：token(...) → token(...) → done(message, draftAction)。</p>
     *
     * @param userId  当前用户 ID
     * @param message 用户消息文本
     * @return SseEmitter（由 Controller 返回给前端）
     */
    SseEmitter chat(Long userId, String message);

    /**
     * 恢复对话（Redis 未过期时）。
     *
     * @param userId 当前用户 ID
     * @return 对话状态 + 最近消息 + 数据来源标记
     */
    ChatStartResultVO resumeChat(Long userId);
}
