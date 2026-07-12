package com.ruoyi.postgrad.recommend.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.postgrad.mapper.AiChatMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.AiChatConversation;
import com.ruoyi.postgrad.recommend.domain.AiChatMessage;
import com.ruoyi.postgrad.recommend.domain.AgentRunContext;
import com.ruoyi.postgrad.recommend.domain.ChatMessageVO;
import com.ruoyi.postgrad.recommend.domain.ChatStartResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.ChatStreamCallback;
import com.ruoyi.postgrad.recommend.service.ChatRunLockService;
import com.ruoyi.postgrad.recommend.service.ChatRagRouteResolver;
import com.ruoyi.postgrad.recommend.service.IAiChatService;
import com.ruoyi.postgrad.recommend.service.IDraftService;
import com.ruoyi.postgrad.recommend.tool.V2BoundChatTools;
import com.ruoyi.postgrad.recommend.tool.V2BoundDraftActionTools;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;
import com.ruoyi.postgrad.recommend.tool.V2DraftActionTools;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;
import com.ruoyi.postgrad.observe.runtime.AiInvocationContext;
import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.service.RagRetrievalResult;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import com.ruoyi.postgrad.rag.trace.RagTrace;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;

/**
 * AI 对话服务实现：编排会话、流式输出和 LangChain4j tool calling。
 * <p>草稿写操作只通过后端 tool 执行，不从模型正文解析动作。</p>
 */
