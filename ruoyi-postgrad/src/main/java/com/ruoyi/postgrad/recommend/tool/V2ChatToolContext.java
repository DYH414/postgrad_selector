package com.ruoyi.postgrad.recommend.tool;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.ruoyi.postgrad.domain.ai.AiToolBudget;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import java.util.function.Supplier;

/**
 * 对话工具上下文 —— ThreadLocal 持有当前对话的 userId 和依赖。
 * <p>LangChain4j 工具调用时可能在不同线程执行，每个对话开始时初始化此上下文。</p>
 * <p>使用模式：
 * <pre>{@code
 * V2ChatToolContext.init(userId, redisTemplate, recommendationMapper);
 * try {
 *     assistant.chat(message);
 * } finally {
 *     V2ChatToolContext.clear();
 * }
 * }</pre>
 */
public final class V2ChatToolContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_CALL_ID = new ThreadLocal<>();
    private static final ToolLifecycleListener NO_OP_LISTENER = new ToolLifecycleListener() { };

    private V2ChatToolContext() {}

    /**
     * 初始化当前线程的工具上下文。
     *
     * @param userId 当前用户 ID
     * @param redis  Redis 模板
     * @param mapper 推荐数据 Mapper
     */
    public static Context init(Long userId, StringRedisTemplate redis, RecommendationMapper mapper, String runId) {
        return init(userId, redis, mapper, runId, AiToolBudget.chatTurnDefaults(), NO_OP_LISTENER);
    }

    public static Context init(Long userId, StringRedisTemplate redis, RecommendationMapper mapper, String runId,
        AiToolBudget budget, ToolLifecycleListener listener) {
        Context context = new Context(userId, redis, mapper, runId, false, budget, listener);
        CURRENT.set(context);
        return context;
    }

    public static Context confirmed(Long userId, StringRedisTemplate redis, RecommendationMapper mapper, String runId) {
        return new Context(userId, redis, mapper, runId, true, AiToolBudget.chatTurnDefaults(), NO_OP_LISTENER);
    }

    public static Context init(Long userId, StringRedisTemplate redis, RecommendationMapper mapper) {
        Context context = confirmed(userId, redis, mapper, null);
        CURRENT.set(context);
        return context;
    }

    /** 清除当前线程的上下文。 */
    public static void clear() {
        CURRENT.remove();
        CURRENT_CALL_ID.remove();
    }

    /** @return 当前上下文，未初始化时返回 null */
    static Context current() {
        return CURRENT.get();
    }

    public static <T> T callWith(Context context, Supplier<T> supplier) {
        return callWith(context, null, supplier);
    }

    public static <T> T callWith(Context context, String callId, Supplier<T> supplier) {
        Context previous = CURRENT.get();
        String previousCallId = CURRENT_CALL_ID.get();
        CURRENT.set(context);
        if (callId == null) {
            CURRENT_CALL_ID.remove();
        } else {
            CURRENT_CALL_ID.set(callId);
        }
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            if (previousCallId == null) {
                CURRENT_CALL_ID.remove();
            } else {
                CURRENT_CALL_ID.set(previousCallId);
            }
        }
    }

    public static String currentCallId() {
        return CURRENT_CALL_ID.get();
    }

    public static boolean writeExecuted() {
        Context ctx = CURRENT.get();
        return ctx != null && ctx.writeExecuted();
    }

    public static boolean draftChanged() {
        Context ctx = CURRENT.get();
        return ctx != null && ctx.draftChanged();
    }

    public static String lastActionResultJson() {
        Context ctx = CURRENT.get();
        return ctx != null ? ctx.lastActionResultJson() : null;
    }

    public static void markWriteExecuted(String actionResultJson) {
        Context ctx = CURRENT.get();
        if (ctx != null) {
            ctx.markWriteExecuted(actionResultJson);
        }
    }

    public static void markProposal(String actionResultJson) {
        Context ctx = CURRENT.get();
        if (ctx != null) ctx.lastActionResultJson = actionResultJson;
    }

    // ── 内部上下文对象 ──

    public static final class Context {
        private final Long userId;
        private final StringRedisTemplate redis;
        private final RecommendationMapper mapper;
        private final String runId;
        private final boolean confirmed;
        private final AiToolBudget toolBudget;
        private final ToolLifecycleListener toolLifecycleListener;
        private boolean writeExecuted;
        private boolean draftChanged;
        private String lastActionResultJson;

        Context(Long userId, StringRedisTemplate redis, RecommendationMapper mapper, String runId, boolean confirmed,
            AiToolBudget toolBudget, ToolLifecycleListener toolLifecycleListener) {
            this.userId = userId;
            this.redis = redis;
            this.mapper = mapper;
            this.runId = runId;
            this.confirmed = confirmed;
            this.toolBudget = toolBudget == null ? AiToolBudget.chatTurnDefaults() : toolBudget;
            this.toolLifecycleListener = toolLifecycleListener == null ? NO_OP_LISTENER : toolLifecycleListener;
        }

        public Long userId() { return userId; }
        StringRedisTemplate redis() { return redis; }
        RecommendationMapper mapper() { return mapper; }
        public String runId() { return runId; }
        public boolean confirmed() { return confirmed; }
        boolean writeExecuted() { return writeExecuted; }
        public boolean draftChanged() { return draftChanged; }
        public String lastActionResultJson() { return lastActionResultJson; }
        public AiToolBudget toolBudget() { return toolBudget; }
        public ToolLifecycleListener toolLifecycleListener() { return toolLifecycleListener; }

        void markWriteExecuted(String actionResultJson) {
            this.writeExecuted = true;
            this.draftChanged = true;
            this.lastActionResultJson = actionResultJson;
        }
    }

    public interface ToolLifecycleListener {
        default void onStarted(String runId, String callId, String toolName, String category) { }
        default void onCompleted(String runId, String callId, String toolName, String status,
            String code, long durationMs) { }
        default void onConfirmationRequired(String runId, String callId, String summary,
            String confirmationToken) { }
    }
}
