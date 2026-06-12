package com.ruoyi.postgrad.recommend.domain;

import java.io.Serializable;

/**
 * 对话消息 —— 存储在 Redis {@code ai:v2:chat:msg:{userId}}。
 */
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String role;     // "user" | "assistant" | "system"
    private String content;

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
}