@Service
public class AiChatServiceImpl implements IAiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private static final String CHAT_KEY_PREFIX = "ai:v2:chat:";
    private static final String CHAT_MSG_KEY_PREFIX = "ai:v2:chat:msg:";
    private static final int MAX_MESSAGES = 20;
    private static final Duration CHAT_TTL = Duration.ofMinutes(30);

    @Value("classpath:prompts/v2/chat-system.txt")
    private org.springframework.core.io.Resource chatSystemPromptResource;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private OpenAiStreamingChatModel streamingChatModel;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChatRunLockService chatRunLockService;

    @Autowired
    private ChatRagRouteResolver chatRagRouteResolver;

    @Autowired
    private V2ChatTools v2ChatTools;

    @Autowired
    private V2DraftActionTools v2DraftActionTools;

    @Autowired
    private AgentToolRuntime agentToolRuntime;

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Autowired
    private IDraftService draftService;

    @Autowired
    private AiChatMapper aiChatMapper;

    @Autowired(required = false)
    private RetrievalAugmentor retrievalAugmentor;

    @Autowired(required = false)
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private com.ruoyi.postgrad.rag.trace.RagTraceRecorder ragTraceRecorder;

    @Value("${app.ai.rag.retrieval.max-results:5}")
    private int ragMaxResults = 5;

    @Value("${app.ai.rag.retrieval.min-score:0.6}")
    private double ragMinScore = 0.6;

    @Value("${app.ai.rag.retrieval.rerank-top-n:3}")
    private int ragRerankTopN = 3;

    private interface StreamAssistant {
        dev.langchain4j.service.TokenStream chat(String message);
    }

    @Override
    public void finalizeConversation(Long userId) {
        aiChatMapper.finalizeActiveConversations(userId);
        log.info("[Chat] Finalized active conversations for userId={}", userId);
    }

    @Override
    public ChatStartResultVO startChat(Long userId) {
        // 终结旧对话，避免上下文污染新草稿
        aiChatMapper.finalizeActiveConversations(userId);
        AiChatConversation conversation = getOrCreateActiveConversation(userId);

        // 清除旧对话历史
        redisTemplate.delete(chatMsgKey(userId));

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt(userId);

        // 保存系统提示词到 Redis（作为对话元数据）
        redisTemplate.opsForValue().set(chatKey(userId), systemPrompt, CHAT_TTL);

        // 保存初始系统消息到对话历史
        List<ChatMessageVO> messages = new ArrayList<>();
        messages.add(new ChatMessageVO("system", systemPrompt));
        redisTemplate.opsForValue().set(chatMsgKey(userId), JSON.toJSONString(messages), CHAT_TTL);

        ChatStartResultVO result = new ChatStartResultVO();
        result.setConversationId(String.valueOf(conversation.getId()));
        result.setMessage("你好！我已了解你的画像和当前草稿。你可以问我：为什么推荐某所学校、某校的风险是什么、或者让我帮忙对比两所学校。");
        result.setOptions(List.of("分析草稿中的学校", "对比两所学校", "解释推荐理由", "推荐替代选择"));
        result.setMessages(loadPersistedDisplayMessages(conversation.getId()));
        result.setSource("new");
        return result;
    }

    @Override
    public void chat(Long userId, String message, ChatStreamCallback callback) {
        // 并发锁：防止同一用户同时发多条消息导致历史覆盖
        String lockOwner = UUID.randomUUID().toString();
        if (!chatRunLockService.acquire(userId, lockOwner)) {
            callback.onError(new IllegalStateException("上一条消息仍在处理中，请稍候再发"));
            return;
        }
        AtomicBoolean finished = new AtomicBoolean(false);
        AgentRunContext runContext = AgentRunContext.start(userId);
        String runId = runContext.getRunId();
        callback.onRunStarted(runId);
        long t0 = System.currentTimeMillis();
        try {
            AiInvocationContext.setOperationKey("ai-chat");
            AiInvocationContext.setRunId(runId);
            AiChatConversation conversation = getOrCreateActiveConversation(userId);

            // 1. 加载/初始化对话历史
            List<ChatMessageVO> loadedHistory = loadHistory(userId);
            if (loadedHistory.isEmpty()) {
                loadedHistory = loadPersistedMemoryMessages(conversation.getId());
            }
            final List<ChatMessageVO> history = loadedHistory;

            // 2. 每轮都重建系统提示词，确保草稿调整后的档位和操作ID是最新的。
            String systemPrompt = buildSystemPrompt(userId);
            redisTemplate.opsForValue().set(chatKey(userId), systemPrompt, CHAT_TTL);

            // 3. 构建 ChatMemory
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES);
            for (ChatMessageVO m : history) {
                if ("system".equals(m.getRole())) continue;
                if (m.getContent() == null || m.getContent().isBlank()) continue;
                if ("assistant".equals(m.getRole())) {
                    chatMemory.add(AiMessage.from(m.getContent()));
                } else if ("user".equals(m.getRole())) {
                    chatMemory.add(UserMessage.from(m.getContent()));
                }
            }

            // 4. 保存用户消息
            persistMessage(userId, conversation.getId(), "user", message, message);
            history.add(new ChatMessageVO("user", message));
            saveHistory(userId, history);

            final String finalSystemPrompt = systemPrompt;
            final ChatRagRouteResolver.Route ragRoute = chatRagRouteResolver.resolve(message);

            // 5. 初始化工具上下文 + 创建流式 assistant
            try {
                V2ChatToolContext.Context toolContext = V2ChatToolContext.init(userId, redisTemplate,
                    recommendationMapper, runId, runContext.getToolBudget(), new V2ChatToolContext.ToolLifecycleListener() {
                        @Override
                        public void onStarted(String lifecycleRunId, String callId, String toolName, String category) {
                            callback.onToolCall(toolName, callId, lifecycleRunId, category);
                        }

                        @Override
                        public void onCompleted(String lifecycleRunId, String callId, String toolName,
                            String status, String code, long durationMs) {
                            callback.onToolResult(toolName, callId, lifecycleRunId, status, code, durationMs);
                        }

                        @Override
                        public void onConfirmationRequired(String lifecycleRunId, String callId,
                            String summary, String confirmationToken) {
                            callback.onConfirmationRequired(callId, lifecycleRunId, summary, confirmationToken);
                        }
                    });
            AiServices<StreamAssistant> assistantBuilder = AiServices.builder(StreamAssistant.class)
                .streamingChatModel(streamingChatModel)
                .tools(
                    new V2BoundChatTools(v2ChatTools, toolContext, agentToolRuntime),
                    new V2BoundDraftActionTools(v2DraftActionTools, toolContext, agentToolRuntime))
                .hallucinatedToolNameStrategy(request -> ToolExecutionResultMessage.from(request,
                    agentToolRuntime.rejectWithoutExecution(toolContext, request.name(), "TOOL_NOT_FOUND",
                        "请求的工具不存在，请选择已提供的工具。", true)))
                .toolArgumentsErrorHandler((error, errorContext) -> ToolErrorHandlerResult.text(
                    agentToolRuntime.rejectWithoutExecution(toolContext,
                        errorContext.toolExecutionRequest().name(), "INVALID_ARGUMENTS",
                        "工具参数无效，请修正参数后重试。", true)))
                .toolExecutionErrorHandler((error, errorContext) -> ToolErrorHandlerResult.text(
                    agentToolRuntime.rejectWithoutExecution(toolContext,
                        errorContext.toolExecutionRequest().name(), "TOOL_EXECUTION_FAILED",
                        "工具执行失败，请基于已有信息继续。", false)))
                .chatMemory(chatMemory)
                .systemMessageProvider(ignored -> finalSystemPrompt);
            final boolean generalRetrievalEnabled = usesGenericRetrieval(ragRoute, retrievalAugmentor);
            final GenericRagRetrieval genericRag = generalRetrievalEnabled
                ? prepareGenericRagRetrieval(message) : null;
            if (generalRetrievalEnabled) {
                assistantBuilder.retrievalAugmentor(genericRag != null
                    ? genericRag.augmentor() : retrievalAugmentor);
            }
            StreamAssistant assistant = assistantBuilder.build();

            StringBuilder fullResponse = new StringBuilder();
            assistant.chat("<user_input>" + message + "</user_input>")
                .onPartialToolCall(toolCall -> log.debug("[AiChat-Tool] partial userId={} name={}",
                    userId, toolCall.name()))
                .onPartialResponse(token -> {
                    fullResponse.append(token);
                    callback.onToken(token);
                })
                .onCompleteResponse(response -> {
                    try {
                        String aiText = response.aiMessage().text();
                        if (aiText == null || aiText.isBlank()) {
                            aiText = fullResponse.toString();
                        }

                        history.add(new ChatMessageVO("assistant", aiText));
                        saveHistory(userId, history);
                        persistMessage(userId, conversation.getId(), "assistant", aiText, aiText);

                        boolean draftChanged = toolContext.draftChanged();
                        String toolActionResultJson = toolContext.lastActionResultJson();
                        if (generalRetrievalEnabled) {
                            if (genericRag != null) {
                                recordRagTrace(userId, message, runId, ragRoute, genericRag.response(), t0);
                            } else {
                                recordRagTrace(userId, message, runId, ragRoute.traceValue(), RagTrace.Outcome.SUCCESS, null, null, t0);
                            }
                        }
                        callback.onDone(aiText, draftChanged, toolActionResultJson, runId);
                    } catch (Exception e) {
                        log.error("[AiChat] Failed to complete stream for userId={}: {}", userId, e.getMessage());
                        callback.onError(e, runId);
                    } finally {
                        V2ChatToolContext.clear();
                        AiInvocationContext.clear();
                        finishChatRun(userId, lockOwner, finished);
                    }
                })
                .onError(error -> {
                    V2ChatToolContext.clear();
                    AiInvocationContext.clear();
                    log.error("[AiChat] Stream error for userId={}: {}", userId, error.getMessage());
                    if (generalRetrievalEnabled) {
                        recordRagTrace(userId, message, runId, ragRoute.traceValue(), RagTrace.Outcome.FAILURE, "stream", error.getMessage(), t0);
                    }
                    notifyErrorAndFinish(callback, error, runId, userId, lockOwner, finished);
                })
                .start();
            V2ChatToolContext.clear();
            AiInvocationContext.clear();
            } catch (Exception e) {
                V2ChatToolContext.clear();
                AiInvocationContext.clear();
                log.error("[AiChat] Failed to start stream for userId={}: {}", userId, e.getMessage());
                notifyErrorAndFinish(callback, e, runId, userId, lockOwner, finished);
            }
        } catch (Exception e) {
            V2ChatToolContext.clear();
            AiInvocationContext.clear();
            log.error("[AiChat] Failed to prepare stream for userId={}: {}", userId, e.getMessage());
            notifyErrorAndFinish(callback, e, runId, userId, lockOwner, finished);
        }
    }

    static boolean usesGenericRetrieval(ChatRagRouteResolver.Route route, RetrievalAugmentor augmentor) {
        return route == ChatRagRouteResolver.Route.GENERAL && augmentor != null;
    }

    private GenericRagRetrieval prepareGenericRagRetrieval(String message) {
        if (ragRetrievalService == null) {
            return null;
        }
        RagSearchTestRequest request = new RagSearchTestRequest(message, ragMaxResults, ragMinScore, ragRerankTopN);
        RagRetrievalResult result = ragRetrievalService.retrieve(request);
        ContentRetriever fixedRetriever = ignored -> result.selectedSegments().stream()
            .map(Content::from)
            .toList();
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
            .contentRetriever(fixedRetriever)
            .build();
        return new GenericRagRetrieval(result.response(), augmentor);
    }

    private void notifyErrorAndFinish(ChatStreamCallback callback, Throwable error, String runId,
                                      Long userId, String lockOwner, AtomicBoolean finished) {
        try {
            callback.onError(error, runId);
        } catch (Exception callbackError) {
            log.warn("[AiChat] Error callback failed for runId={}: {}", runId, callbackError.getMessage());
        } finally {
            finishChatRun(userId, lockOwner, finished);
        }
    }

    private void finishChatRun(Long userId, String lockOwner, AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            chatRunLockService.release(userId, lockOwner);
        }
    }

    @Override
    public ChatStartResultVO resumeChat(Long userId) {
        AiChatConversation conversation = getOrCreateActiveConversation(userId);

        // 刷新 TTL
        String systemPrompt = redisTemplate.opsForValue().get(chatKey(userId));
        if (systemPrompt == null) {
            redisTemplate.opsForValue().set(chatKey(userId), buildSystemPrompt(userId), CHAT_TTL);
        }
        redisTemplate.expire(chatKey(userId), CHAT_TTL);
        redisTemplate.expire(chatMsgKey(userId), CHAT_TTL);

        ChatStartResultVO result = new ChatStartResultVO();
        result.setConversationId(String.valueOf(conversation.getId()));
        result.setMessage("对话已恢复");
        result.setSource("db");
        result.setMessages(loadPersistedDisplayMessages(conversation.getId()));
        return result;
    }

    // ── private helpers ──

    private String chatKey(Long userId) { return CHAT_KEY_PREFIX + userId; }
    private String chatMsgKey(Long userId) { return CHAT_MSG_KEY_PREFIX + userId; }

    private AiChatConversation getOrCreateActiveConversation(Long userId) {
        AiChatConversation conversation = aiChatMapper.selectActiveConversation(userId);
        if (conversation != null) return conversation;

        conversation = new AiChatConversation();
        conversation.setUserId(userId);
        conversation.setDraftKey("active:" + userId);
        conversation.setTitle("AI 择校对话");
        conversation.setStatus("active");
        aiChatMapper.insertConversation(conversation);
        return conversation;
    }

    private void persistMessage(Long userId, Long conversationId, String role, String content, String displayContent) {
        AiChatMessage message = new AiChatMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setDisplayContent(displayContent != null ? displayContent : content);
        message.setMessageType("text");
        message.setStatus("completed");
        Integer seq = aiChatMapper.selectNextSeq(conversationId);
        message.setSeq(seq != null ? seq : 1);
        message.setMetadataJson(null);
        aiChatMapper.insertMessage(message);
        aiChatMapper.touchConversation(conversationId);
    }

    private List<ChatMessageVO> loadPersistedDisplayMessages(Long conversationId) {
        List<AiChatMessage> rows = aiChatMapper.selectMessages(conversationId, 200);
        List<ChatMessageVO> result = new ArrayList<>();
        for (AiChatMessage row : rows) {
            if ("system".equals(row.getRole())) continue;
            ChatMessageVO vo = new ChatMessageVO(row.getRole(),
                row.getDisplayContent() != null ? row.getDisplayContent() : row.getContent());
            vo.setMessageType(row.getMessageType());
            vo.setStatus(row.getStatus());
            vo.setSeq(row.getSeq());
            vo.setMetadataJson(row.getMetadataJson());
            result.add(vo);
        }
        return result;
    }

    private List<ChatMessageVO> loadPersistedMemoryMessages(Long conversationId) {
        List<AiChatMessage> rows = aiChatMapper.selectMessages(conversationId, MAX_MESSAGES);
        List<ChatMessageVO> result = new ArrayList<>();
        for (AiChatMessage row : rows) {
            if ("system".equals(row.getRole())) continue;
            if (!"user".equals(row.getRole()) && !"assistant".equals(row.getRole())) continue;
            String content = row.getDisplayContent() != null ? row.getDisplayContent() : row.getContent();
            if (content == null || content.isBlank()) continue;
            result.add(new ChatMessageVO(row.getRole(), content));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessageVO> loadHistory(Long userId) {
        String json = redisTemplate.opsForValue().get(chatMsgKey(userId));
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return JSON.parseArray(json, ChatMessageVO.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveHistory(Long userId, List<ChatMessageVO> history) {
        redisTemplate.opsForValue().set(chatMsgKey(userId), JSON.toJSONString(history), CHAT_TTL);
    }

    /**
     * 构建系统提示词：加载模板 + 注入用户画像 + 草稿上下文。
     */
    private String buildSystemPrompt(Long userId) {
        String template = loadPromptTemplate();
        DraftVO draft = draftService.getDraft(userId);
        String draftCtx = buildDraftContextText(draft);
        String profileCtx = buildProfileContextText(draft);

        return template
            .replace("{profileContext}", profileCtx)
            .replace("{draftContext}", draftCtx);
    }

    /**
     * 从草稿的 ProfileBasisVO 中提取用户画像上下文。
     */
    private String buildProfileContextText(DraftVO draft) {
        if (draft == null || draft.getProfileBasis() == null) {
            return "用户画像未设置。";
        }
        var p = draft.getProfileBasis();
        StringBuilder sb = new StringBuilder();
        sb.append("预估分数：").append(p.getEstimatedScore() != null ? p.getEstimatedScore() : "未知").append(" 分\n");
        sb.append("目标地区：").append(p.getTargetRegions() != null ? p.getTargetRegions() : "不限").append("\n");
        sb.append("择校偏好：").append(p.getSchoolTierPreference() != null ? p.getSchoolTierPreference() : "安全上岸优先").append("\n");
        return sb.toString();
    }

    private String loadPromptTemplate() {
        try {
            return new String(chatSystemPromptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[AiChat] Failed to load chat system prompt: {}", e.getMessage());
            return "你是考研择校助手，帮助用户分析和调整择校草稿。\n当前草稿：{draftContext}";
        }
    }

    private void recordRagTrace(Long userId, String query, String runId, String retrievalRoute, RagTrace.Outcome outcome,
                                 String failureStage, String errorSummary, long startMs) {
        try {
            RagTrace trace = new RagTrace();
            trace.setOperationKey("ai-chat");
            trace.setRunId(runId);
            trace.setRetrievalRoute(retrievalRoute);
            trace.setUserId(userId);
            trace.setQuery(query);
            trace.setOutcome(outcome);
            trace.setFailureStage(failureStage);
            trace.setErrorSummary(errorSummary);
            trace.setTotalLatencyMs(System.currentTimeMillis() - startMs);
            ragTraceRecorder.record(trace);
        } catch (Exception e) {
            log.warn("[AiChat] Failed to record RAG trace for runId={}: {}", runId, e.getMessage());
        }
    }

    private void recordRagTrace(Long userId, String query, String runId, ChatRagRouteResolver.Route route,
                                RagSearchTestResponse retrieval, long startMs) {
        RagTrace.Outcome outcome = retrieval != null && retrieval.getErrorSummary() == null
            ? RagTrace.Outcome.SUCCESS : RagTrace.Outcome.FAILURE;
        try {
            RagTrace trace = new RagTrace();
            trace.setOperationKey("ai-chat");
            trace.setRunId(runId);
            trace.setRetrievalRoute(route.traceValue());
            trace.setUserId(userId);
            trace.setQuery(query);
            trace.setOutcome(outcome);
            trace.setFailureStage(outcome == RagTrace.Outcome.FAILURE ? "retrieval" : null);
            trace.setErrorSummary(retrieval != null ? retrieval.getErrorSummary() : "RAG retrieval unavailable");
            if (retrieval != null) {
                trace.setRetrievedCount(retrieval.getRetrievedCount());
                trace.setSelectedCount(retrieval.getSelectedCount());
                trace.setMetadataFilters(retrieval.getAppliedFilters());
                trace.setEmbeddingLatencyMs(retrieval.getLatency().getEmbeddingMs());
                trace.setRetrievalLatencyMs(retrieval.getLatency().getRetrievalMs());
                trace.setRerankLatencyMs(retrieval.getLatency().getRerankMs());
                List<RagChunkResult> selected = retrieval.getChunks().stream()
                    .filter(RagChunkResult::isSelectedForPrompt)
                    .toList();
                trace.setTopSources(selected.stream()
                    .map(RagChunkResult::getSourceName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .toList());
                trace.setSelectedChunks(selected.stream()
                    .map(chunk -> new RagTrace.SelectedChunk(chunk.getSourceName(), chunk.getContentPreview()))
                    .toList());
            }
            trace.setTotalLatencyMs(System.currentTimeMillis() - startMs);
            ragTraceRecorder.record(trace);
        } catch (Exception e) {
            log.warn("[AiChat] Failed to record generic RAG trace for runId={}: {}", runId, e.getMessage());
        }
    }

    private record GenericRagRetrieval(RagSearchTestResponse response, RetrievalAugmentor augmentor) {
    }

    static String buildDraftContextText(DraftVO draft) {
        if (draft == null || draft.getTiers() == null) return "尚未生成草稿。";

        StringBuilder sb = new StringBuilder();
        for (TierCandidates t : draft.getTiers()) {
            if (t.getCandidates() == null || t.getCandidates().isEmpty()) {
                sb.append(t.getLabel()).append("：暂无候选\n");
                continue;
            }
            sb.append(t.getLabel()).append("（").append(t.getCandidates().size())
                .append("/").append(t.getTargetCount()).append("）：\n");
            for (var c : t.getCandidates()) {
                var f = c.getFact();
                sb.append("  - ").append(f.getSchoolName())
                    .append(" ").append(f.getProgramName())
                    .append(" (ID ").append(f.getProgramId()).append(")");
                if (f.getAvgAdmittedScore() != null) {
                    sb.append(" 均分").append(f.getAvgAdmittedScore());
                }
                if (f.getScoreGap() != null) {
                    sb.append(" 差距").append(f.getScoreGap() >= 0 ? "+" : "").append(f.getScoreGap());
                }
                if (c.getOpinion() != null && c.getOpinion().getReason() != null) {
                    sb.append(" 理由:").append(c.getOpinion().getReason());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
