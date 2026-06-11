package com.ruoyi.postgrad.domain.ai;

import java.util.concurrent.TimeUnit;

/**
 * AI 模块统一常量 —— Redis Key 模式与 TTL。
 * <p>
 * 所有 ai:* Redis key 的读写必须通过这里的 key 方法，不得在业务代码中硬编码字符串或 TTL 数值。
 */
public final class AiConstants {

    private AiConstants() {}

    // ── TTL ──

    /** 对话生命周期（conv / pool / bookmarks / owner）默认 TTL */
    public static final long TTL_CONVERSATION = 30;
    public static final TimeUnit TTL_CONVERSATION_UNIT = TimeUnit.MINUTES;

    /** 工具 trace（searched / discussed）TTL，略长于对话以防 trace 比主体先过期 */
    public static final long TTL_TRACE_MINUTES = 60;

    /** 报告及关联 key 延长期限 */
    public static final long TTL_REPORT = 7;
    public static final TimeUnit TTL_REPORT_UNIT = TimeUnit.DAYS;

    /** Agent / Analyze 候选池初始 TTL */
    public static final long TTL_AGENT_POOL_HOURS = 1;

    // ── Redis Key 模式 ──

    public static String keyConv(String conversationId) {
        return "ai:conv:" + conversationId;
    }

    public static String keyPool(String conversationId) {
        return "ai:pool:" + conversationId;
    }

    public static String keyBookmarks(String conversationId) {
        return "ai:bookmarks:" + conversationId;
    }

    public static String keyOwner(String conversationId) {
        return "ai:owner:" + conversationId;
    }

    public static String keySearched(String conversationId) {
        return "ai:searched:" + conversationId;
    }

    public static String keyDiscussed(String conversationId) {
        return "ai:discussed:" + conversationId;
    }

    public static String keyReport(long reportId) {
        return "ai:report:" + reportId;
    }

    public static String keyReportProgress(long reportId) {
        return "ai:report:progress:" + reportId;
    }

    public static String keyAgentPool(Object id) {
        return "ai:agent:pool:" + id;
    }

    public static String keyAnalyzePool(Object id) {
        return "ai:analyze:pool:" + id;
    }
}
