package com.ruoyi.postgrad.recommend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 发送对话消息请求体。
 */
public class ChatSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户消息文本 */
    @NotBlank
    private String message;

    // ── getters / setters ──

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
