package com.ruoyi.postgrad.recommend.service;

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
     * 工具调用通知（可选择实现）。
     *
     * @param toolName 工具名称（如 searchPrograms / addDraftCandidate）
     */
    default void onToolCall(String toolName) {
    }

    /**
     * 对话完成。
     *
     * @param fullMessage AI 完整回复文本。
     */
    default void onDone(String fullMessage) {
    }

    /**
     * 对话完成。
     *
     * @param fullMessage AI 完整回复文本。
     * @param draftChanged 是否真实触发了草稿写操作
     * @param toolActionResultJson 工具执行结果 JSON
     */
    default void onDone(String fullMessage, boolean draftChanged, String toolActionResultJson) {
        onDone(fullMessage);
    }

    /**
     * 对话出错。
     *
     * @param error 异常
     */
    void onError(Throwable error);
}
