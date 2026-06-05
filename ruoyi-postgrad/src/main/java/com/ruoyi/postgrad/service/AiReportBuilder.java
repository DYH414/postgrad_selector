package com.ruoyi.postgrad.service;

import dev.langchain4j.model.chat.ChatModel;
import java.util.Map;

public interface AiReportBuilder {
    Map<String, Object> buildConversationReport(ChatModel chatModel, String conversationJson,
        String poolJson, int estimatedScore, Map<String, Object> preferenceProfile);

    Map<String, Object> buildAnalyzeReport(ChatModel chatModel, String poolJson,
        int estimatedScore, Map<String, Object> preferenceProfile);

    Map<String, Object> buildFromBookmarks(String bookmarkJson, String poolJson,
        int estimatedScore);
}
