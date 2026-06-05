package com.ruoyi.postgrad.service;

import java.util.Map;

/**
 * AI recommendation conversation service.
 * Manages AI chat sessions for postgraduate school selection guidance.
 */
public interface IAiRecommendationService
{
    /**
     * Start a new AI conversation session.
     *
     * @param userId  the current user ID
     * @param request request params containing candidateIds, estimatedScore, etc.
     * @return map with conversationId, message, options
     */
    Map<String, Object> startConversation(Long userId, Map<String, Object> request);

    /**
     * Send a message in an existing conversation and get AI response.
     *
     * @param userId         the current user ID (for ownership verification)
     * @param conversationId the conversation session ID
     * @param message        user message text
     * @return map with message, options
     */
    Map<String, Object> chat(Long userId, String conversationId, String message);

    /**
     * Send a message and stream the AI response token by token.
     *
     * @param userId         the current user ID (for ownership verification)
     * @param conversationId the conversation session ID
     * @param message        user message text
     * @param callback       streaming callbacks for token/done/error events
     */
    void chatStream(Long userId, String conversationId, String message, StreamCallback callback);

    /**
     * Trigger AI report generation for a completed conversation.
     *
     * @param userId         the current user ID
     * @param conversationId the conversation session ID
     * @return map with reportId, status
     */
    Map<String, Object> generateReport(Long userId, String conversationId);

    /**
     * Get a specific AI-generated report by its ID.
     *
     * @param userId   the current user ID
     * @param reportId the report log ID
     * @return map with report data or status
     */
    Map<String, Object> getReport(Long userId, Long reportId);

    /**
     * Get all AI reports for the current user.
     *
     * @param userId the current user ID
     * @return map with reports list
     */
    Map<String, Object> getReports(Long userId);

    /**
     * Resume an existing conversation from Redis or DB snapshot.
     *
     * @param userId         the current user ID
     * @param conversationId the conversation session ID
     * @return map with conversationId, message, options, source
     */
    Map<String, Object> resumeConversation(Long userId, String conversationId);

    /**
     * One-shot recommendation: analyze schools directly from user profile
     * without requiring a conversation. Generates report via MQ.
     *
     * @param userId the current user ID
     * @return map with reportId, msg
     */
    Map<String, Object> analyze(Long userId);

    /** 获取当前对话的书签列表（供前端展示） */
    Map<String, Object> getBookmarks(Long userId, String conversationId);

    interface StreamCallback {
        /** Progress event sent before a tool is about to be executed. */
        default void onThinking(String message) {}

        void onToken(String token);

        void onComplete(Map<String, Object> result);

        void onError(Throwable error);
    }
}
