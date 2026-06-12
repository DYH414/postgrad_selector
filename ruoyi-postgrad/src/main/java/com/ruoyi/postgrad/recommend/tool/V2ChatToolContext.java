package com.ruoyi.postgrad.recommend.tool;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.ruoyi.postgrad.mapper.RecommendationMapper;

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

    private V2ChatToolContext() {}

    /**
     * 初始化当前线程的工具上下文。
     *
     * @param userId 当前用户 ID
     * @param redis  Redis 模板
     * @param mapper 推荐数据 Mapper
     */
    public static void init(Long userId, StringRedisTemplate redis, RecommendationMapper mapper) {
        CURRENT.set(new Context(userId, redis, mapper));
    }

    /** 清除当前线程的上下文。 */
    public static void clear() {
        CURRENT.remove();
    }

    /** @return 当前上下文，未初始化时返回 null */
    static Context current() {
        return CURRENT.get();
    }

    // ── 内部上下文对象 ──

    record Context(Long userId, StringRedisTemplate redis, RecommendationMapper mapper) {}
}
