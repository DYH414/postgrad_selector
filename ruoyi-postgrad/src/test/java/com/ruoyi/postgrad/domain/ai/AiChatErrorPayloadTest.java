package com.ruoyi.postgrad.domain.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class AiChatErrorPayloadTest {

    @Test
    void shouldExposeTimeoutAsActionableChatError() {
        Map<String, Object> payload = AiChatErrorPayload.from(new TimeoutException("120000 ms"));

        assertEquals("timeout", payload.get("code"));
        assertEquals("本轮分析耗时较久，已停止等待。你可以缩小问题范围，或直接生成当前推荐报告。", payload.get("message"));
    }

    @Test
    void shouldExposeConnectionErrorsSeparately() {
        Map<String, Object> payload = AiChatErrorPayload.from(new SocketTimeoutException("Read timed out"));

        assertEquals("connection_timeout", payload.get("code"));
        assertEquals("模型连接超时，可能是服务响应慢或网络波动。请稍后重试。", payload.get("message"));
    }

    @Test
    void shouldExposeExpiredConversation() {
        Map<String, Object> payload = AiChatErrorPayload.from(new IllegalArgumentException("对话已过期，请开始新对话"));

        assertEquals("expired", payload.get("code"));
        assertEquals("对话已过期，请重新开始 AI 推荐。", payload.get("message"));
    }
}
