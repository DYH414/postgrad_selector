package com.ruoyi.postgrad.tool;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.AiRecommendationSafety;
import com.ruoyi.postgrad.domain.AiToolBudget;
import com.ruoyi.postgrad.domain.AiToolTrace;
import com.ruoyi.postgrad.domain.RowMap;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class AiRecommendationTools {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationTools.class);
    private static final int SEARCH_PROGRAMS_DEFAULT_LIMIT = 12;
    private static final int SEARCH_PROGRAMS_MAX_LIMIT = 20;
    private static final ThreadLocal<String> CURRENT_CONVERSATION = new ThreadLocal<>();

    /** 对话级工具结果缓存，key = "conversationId:programId"，避免同一对话中重复查询 */
    private static final ConcurrentHashMap<String, String> DETAIL_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<AiToolBudget> CURRENT_BUDGET =
        ThreadLocal.withInitial(AiToolBudget::reportDefaults);
    private static final ThreadLocal<AiToolTrace> CURRENT_TRACE =
        ThreadLocal.withInitial(AiToolTrace::new);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private com.ruoyi.postgrad.mapper.AiDatabaseToolMapper aiDatabaseToolMapper;

    public static void setConversationId(String id) {
        CURRENT_CONVERSATION.set(id);
    }

    public static void startReportContext(String id) {
        CURRENT_CONVERSATION.set(id);
        CURRENT_BUDGET.set(AiToolBudget.reportDefaults());
        CURRENT_TRACE.set(new AiToolTrace());
    }

    public static AiToolTrace currentTrace() {
        return CURRENT_TRACE.get();
    }

    public static void clear() {
        String convId = CURRENT_CONVERSATION.get();
        CURRENT_CONVERSATION.remove();
        CURRENT_BUDGET.remove();
        CURRENT_TRACE.remove();
        // 清理该对话的工具结果缓存
        if (convId != null) {
            String prefix = convId + ":";
            DETAIL_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    @Tool("获取指定学校的完整录取数据，包括近三年复试线、小分、招生计划、录取均分")
    public String getProgramDetail(@P("学校 programId") long programId) {
        String conversationId = CURRENT_CONVERSATION.get();
        log.info("[Tool] getProgramDetail called — conversationId={}, programId={}", conversationId, programId);
        if (conversationId == null) return "{}";
        if (!CURRENT_BUDGET.get().tryUse("getProgramDetail", 800)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }

        // 对话级缓存：同一 programId 在本次对话中已查过，直接返回
        String cacheKey = conversationId + ":" + programId;
        String cached = DETAIL_CACHE.get(cacheKey);
        if (cached != null) {
            log.info("[Tool] getProgramDetail cache hit — conversationId={}, programId={}", conversationId, programId);
            return cached;
        }

        String poolJson = loadPoolJson(conversationId);
        if (poolJson == null) return "{}";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pool = JSON.parseObject(poolJson, List.class);
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj));
            if (pid == programId) {
                CURRENT_TRACE.get().recordDetail(programId);
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("programId", programId);
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("found", true);
                CURRENT_TRACE.get().record("getProgramDetail", args, summary);
                String result = JSON.toJSONString(p);
                DETAIL_CACHE.put(cacheKey, result);
                return result;
            }
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("programId", programId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("found", false);
        CURRENT_TRACE.get().record("getProgramDetail", args, summary);
        return "{}";
    }

    @Tool("在候选池内按条件筛选学校，返回有限摘要和总数。需要完整信息时再用 getProgramDetail(programId)")
    public String searchPrograms(@P("筛选条件，JSON 格式，如 {\"city\":\"北京\",\"tier\":\"985\",\"minScore\":300,\"maxScore\":400,\"limit\":12}") String filters) {
        String conversationId = CURRENT_CONVERSATION.get();
        log.info("[Tool] searchPrograms called — conversationId={}, filters={}", conversationId, filters);
        if (conversationId == null) return "{\"total\":0,\"returned\":0,\"hasMore\":false,\"items\":[]}";
        if (!CURRENT_BUDGET.get().tryUse("searchPrograms", 1000)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }

        String poolJson = loadPoolJson(conversationId);
        if (poolJson == null) return "{\"total\":0,\"returned\":0,\"hasMore\":false,\"items\":[]}";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pool = JSON.parseObject(poolJson, List.class);

        Map<String, Object> filterMap = filters == null || filters.isBlank()
            ? new LinkedHashMap<>()
            : JSON.parseObject(filters);
        List<Map<String, Object>> result = pool.stream()
            .filter(p -> matchFilter(p, filterMap))
            .collect(Collectors.toList());
        int limit = Math.min(intVal(filterMap, "limit", SEARCH_PROGRAMS_DEFAULT_LIMIT), SEARCH_PROGRAMS_MAX_LIMIT);
        if (limit <= 0) limit = SEARCH_PROGRAMS_DEFAULT_LIMIT;
        List<Map<String, Object>> items = result.stream()
            .limit(limit)
            .map(this::searchSummaryItem)
            .collect(Collectors.toList());

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("filters", filters);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", result.size());
        summary.put("returned", items.size());
        CURRENT_TRACE.get().record("searchPrograms", args, summary);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", result.size());
        response.put("returned", items.size());
        response.put("hasMore", result.size() > items.size());
        response.put("items", items);
        response.put("facets", buildSearchFacets(result));
        response.put("hint", result.size() > items.size()
            ? "仅返回前" + items.size() + "条摘要。若要查看某所学校完整数据，请调用 getProgramDetail(programId)；若要继续缩小范围，请追加 province/tier/minScore/maxScore/limit。"
            : "已返回全部匹配摘要。需要完整数据请调用 getProgramDetail(programId)。");
        return JSON.toJSONString(response);
    }

    private boolean matchFilter(Map<String, Object> program, Map<String, Object> filter) {
        if (filter.containsKey("city") && !filter.get("city").equals(program.get("city"))) return false;
        if (filter.containsKey("province") && !filter.get("province").equals(program.get("province"))) return false;
        if (filter.containsKey("tier") && !filter.get("tier").equals(program.get("schoolTier"))) return false;
        if (filter.containsKey("minScore")) {
            Object avgObj = program.get("avgAdmittedScore");
            double avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : 0;
            double min = ((Number) filter.get("minScore")).doubleValue();
            if (avg < min) return false;
        }
        if (filter.containsKey("maxScore")) {
            Object avgObj = program.get("avgAdmittedScore");
            double avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : 0;
            double max = ((Number) filter.get("maxScore")).doubleValue();
            if (avg > max) return false;
        }
        return true;
    }

    private Map<String, Object> searchSummaryItem(Map<String, Object> program) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("programId", program.get("programId"));
        item.put("schoolName", program.get("schoolName"));
        item.put("schoolTier", program.get("schoolTier"));
        item.put("province", program.get("province"));
        item.put("city", program.get("city"));
        item.put("collegeName", program.get("collegeName"));
        item.put("programName", program.get("programName"));
        item.put("avgAdmittedScore", program.get("avgAdmittedScore"));
        item.put("gap", program.get("gap"));
        item.put("admissionLow", program.get("admissionLow"));
        item.put("admissionHigh", program.get("admissionHigh"));
        item.put("unifiedExamQuota", program.getOrDefault("unifiedExamQuota", program.get("planCount")));
        item.put("planCount", program.get("planCount"));
        item.put("dataCompleteness", program.get("dataCompleteness"));
        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(program, 0);
        item.put("quotaRisk", guard.get("quotaRisk"));
        item.put("canBeSafe", guard.get("canBeSafe"));
        if (guard.get("safeBlockReason") != null) {
            item.put("safeBlockReason", guard.get("safeBlockReason"));
        }
        return item;
    }

    private Map<String, Object> buildSearchFacets(List<Map<String, Object>> result) {
        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("provinces", countBy(result, "province"));
        facets.put("tiers", countBy(result, "schoolTier"));
        facets.put("quotaRisk", result.stream()
            .collect(Collectors.groupingBy(
                row -> AiRecommendationSafety.quotaRisk(integerValue(row.getOrDefault("unifiedExamQuota", row.get("planCount")))),
                LinkedHashMap::new,
                Collectors.counting())));
        return facets;
    }

    private Map<String, Long> countBy(List<Map<String, Object>> rows, String key) {
        return rows.stream()
            .map(row -> row.get(key))
            .filter(value -> value != null && !String.valueOf(value).isBlank())
            .collect(Collectors.groupingBy(
                String::valueOf,
                LinkedHashMap::new,
                Collectors.counting()));
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Tool("直接查询 MySQL 数据库中的院校数据，不受候选池限制。可按关键词、学校层次、省份、分数范围筛选")
    public String queryDatabase(@P("查询条件，JSON 格式，如 {\"keyword\":\"计算机\",\"tier\":\"985\",\"province\":\"北京\",\"minScore\":300,\"maxScore\":400,\"limit\":20}") String filters) {
        log.info("[Tool] queryDatabase called — filters={}", filters);
        if (!CURRENT_BUDGET.get().tryUse("queryDatabase", 500)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }

        Map<String, Object> filterMap = filters == null || filters.isEmpty()
            ? new LinkedHashMap<>()
            : JSON.parseObject(filters);
        String keyword = stringVal(filterMap, "keyword", null);
        String tier = stringVal(filterMap, "tier", null);
        String province = stringVal(filterMap, "province", null);
        Integer minScore = nullableInt(filterMap, "minScore");
        Integer maxScore = nullableInt(filterMap, "maxScore");
        int limit = Math.min(intVal(filterMap, "limit", 20), 30);

        List<RowMap> rows = aiDatabaseToolMapper.querySchools(keyword, tier, province, minScore, maxScore, limit);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("filters", filters);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", rows.size());
        summary.put("limit", limit);
        CURRENT_TRACE.get().record("queryDatabase", args, summary);
        return JSON.toJSONString(rows);
    }

    @Tool("横向对比多所学校的录取数据，返回详细对比")
    public String comparePrograms(@P("学校 programId 列表") List<Long> ids) {
        String conversationId = CURRENT_CONVERSATION.get();
        log.info("[Tool] comparePrograms called — conversationId={}, ids={}", conversationId, ids);
        if (conversationId == null) return "[]";
        if (!CURRENT_BUDGET.get().tryUse("comparePrograms", 1200)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }

        String poolJson = loadPoolJson(conversationId);
        if (poolJson == null) return "[]";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pool = JSON.parseObject(poolJson, List.class);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj));
            if (ids.contains(pid)) {
                result.add(p);
                CURRENT_TRACE.get().recordDetail(pid);
            }
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("ids", ids);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", result.size());
        CURRENT_TRACE.get().record("comparePrograms", args, summary);
        return JSON.toJSONString(result);
    }

    @Tool("扩展当前候选池，从数据库查询新学校加入候选池。当用户想添加新地区、新层次或候选池外学校时使用")
    public String expandCandidatePool(@P("扩展条件 JSON，如 {\"province\":\"北京\"} 或 {\"keyword\":\"软件工程\",\"tier\":\"211\"}") String filters) {
        if (!CURRENT_BUDGET.get().tryUse("expandCandidatePool", 600)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }

        String conversationId = CURRENT_CONVERSATION.get();
        if (conversationId == null) return "{\"error\":\"no_conversation\"}";

        Map<String, Object> filterMap = filters == null || filters.isEmpty()
            ? new LinkedHashMap<>()
            : JSON.parseObject(filters);
        String keyword = stringVal(filterMap, "keyword", null);
        String tier = stringVal(filterMap, "tier", null);
        String province = stringVal(filterMap, "province", null);
        int limit = Math.min(intVal(filterMap, "limit", 30), 30);

        // 查询数据库
        List<RowMap> newRows = aiDatabaseToolMapper.querySchools(keyword, tier, province, null, null, limit);

        // 读取现有候选池
        String poolJson = loadPoolJson(conversationId);
        List<Map<String, Object>> pool;
        if (poolJson != null) {
            pool = new ArrayList<>(JSON.parseObject(poolJson, List.class));
        } else {
            pool = new ArrayList<>();
        }

        // 去重合并
        int added = 0;
        int dup = 0;
        for (RowMap row : newRows) {
            Object pid = row.get("programId");
            long newId = pid instanceof Number ? ((Number) pid).longValue() : Long.parseLong(String.valueOf(pid));
            boolean exists = pool.stream().anyMatch(p -> {
                Object existingId = p.get("programId");
                return existingId != null && existingId.equals(newId);
            });
            if (!exists) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("programId", newId);
                item.put("schoolName", row.get("schoolName"));
                item.put("schoolTier", row.get("schoolTier"));
                item.put("city", row.get("city"));
                item.put("province", row.get("province"));
                item.put("programName", row.get("programName"));
                item.put("collegeName", row.get("collegeName"));
                item.put("degreeType", row.get("degreeType"));
                Object avgObj = row.get("avgAdmittedScore");
                int avg = avgObj instanceof Number ? ((Number) avgObj).intValue() : 0;
                item.put("avgAdmittedScore", avg);
                item.put("gap", avg > 0 ? (estimateScore(conversationId) - avg) : null);
                item.put("scoreLine", row.get("scoreLine"));
                item.put("admissionLow", row.get("admissionLow"));
                item.put("admissionHigh", row.get("admissionHigh"));
                item.put("planCount", row.get("planCount"));
                item.put("admittedCount", row.get("admittedCount"));
                item.put("retestCount", row.get("retestCount"));
                item.put("dataYear", row.get("dataYear"));
                item.put("dataCompleteness", row.get("dataCompleteness"));
                item.put("sourceUrl", row.get("sourceUrl"));
                item.put("sourceOwner", row.get("sourceOwner"));
                pool.add(item);
                added++;
            } else {
                dup++;
            }
        }

        // 写回 Redis
        redisTemplate.opsForValue().set("ai:pool:" + conversationId, JSON.toJSONString(pool),
            java.time.Duration.ofMinutes(30));

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("filters", filters);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("addedCount", added);
        summary.put("duplicateCount", dup);
        summary.put("totalPoolCount", pool.size());
        CURRENT_TRACE.get().record("expandCandidatePool", args, summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addedCount", added);
        result.put("duplicateCount", dup);
        result.put("totalPoolCount", pool.size());
        result.put("newSchools", newRows.stream().limit(5).map(r ->
            r.get("schoolName") + " | " + r.get("programName")).toList());
        return JSON.toJSONString(result);
    }

    /** 从对话 Redis 中提取用户预估分（用于 gap 计算） */
    private int estimateScore(String conversationId) {
        try {
            String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
            if (convJson != null && convJson.contains("预估总分:")) {
                int start = convJson.indexOf("预估总分:") + 6;
                int end = convJson.indexOf("\n", start);
                if (end < 0) end = Math.min(start + 4, convJson.length());
                return Integer.parseInt(convJson.substring(start, end).trim());
            }
        } catch (Exception ignored) {}
        return 300;
    }

    @Tool("核验院校官网或研究生院信息。Phase 1 仅返回本地数据状态，不联网")
    public String verifyOfficialInfo(@P("核验输入 JSON") String inputJson) {
        if (!CURRENT_BUDGET.get().tryUse("verifyOfficialInfo", 500)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("input", inputJson);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("provider", null);
        CURRENT_TRACE.get().record("verifyOfficialInfo", args, summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verificationStatus", "local_data_only");
        result.put("verificationProvider", null);
        return JSON.toJSONString(result);
    }

    private String loadPoolJson(String conversationId) {
        String poolJson = redisTemplate.opsForValue().get("ai:agent:pool:" + conversationId);
        if (poolJson == null) {
            poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
        }
        return poolJson;
    }

    private static String stringVal(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : v.toString();
    }

    private static int intVal(Map<String, Object> map, String key, int fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static Integer nullableInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
