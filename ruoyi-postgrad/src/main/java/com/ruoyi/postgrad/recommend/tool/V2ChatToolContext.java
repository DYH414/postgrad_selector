package com.ruoyi.postgrad.recommend.tool;

import org.springframework.data.redis.core.StringRedisTemplate;

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

    private static final ThreadLocal<Context> CURRENT = new InheritableThreadLocal<>();

    private V2ChatToolContext() {}

    /**
     * 初始化当前线程的工具上下文。
     *
     * @param userId 当前用户 ID
     * @param redis  Redis 模板
     * @param mapper 推荐数据 Mapper
     */
    public static Context init(Long userId, StringRedisTemplate redis, RecommendationMapper mapper) {
        Context context = new Context(userId, redis, mapper);
        CURRENT.set(context);
        return context;
    }

    /** 清除当前线程的上下文。 */
    public static void clear() {
        CURRENT.remove();
    }

    /** @return 当前上下文，未初始化时返回 null */
    static Context current() {
        return CURRENT.get();
    }

    public static <T> T callWith(Context context, Supplier<T> supplier) {
        Context previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
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

    // ── 内部上下文对象 ──

    public static final class Context {
        private final Long userId;
        private final StringRedisTemplate redis;
        private final RecommendationMapper mapper;
        private boolean writeExecuted;
        private boolean draftChanged;
        private String lastActionResultJson;

        Context(Long userId, StringRedisTemplate redis, RecommendationMapper mapper) {
            this.userId = userId;
            this.redis = redis;
            this.mapper = mapper;
        }

        Long userId() { return userId; }
        StringRedisTemplate redis() { return redis; }
        RecommendationMapper mapper() { return mapper; }
        boolean writeExecuted() { return writeExecuted; }
        public boolean draftChanged() { return draftChanged; }
        public String lastActionResultJson() { return lastActionResultJson; }

        void markWriteExecuted(String actionResultJson) {
            this.writeExecuted = true;
            this.draftChanged = true;
            this.lastActionResultJson = actionResultJson;
        }
    }
}
