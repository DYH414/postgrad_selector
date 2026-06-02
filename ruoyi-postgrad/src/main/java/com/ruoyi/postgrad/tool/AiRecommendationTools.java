package com.ruoyi.postgrad.tool;

import com.alibaba.fastjson2.JSON;
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
import java.util.stream.Collectors;

@Component
public class AiRecommendationTools {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationTools.class);
    private static final ThreadLocal<String> CURRENT_CONVERSATION = new ThreadLocal<>();
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
        CURRENT_CONVERSATION.remove();
        CURRENT_BUDGET.remove();
        CURRENT_TRACE.remove();
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
                return JSON.toJSONString(p);
            }
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("programId", programId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("found", false);
        CURRENT_TRACE.get().record("getProgramDetail", args, summary);
        return "{}";
    }

    @Tool("在候选池内按条件筛选学校，如按城市、学校层次、分数范围过滤")
    public String searchPrograms(@P("筛选条件，JSON 格式，如 {\"city\":\"北京\",\"tier\":\"985\",\"minScore\":300,\"maxScore\":400}") String filters) {
        String conversationId = CURRENT_CONVERSATION.get();
        log.info("[Tool] searchPrograms called — conversationId={}, filters={}", conversationId, filters);
        if (conversationId == null) return "[]";
        if (!CURRENT_BUDGET.get().tryUse("searchPrograms", 1000)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }

        String poolJson = loadPoolJson(conversationId);
        if (poolJson == null) return "[]";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pool = JSON.parseObject(poolJson, List.class);

        Map<String, Object> filterMap = JSON.parseObject(filters);
        List<Map<String, Object>> result = pool.stream()
            .filter(p -> matchFilter(p, filterMap))
            .collect(Collectors.toList());
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("filters", filters);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", result.size());
        CURRENT_TRACE.get().record("searchPrograms", args, summary);
        return JSON.toJSONString(result);
    }

    private boolean matchFilter(Map<String, Object> program, Map<String, Object> filter) {
        if (filter.containsKey("city") && !filter.get("city").equals(program.get("city"))) return false;
        if (filter.containsKey("tier") && !filter.get("tier").equals(program.get("tier"))) return false;
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

    @Tool("扩展当前候选池，例如按地区、学校层次、分数范围加入更多候选")
    public String expandCandidatePool(@P("扩展条件 JSON") String filters) {
        if (!CURRENT_BUDGET.get().tryUse("expandCandidatePool", 600)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("filters", filters);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phase", "stub");
        CURRENT_TRACE.get().record("expandCandidatePool", args, summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addedCount", 0);
        result.put("duplicateCount", 0);
        result.put("totalPoolCount", 0);
        result.put("appliedFilters", filters);
        return JSON.toJSONString(result);
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
