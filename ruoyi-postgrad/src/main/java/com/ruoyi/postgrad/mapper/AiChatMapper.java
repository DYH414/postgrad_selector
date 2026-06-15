package com.ruoyi.postgrad.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.postgrad.recommend.domain.AiChatConversation;
import com.ruoyi.postgrad.recommend.domain.AiChatMessage;

public interface AiChatMapper {
    AiChatConversation selectActiveConversation(@Param("userId") Long userId);

    int finalizeActiveConversations(@Param("userId") Long userId);

    int insertConversation(AiChatConversation conversation);

    int touchConversation(@Param("id") Long id);

    Integer selectNextSeq(@Param("conversationId") Long conversationId);

    int insertMessage(AiChatMessage message);

    List<AiChatMessage> selectMessages(@Param("conversationId") Long conversationId,
                                       @Param("limit") Integer limit);
}
