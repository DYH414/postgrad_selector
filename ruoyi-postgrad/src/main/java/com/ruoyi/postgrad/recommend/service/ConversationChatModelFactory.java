package com.ruoyi.postgrad.recommend.service;

import dev.langchain4j.model.chat.StreamingChatModel;

@FunctionalInterface
public interface ConversationChatModelFactory {
    StreamingChatModel forConversation(long conversationId);
}
