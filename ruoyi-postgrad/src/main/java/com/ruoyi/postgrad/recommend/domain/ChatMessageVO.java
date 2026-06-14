package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * 对话消息 —— 存储在 Redis {@code ai:v2:chat:msg:{userId}}。
 */
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String role;     // "user" | "assistant" | "system"
    private String content;
    private String messageType = "text";
    private String status = "completed";
    private Integer seq;
    private String metadataJson;

    public ChatMessageVO() {}

    public ChatMessageVO(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // ── getters / setters ──

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
