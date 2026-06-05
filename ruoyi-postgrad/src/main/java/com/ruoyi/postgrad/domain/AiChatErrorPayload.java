package com.ruoyi.postgrad.domain;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public final class AiChatErrorPayload {
    private AiChatErrorPayload() {
    }

    public static Map<String, Object> from(Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String rawMessage = error != null && error.getMessage() != null ? error.getMessage() : "";
        Throwable root = rootCause(error);

        if (contains(rawMessage, "对话已过期") || contains(rootMessage(root), "对话已过期")) {
            payload.put("code", "expired");
            payload.put("message", "对话已过期，请重新开始 AI 推荐。");
        } else if (root instanceof SocketTimeoutException || contains(rawMessage, "Read timed out")) {
            payload.put("code", "connection_timeout");
            payload.put("message", "模型连接超时，可能是服务响应慢或网络波动。请稍后重试。");
        } else if (root instanceof TimeoutException || contains(rawMessage, "timeout") || contains(rawMessage, "timed out")) {
            payload.put("code", "timeout");
            payload.put("message", "本轮分析耗时较久，已停止等待。你可以缩小问题范围，或直接生成当前推荐报告。");
        } else if (contains(rawMessage, "401") || contains(rawMessage, "api key") || contains(rawMessage, "unauthorized")) {
            payload.put("code", "auth_failed");
            payload.put("message", "模型鉴权失败，请检查后端 AI Key 配置。");
        } else if (contains(rawMessage, "429") || contains(rawMessage, "rate limit")) {
            payload.put("code", "rate_limited");
            payload.put("message", "模型服务请求过于频繁，请稍后再试。");
        } else {
            payload.put("code", "model_error");
            payload.put("message", "AI 服务本轮响应失败，请稍后重试。");
        }

        if (!rawMessage.isBlank()) {
            payload.put("detail", rawMessage);
        }
        return payload;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cur = error;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String rootMessage(Throwable error) {
        return error != null && error.getMessage() != null ? error.getMessage() : "";
    }

    private static boolean contains(String text, String pattern) {
        return text != null && pattern != null && text.toLowerCase().contains(pattern.toLowerCase());
    }
}
