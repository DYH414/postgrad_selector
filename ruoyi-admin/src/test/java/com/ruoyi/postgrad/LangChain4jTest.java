package com.ruoyi.postgrad;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

public class LangChain4jTest {

    @Test
    public void testSimpleChat() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("请设置环境变量 DASHSCOPE_API_KEY");
        }

        ChatModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .build();

        String response = model.chat("你好，请用一句话介绍你自己。");
        System.out.println("AI 回复: " + response);
    }
}
