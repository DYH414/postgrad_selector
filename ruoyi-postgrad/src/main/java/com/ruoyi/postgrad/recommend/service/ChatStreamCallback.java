package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.DraftAction;

/**
 * 对话流式回调 —— Service 层不依赖 web 层（SseEmitter），
 * 由 Controller 实现此接口，将 token / done / error 事件桥接到 SSE。
 */
public interface ChatStreamCallback {

    /**
     * 推送单个 token。
     *
     * @param token 文本片段
     */
    void onToken(String token);

    /**
     * 对话完成。
     *
     * @param fullMessage AI 完整回复文本（不含 ACTION 分隔部分）
     * @param draftAction AI 解析出的草稿操作指令（可能为 null 或 type=none）
     */
    void onDone(String fullMessage, DraftAction draftAction);

    /**
     * 对话出错。
     *
     * @param error 异常
     */
    void onError(Throwable error);
}
