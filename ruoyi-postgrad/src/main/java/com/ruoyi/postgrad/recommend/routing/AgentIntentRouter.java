package com.ruoyi.postgrad.recommend.routing;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.postgrad.recommend.domain.ChatMessageVO;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Uses the existing chat model as a small structured classifier.
 * It selects a capability group; the main Agent still decides which concrete
 * tool to call and what documents to retrieve.
 */
@Component
public class AgentIntentRouter {

    private static final Logger log = LoggerFactory.getLogger(AgentIntentRouter.class);
    private static final int HISTORY_USER_TURNS = 3;

    private final ChatModel chatModel;

    public AgentIntentRouter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public AgentRouteDecision route(String message, List<ChatMessageVO> history) {
        try {
            ChatRequest request = ChatRequest.builder()
                .messages(
                    SystemMessage.from(routingPrompt()),
                    UserMessage.from(buildRoutingInput(message, history)))
                .build();
            String raw = chatModel.chat(request).aiMessage().text();
            return parse(raw);
        } catch (Exception e) {
            log.warn("[AgentRoute] Route model failed, preserving fallback tools: {}",
                e.getClass().getSimpleName());
            return AgentRouteDecision.fallback("route_model_failed");
        }
    }

    public static AgentRouteDecision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AgentRouteDecision.fallback("empty_route");
        }
        try {
            String normalized = raw.trim();
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) {
                normalized = normalized.substring(start, end + 1);
            }
            JSONObject json = JSON.parseObject(normalized);
            AgentIntentRoute route = AgentIntentRoute.valueOf(
                json.getString("route").trim().toUpperCase());
            return new AgentRouteDecision(route, json.getString("reason"));
        } catch (Exception ignored) {
            return AgentRouteDecision.fallback("invalid_route_response");
        }
    }

    public static String routingPrompt() {
        return """
            你是考研择校 Agent 的能力路由器。只判断解决问题需要哪类系统能力，不回答用户问题。

            可选 route：
            - KNOWLEDGE_RAG：考研政策、国家线、调剂、408 考试、术语、择校方法、学校招生简章中的学费/学制/奖助等非结构化知识
            - PROGRAM_FACTS：学校或专业搜索、录取分数、招生人数、历年趋势、是否好考、两校结构化数据对比
            - HYBRID：同时需要结构化招生事实和非结构化学校文档/政策解释
            - DRAFT_READ：用户明确询问当前草稿、候选池、冲稳保配置或草稿健康度，但不要求修改
            - DRAFT_WRITE：用户明确要求添加、删除、替换、补充或调整当前草稿
            - CLARIFY：缺少学校/专业/指代对象，现有上下文也无法确定，应先追问

            边界：
            1. “没戏、难不难、怎么样”等口语本身不代表草稿意图；只有明确指向当前草稿/候选池时才选择 DRAFT_READ。
            2. 具体分数、名额、专业和历年趋势属于 PROGRAM_FACTS，不使用通用知识库代替数据库。
            3. 学校概况或招生简章信息属于 KNOWLEDGE_RAG；同时要求录取难度时选择 HYBRID。
            4. 用户明确要求修改草稿时才选择 DRAFT_WRITE。
            5. 结合最近用户消息解析简称和指代，但不要把助手以前的猜测当成事实。

            只输出 JSON，不要输出 Markdown 或解释：
            {"route":"PROGRAM_FACTS","reason":"一句话原因"}
            """;
    }

    private static String buildRoutingInput(String message, List<ChatMessageVO> history) {
        List<String> userTurns = new ArrayList<>();
        if (history != null) {
            for (ChatMessageVO item : history) {
                if (item != null && "user".equals(item.getRole())
                    && item.getContent() != null && !item.getContent().isBlank()) {
                    userTurns.add(item.getContent().trim());
                }
            }
        }
        int from = Math.max(0, userTurns.size() - HISTORY_USER_TURNS);
        List<String> recent = userTurns.subList(from, userTurns.size());
        String current = message == null ? "" : message.trim();
        return "最近用户消息：\n" + String.join("\n", recent)
            + "\n\n当前问题：\n" + current;
    }
}
