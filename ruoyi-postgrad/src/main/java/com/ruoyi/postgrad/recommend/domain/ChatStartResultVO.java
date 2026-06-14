package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 对话开始/恢复结果 —— 对话 ID + 初始消息 + 快捷选项。
 */
public class ChatStartResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对话唯一标识（等于 userId，因为一个用户同时只有一个对话） */
    private String conversationId;

    /** AI 初始消息文本 */
    private String message;

    /** 快捷操作选项 */
    private List<String> options;

    private List<ChatMessageVO> messages;

    /** 数据来源：redis / db */
    private String source;

    // ── getters / setters ──

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public List<ChatMessageVO> getMessages() { return messages; }
    public void setMessages(List<ChatMessageVO> messages) { this.messages = messages; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
