package com.ruoyi.postgrad;

import com.ruoyi.postgrad.service.IAiRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "druid"})
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
        Map<String, Object> result = aiService.chat(1L, fakeConvId, "hello");
        assertThat(result).containsEntry("status", "expired");
    }

    @Test
    public void testStartWithEmptyCandidates() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Collections.emptyList());

        Map<String, Object> start = aiService.startConversation(1L, req);
        assertThat(start).containsKeys("conversationId", "message");
    }

    @Test
    public void debugSystemPrompt() {
        // 选几个真实 programId 让 AI 有数据可查
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Arrays.asList(793, 2501, 797, 32, 142, 26));
        req.put("estimatedScore", 280);

        Map<String, Object> start = aiService.startConversation(1L, req);
        String convId = (String) start.get("conversationId");

        System.out.println("=== System Prompt 测试 ===");
        System.out.println("user=1, estimatedScore=280, undergrad=PRIVATE, isCrossMajor=否, regions=[福建]");
        System.out.println("---");
        System.out.println("AI 开局: " + start.get("message"));
        System.out.println("Options: " + start.get("options"));
        System.out.println("---");

        // 模拟一轮对话
        Map<String, Object> chat1 = aiService.chat(1L, convId, "我比较看重上岸率");
        System.out.println("用户: 我比较看重上岸率");
        System.out.println("AI: " + chat1.get("message"));
        System.out.println("Options: " + chat1.getOrDefault("options", "[]"));
        System.out.println("---");

        // 再模拟一轮
        Map<String, Object> chat2 = aiService.chat(1L, convId, "帮我看一下上海大学的详细数据");
        System.out.println("用户: 帮我看一下上海大学的详细数据");
        System.out.println("AI: " + chat2.get("message"));
        System.out.println("Options: " + chat2.getOrDefault("options", "[]"));
    }

    @Test
    public void debugReportGeneration() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Arrays.asList(793, 2501, 797, 32, 142, 26, 37, 72, 433, 23, 17, 22));
        req.put("estimatedScore", 280);

        Map<String, Object> start = aiService.startConversation(1L, req);
        String convId = (String) start.get("conversationId");

        System.out.println("=== 报告效果测试 ===");
        System.out.println("开局: " + start.get("message"));
        System.out.println("---");

        // 模拟对话：用户表达偏好后直接要报告
        aiService.chat(1L, convId, "我280分，本科民办，只想在福建，最看重学校层次和上岸率");
        aiService.chat(1L, convId, "帮我推荐冲刺、稳妥、保底三个档，每个档位1-2所学校，然后直接出报告");

        // 生成报告（测试环境 MQ 禁用，同步返回）
        Map<String, Object> report = aiService.generateReport(1L, convId);

        System.out.println("报告状态: " + report.get("status"));
        if (report.get("result") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) report.get("result");
            System.out.println("概要: " + result.getOrDefault("summary", "N/A"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tiers = (List<Map<String, Object>>) result.get("tiers");
            if (tiers != null) {
                for (Map<String, Object> tier : tiers) {
                    System.out.println("\n>>> " + tier.get("label") + " (" + tier.get("level") + ")");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
                    if (schools != null) {
                        for (Map<String, Object> s : schools) {
                            System.out.println("  - " + s.get("schoolName") + " " + s.get("programName"));
                            System.out.println("    matchScore=" + s.get("matchScore")
                                + " 均分=" + s.get("avgAdmittedScore")
                                + " 复试线=" + s.get("scoreLine")
                                + " 年份=" + s.get("dataYear"));
                            System.out.println("    理由: " + s.get("reason"));
                        }
                    }
                }
            }
        } else {
            System.out.println("报告结果: " + report);
        }
    }
}
