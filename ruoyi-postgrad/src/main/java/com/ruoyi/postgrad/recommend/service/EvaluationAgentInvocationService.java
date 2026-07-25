package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.observe.runtime.AiInvocationContext;
import com.ruoyi.postgrad.recommend.domain.AgentRunContext;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationContext;
import com.ruoyi.postgrad.recommend.domain.EvaluationAgentInvocationResult;
import com.ruoyi.postgrad.recommend.routing.AgentIntentRoute;
import com.ruoyi.postgrad.recommend.routing.AgentIntentRouter;
import com.ruoyi.postgrad.recommend.routing.AgentRouteDecision;
import com.ruoyi.postgrad.recommend.routing.AgentToolScope;
import com.ruoyi.postgrad.recommend.tool.V2BoundChatTools;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 隔离只读 Agent 评测调用。同步返回，不走 SSE。
 *
 * <p>四层只读防御：①能力不给（只绑 V2BoundChatTools，绝不绑写工具）；
 * ②执行前拦截（DRAFT_WRITE 在建工具上下文/调模型前短路）；③不加锁/不写聊天历史/不写会话；
 * ④事后兜底（运行后若 draftChanged 则终态改 UNSUPPORTED_SIDE_EFFECT）。</p>
 */
@Service
public class EvaluationAgentInvocationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationAgentInvocationService.class);

    private final ChatModel chatModel;
    private final StringRedisTemplate redisTemplate;
    private final V2ChatTools v2ChatTools;
    private final AgentToolRuntime agentToolRuntime;
    private final AgentIntentRouter agentIntentRouter;
    private final RecommendationMapper recommendationMapper;
    private final IDraftService draftService;

    public EvaluationAgentInvocationService(ChatModel chatModel, StringRedisTemplate redisTemplate,
        V2ChatTools v2ChatTools, AgentToolRuntime agentToolRuntime, AgentIntentRouter agentIntentRouter,
        RecommendationMapper recommendationMapper, IDraftService draftService) {
        this.chatModel = chatModel;
        this.redisTemplate = redisTemplate;
        this.v2ChatTools = v2ChatTools;
        this.agentToolRuntime = agentToolRuntime;
        this.agentIntentRouter = agentIntentRouter;
        this.recommendationMapper = recommendationMapper;
        this.draftService = draftService;
    }

    private interface Assistant {
        String chat(String message);
    }

    public EvaluationAgentInvocationResult invoke(EvaluationAgentInvocationContext ctx) {
        long t0 = System.currentTimeMillis();
        AgentRunContext runContext = AgentRunContext.start(ctx.subjectUserId());
        String runId = runContext.getRunId();
        AgentRouteDecision decision = null;
        try {
            AiInvocationContext.setRunId(runId);

            // 空历史：评测每个 case 独立单轮，不共享对话历史
            AiInvocationContext.setOperationKey("eval-agent-route");
            decision = agentIntentRouter.route(ctx.query(), List.of());
            AiInvocationContext.setOperationKey("eval-agent");
            String route = decision.route().name();

            // 防御②：写路由在建工具上下文/调模型前短路
            if (decision.route() == AgentIntentRoute.DRAFT_WRITE) {
                return EvaluationAgentInvocationResult.unsupportedSideEffect(
                    runId, route, decision.reason(), System.currentTimeMillis() - t0);
            }

            String systemPrompt = buildSystemPrompt(ctx)
                + "\n<runtime_route>" + route + "；只使用本轮实际提供的工具。</runtime_route>";

            V2ChatToolContext.Context toolContext = V2ChatToolContext.init(
                ctx.subjectUserId(), redisTemplate, recommendationMapper, runId);
            try {
                // 防御①：只绑只读工具（V2BoundChatTools），绝不绑 V2BoundDraftActionTools
                var readTools = new V2BoundChatTools(v2ChatTools, toolContext, agentToolRuntime);
                var scopedTools = AgentToolScope.select(decision.route(), readTools);
                Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .tools(scopedTools)
                    .systemMessageProvider(ignored -> systemPrompt)
                    .build();

                String answer = assistant.chat("<user_input>" + ctx.query() + "</user_input>");
                long durationMs = System.currentTimeMillis() - t0;

                // 防御④：事后兜底——若草稿被改动，终态改 UNSUPPORTED_SIDE_EFFECT
                if (toolContext.draftChanged() || V2ChatToolContext.writeExecuted()) {
                    return EvaluationAgentInvocationResult.unsupportedSideEffect(
                        runId, route, decision.reason(), durationMs);
                }
                return new EvaluationAgentInvocationResult(runId, "SUCCEEDED", route,
                    decision.reason(), answer, durationMs, false, null, null);
            } finally {
                V2ChatToolContext.clear();
            }
        } catch (Exception e) {
            // 错误即数据：归一化为 FAILED，不抛
            String route = decision == null ? null : decision.route().name();
            log.warn("[EvalAgent] invocation failed runId={}: {}", runId, e.getMessage());
            return EvaluationAgentInvocationResult.failed(
                runId, route, System.currentTimeMillis() - t0, safe(e.getMessage()));
        } finally {
            AiInvocationContext.clear();
        }
    }

    /** 用 config 传入的业务 Prompt，注入只读加载的测试用户画像。 */
    private String buildSystemPrompt(EvaluationAgentInvocationContext ctx) {
        String template = ctx.businessPromptText() == null ? "" : ctx.businessPromptText();
        String profileCtx;
        try {
            DraftVO draft = draftService.getDraft(ctx.subjectUserId());
            profileCtx = buildProfileContextText(draft);
        } catch (Exception e) {
            profileCtx = "用户画像未设置。";
        }
        return template.replace("{profileContext}", profileCtx);
    }

    private String buildProfileContextText(DraftVO draft) {
        if (draft == null || draft.getProfileBasis() == null) return "用户画像未设置。";
        var p = draft.getProfileBasis();
        return "预估分数：" + (p.getEstimatedScore() != null ? p.getEstimatedScore() : "未知") + " 分\n"
            + "目标地区：" + (p.getTargetRegions() != null ? p.getTargetRegions() : "不限") + "\n"
            + "择校偏好：" + (p.getSchoolTierPreference() != null ? p.getSchoolTierPreference() : "安全上岸优先") + "\n";
    }

    private static String safe(String msg) {
        if (msg == null) return "unknown";
        return msg.length() > 512 ? msg.substring(0, 512) : msg;
    }
}
