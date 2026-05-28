package com.ruoyi.postgrad;

import com.ruoyi.postgrad.service.IAiRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AiRecommendationTest {

    @Autowired
    private IAiRecommendationService aiService;

    @Test
    public void testGoldenPath() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Arrays.asList(1, 2, 3));
        Map<String, Object> start = aiService.startConversation(1L, req);
        assertThat(start).containsKeys("conversationId", "message", "options");
        String convId = (String) start.get("conversationId");

        Map<String, Object> chat1 = aiService.chat(1L, convId, "看重专业排名");
        assertThat(chat1).containsKey("message");

        Map<String, Object> chat2 = aiService.chat(1L, convId, "想冲一下985");
        assertThat(chat2).containsKey("message");

        Map<String, Object> report = aiService.generateReport(1L, convId);
        assertThat(report).containsEntry("status", "PENDING");
        assertThat(report).containsKey("reportId");
    }

    @Test
    public void testConversationIsolation() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Arrays.asList(1, 2, 3));
        String convId = (String) aiService.startConversation(1L, req).get("conversationId");

        assertThatThrownBy(() -> aiService.chat(2L, convId, "hello"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testConversationExpired() {
        String fakeConvId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> aiService.chat(1L, fakeConvId, "hello"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("过期");
    }

    @Test
    public void testStartWithEmptyCandidates() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Collections.emptyList());

        Map<String, Object> start = aiService.startConversation(1L, req);
        assertThat(start).containsKeys("conversationId", "message");
    }
}
