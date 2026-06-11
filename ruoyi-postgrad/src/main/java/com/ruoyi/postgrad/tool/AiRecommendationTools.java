package com.ruoyi.postgrad.tool;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.ai.AiBookmark;
import com.ruoyi.postgrad.domain.ai.AiRecommendationSafety;
import com.ruoyi.postgrad.domain.ai.AiToolBudget;
import com.ruoyi.postgrad.domain.ai.AiToolTrace;
import com.ruoyi.postgrad.domain.ai.AiConstants;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.dto.CandidateProgramDTO;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    private static final Set<String> VALID_JUDGEMENTS = Set.of("reach", "steady", "safe");
    private static final int MAX_REASON_LENGTH = 300;
    private static final int MAX_PRO_CON_LENGTH = 20;
    private static final int MAX_LIST_ITEMS = 8;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private com.ruoyi.postgrad.mapper.AiDatabaseToolMapper aiDatabaseToolMapper;

    @Autowired
    private RecommendationLogMapper recommendationLogMapper;

    public static void setConversationId(String id) {
        CURRENT_CONVERSATION.set(id);
    }

    public static void startReportContext(String id) {
        CURRENT_CONVERSATION.set(id);
        CURRENT_BUDGET.set(AiToolBudget.reportDefaults());
        CURRENT_TRACE.set(new AiToolTrace());
    }

    public static void startChatContext(String id) {
        CURRENT_CONVERSATION.set(id);
        CURRENT_BUDGET.set(AiToolBudget.chatTurnDefaults());
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

    @Tool("获取指定学校的完整录取数据，包括近三年复试线、小分、招生计划、录取均分。每轮最多调用1次")
    public String getProgramDetail(@P("学校 programId") long programId) {
        String conversationId = CURRENT_CONVERSATION.get();
        log.info("[Tool] getProgramDetail called — conversationId={}, programId={}", conversationId, programId);
        if (conversationId == null) return "{}";

        // 对话级缓存：同一 programId 已查过直接返回，不占预算
        String cacheKey = conversationId + ":" + programId;
        String cached = DETAIL_CACHE.get(cacheKey);
        if (cached != null) {
            log.info("[Tool] getProgramDetail cache hit — conversationId={}, programId={}", conversationId, programId);
            return cached;
        }

        // 候选池校验：pid 不在 pool 中直接拒绝
        String poolJson = loadPoolJson(conversationId);
        if (poolJson == null) return "{}";
        List<CandidateProgramDTO> pool = loadPoolAsDto(conversationId);
        if (pool.isEmpty()) return "{}";
        boolean inPool = pool.stream().anyMatch(p -> p.getProgramId() != null && p.getProgramId() == programId);
        if (!inPool) {
            return "{\"error\":\"program_not_in_pool\",\"message\":\"该学校不在当前候选池中，请使用 searchPrograms 在候选池内筛选。\"}";
        }

        // 硬限制：每轮最多 1 次
        if (!CURRENT_BUDGET.get().tryUse("getProgramDetail", 800)) {
            CURRENT_TRACE.get().setExplorationLimited(true);
            return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true,\"message\":\"本轮已获取过详细数据(每轮最多1次)，请基于已有数据继续分析。\"}";
        }

        for (CandidateProgramDTO p : pool) {
            if (p.getProgramId() != null && p.getProgramId() == programId) {
                CURRENT_TRACE.get().recordDetail(programId);
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("programId", programId);
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("found", true);
                CURRENT_TRACE.get().record("getProgramDetail", args, summary);
                String result = JSON.toJSONString(p);
                DETAIL_CACHE.put(cacheKey, result);
                log.info("[AI-TRACE] TOOL getProgramDetail RESULT programId={}", programId);
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

        List<CandidateProgramDTO> pool = loadPoolAsDto(conversationId);

        Map<String, Object> filterMap = filters == null || filters.isBlank()
            ? new LinkedHashMap<>()
            : JSON.parseObject(filters);
        List<CandidateProgramDTO> result = pool.stream()
            .filter(p -> matchFilter(p, filterMap))
            .collect(Collectors.toList());
        int limit = Math.min(intVal(filterMap, "limit", SEARCH_PROGRAMS_DEFAULT_LIMIT), SEARCH_PROGRAMS_MAX_LIMIT);
        if (limit <= 0) limit = SEARCH_PROGRAMS_DEFAULT_LIMIT;
        List<Map<String, Object>> items = result.stream()
            .limit(limit)
            .map(this::searchSummaryItem)
            .collect(Collectors.toList());

        // 记录返回的 programId
        int recordedFromSearch = 0;
        for (Map<String, Object> item : items) {
            if (recordedFromSearch >= 5) break;
            Object pidObj = item.get("programId");
            if (pidObj instanceof Number n) {
                CURRENT_TRACE.get().recordSearchResult(n.longValue());
                recordedFromSearch++;
            }
        }

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
        String responseJson = JSON.toJSONString(response);
        log.info("[AI-TRACE] TOOL searchPrograms RESULT total={} returned={} hasMore={}", result.size(), items.size(), result.size() > items.size());
        return responseJson;
    }

    private boolean matchFilter(CandidateProgramDTO p, Map<String, Object> filter) {
        List<String> regionFilters = collectRegions(filter);
        if (!regionFilters.isEmpty()) {
            String rowProvince = p.getProvince() != null ? p.getProvince() : "";
            String rowCity = p.getCity() != null ? p.getCity() : "";
            boolean regionMatch = regionFilters.stream().anyMatch(r ->
                r.equals(rowProvince) || r.equals(rowCity));
            if (!regionMatch) return false;
        }
        if (filter.containsKey("tier") && !csvContains(filter.get("tier"), p.getSchoolTier())) return false;
        if (filter.containsKey("keyword") && !keywordMatches(p, filter.get("keyword"))) return false;
        if (filter.containsKey("minScore")) {
            double avg = p.getAvgAdmittedScore() != null ? p.getAvgAdmittedScore().doubleValue() : 0;
            double min = ((Number) filter.get("minScore")).doubleValue();
            if (avg < min) return false;
        }
        if (filter.containsKey("maxScore")) {
            double avg = p.getAvgAdmittedScore() != null ? p.getAvgAdmittedScore().doubleValue() : 0;
            double max = ((Number) filter.get("maxScore")).doubleValue();
            if (avg > max) return false;
        }
        return true;
    }

    /** 从 filter 中收集所有地区条件（regions / city / province），支持 String 和 JSON Array */
    @SuppressWarnings("unchecked")
    private static List<String> collectRegions(Map<String, Object> filter) {
        List<String> regions = new ArrayList<>();
        for (String key : List.of("regions", "city", "province")) {
            Object val = filter.get(key);
            if (val == null) continue;
            if (val instanceof List<?> list) {
                for (Object item : list) {
                    String s = String.valueOf(item).trim();
                    if (!s.isBlank()) regions.add(s);
                }
            } else {
                String s = String.valueOf(val).trim();
                if (!s.isBlank()) regions.add(s);
            }
        }
        return regions;
    }

    /**
     * Check whether {@code csvFilter} contains {@code actualValue}.
     * Supports both single values ("211") and comma-separated lists ("211,985,DOUBLE_FIRST").
     * The AI may send multiple tiers/provinces as a CSV string.
     */
    @SuppressWarnings("unchecked")
    private static boolean csvContains(Object filterValue, Object actualValue) {
        if (filterValue == null || actualValue == null) return false;
        // 支持 JSON Array：LLM 可能传 ["211","985","DOUBLE_FIRST"]
        if (filterValue instanceof List<?> list) {
            String actualStr = String.valueOf(actualValue);
            return list.stream().anyMatch(item -> actualStr.equals(String.valueOf(item).trim()));
        }
        String filterStr = String.valueOf(filterValue);
        String actualStr = String.valueOf(actualValue);
        if (filterStr.isBlank()) return true;
        for (String part : filterStr.split(",")) {
            if (part.trim().equals(actualStr)) return true;
        }
        return false;
    }

    private static boolean keywordMatches(CandidateProgramDTO p, Object keywordObj) {
        if (keywordObj == null) return true;
        String kw = String.valueOf(keywordObj).trim();
        if (kw.isEmpty()) return true;
        String school = p.getSchoolName() != null ? p.getSchoolName() : "";
        String prog = p.getProgramName() != null ? p.getProgramName() : "";
        String college = p.getCollegeName() != null ? p.getCollegeName() : "";
        return school.contains(kw) || prog.contains(kw) || college.contains(kw);
    }

    private Map<String, Object> searchSummaryItem(CandidateProgramDTO p) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("programId", p.getProgramId());
        item.put("schoolName", p.getSchoolName());
        item.put("schoolTier", p.getSchoolTier());
        item.put("province", p.getProvince());
        item.put("city", p.getCity());
        item.put("collegeName", p.getCollegeName());
        item.put("programName", p.getProgramName());
        item.put("avgAdmittedScore", p.getAvgAdmittedScore());
        item.put("gap", p.getGap());
        item.put("admissionLow", p.getAdmissionLow());
        item.put("admissionHigh", p.getAdmissionHigh());
        int quota = p.getUnifiedExamQuota() != null ? p.getUnifiedExamQuota()
            : (p.getPlanCount() != null ? p.getPlanCount() : 0);
        item.put("unifiedExamQuota", quota > 0 ? quota : null);
        item.put("planCount", p.getPlanCount());
        item.put("dataCompleteness", p.getDataCompleteness());
        // guard fields — compute from DTO if not already set
        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(p.toMap(), 0);
        item.put("quotaRisk", p.getQuotaRisk() != null ? p.getQuotaRisk() : guard.get("quotaRisk"));
        item.put("canBeSafe", guard.get("canBeSafe"));
        if (p.getSafeBlockReason() != null) item.put("safeBlockReason", p.getSafeBlockReason());
        else if (guard.get("safeBlockReason") != null) item.put("safeBlockReason", guard.get("safeBlockReason"));

        int gapVal = p.getGap();
        int quotaVal = quota;
        boolean canSafe = Boolean.TRUE.equals(guard.get("canBeSafe"));

        item.put("gapLabel", gapLabel(gapVal));
        item.put("quotaLabel", quotaLabel(quotaVal));
        item.put("safeLabel", canSafe ? "可以保底" : "不可保底");
        item.put("quotaRiskLabel", quotaRiskLabel(String.valueOf(guard.getOrDefault("quotaRisk", ""))));

        return item;
    }

    // ==================== 数字离散化工具方法 ====================

    private static int toInt(Object val, int fallback) {
        if (val instanceof Number n) return n.intValue();
        if (val == null) return fallback;
        try { return Integer.parseInt(String.valueOf(val)); } catch (NumberFormatException e) { return fallback; }
    }

    public static String gapLabel(int gap) {
        if (gap >= 15) return "分数大幅超出(+" + gap + ")";
        if (gap >= 5)  return "分数适度超出(+" + gap + ")";
        if (gap >= 0)  return "分数微弱超出(+" + gap + ")";
        if (gap >= -5) return "分数微弱不足(" + gap + ")";
        if (gap >= -15) return "分数适度不足(" + gap + ")";
        return "分数大幅不足(" + gap + ")";
    }

    public static String quotaLabel(int quota) {
        if (quota >= 30) return "招生充裕(" + quota + "人)";
        if (quota >= 10) return "招生正常(" + quota + "人)";
        if (quota >= 4)  return "招生偏少(" + quota + "人)";
        return "招生极少(" + quota + "人)";
    }

    /** 将数据库中原始 tier 值映射为用户可读的中文标签 */
    public static String tierDisplayLabel(Object value) {
        String v = value == null ? "" : String.valueOf(value);
        return switch (v) {
            case "985" -> "985";
            case "211" -> "211";
            case "DOUBLE_FIRST" -> "双一流";
            case "PUBLIC_REGULAR" -> "普通一本";
            case "PRIVATE" -> "民办";
            case "INDEPENDENT" -> "独立学院";
            case "RESEARCH_INSTITUTE" -> "科研院所";
            case "OTHER" -> "其他";
            default -> v.isBlank() ? "双非" : v;
        };
    }

    public static String quotaRiskLabel(String risk) {
        if (risk == null) return "";
        return switch (risk) {
            case "normal"    -> "名额风险=低";
            case "medium"    -> "名额风险=中";
            case "high"      -> "名额风险=高";
            case "very_high" -> "名额风险=极高";
            default          -> "";
        };
    }

    private Map<String, Object> buildSearchFacets(List<CandidateProgramDTO> result) {
        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("provinces", result.stream()
            .map(CandidateProgramDTO::getProvince)
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.groupingBy(String::valueOf, LinkedHashMap::new, Collectors.counting())));
        facets.put("tiers", result.stream()
            .map(CandidateProgramDTO::getSchoolTier)
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.groupingBy(String::valueOf, LinkedHashMap::new, Collectors.counting())));
        facets.put("quotaRisk", result.stream()
            .collect(Collectors.groupingBy(
                row -> AiRecommendationSafety.quotaRisk(integerValue(row.getUnifiedExamQuota() != null ? row.getUnifiedExamQuota() : row.getPlanCount())),
                LinkedHashMap::new,
                Collectors.counting())));
        return facets;
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

        List<CandidateProgramDTO> pool = loadPoolAsDto(conversationId);
        List<CandidateProgramDTO> result = new ArrayList<>();
        for (CandidateProgramDTO p : pool) {
            if (p.getProgramId() != null && ids.contains(p.getProgramId())) {
                result.add(p);
                CURRENT_TRACE.get().recordDetail(p.getProgramId());
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
        List<CandidateProgramDTO> pool = loadPoolAsDto(conversationId);
        if (pool == null) pool = new ArrayList<>();

        // 去重合并
        int added = 0;
        int dup = 0;
        int estimatedScore = estimateScore(conversationId);
        for (RowMap row : newRows) {
            Long newId = toLong(row.get("programId"));
            if (newId == null) continue;
            boolean exists = pool.stream().anyMatch(p -> newId.equals(p.getProgramId()));
            if (!exists) {
                CandidateProgramDTO dto = CandidateProgramDTO.fromRowMap(row, estimatedScore);
                pool.add(dto);
                added++;
            } else {
                dup++;
            }
        }

        // 写回 Redis
        redisTemplate.opsForValue().set(AiConstants.keyPool(conversationId), JSON.toJSONString(pool),
            java.time.Duration.ofMinutes(AiConstants.TTL_CONVERSATION));

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
            String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
            if (convJson != null && convJson.contains("预估总分:")) {
                int start = convJson.indexOf("预估总分:") + 6;
                int end = convJson.indexOf("\n", start);
                if (end < 0) end = Math.min(start + 4, convJson.length());
                return Integer.parseInt(convJson.substring(start, end).trim());
            }
        } catch (Exception ex) { log.warn("[ai] Non-critical operation failed", ex); }
        return 300;
    }

    @Tool("将当前讨论的学校加入推荐报告候选。必须在 getProgramDetail 后调用；重复调用会更新推荐理由")
    public String addToReport(
            @P("programId") long programId,
            @P("judgement，取值 reach/steady/safe 之一") String judgement,
            @P("推荐理由，一句话说明为何推荐") String reason,
            @P("优势列表") List<String> pros,
            @P("风险列表") List<String> cons,
            @P("取舍说明列表") List<String> tradeoffs,
            @P("行动建议") String recommendedAction) {

        String conversationId = CURRENT_CONVERSATION.get();
        if (conversationId == null) return "{\"error\":\"no_conversation\"}";

        // 1. 白名单校验
        if (judgement == null || !VALID_JUDGEMENTS.contains(judgement.trim())) {
            return "{\"error\":\"judgement 必须是 reach/steady/safe 之一\"}";
        }
        judgement = judgement.trim();

        // 2. 从候选池校验 programId 并注入 schoolName/programName，同时保存完整 fact 行供裁决使用
        List<CandidateProgramDTO> pool = loadPoolAsDto(conversationId);
        String schoolName = null, programName = null;
        boolean inPool = false;
        CandidateProgramDTO matchedDto = null;
        for (CandidateProgramDTO dto : pool) {
            if (dto.getProgramId() != null && dto.getProgramId() == programId) {
                schoolName = dto.getSchoolName();
                programName = dto.getProgramName();
                matchedDto = dto;
                inPool = true;
                break;
            }
        }
        if (!inPool) {
            return "{\"error\":\"programId=" + programId + " 不在当前候选池中\"}";
        }

        // 3. 长度截断
        reason = safeTruncate(reason, MAX_REASON_LENGTH);
        recommendedAction = safeTruncate(recommendedAction, MAX_REASON_LENGTH);
        pros = safeTruncateList(pros, MAX_LIST_ITEMS, MAX_PRO_CON_LENGTH);
        cons = safeTruncateList(cons, MAX_LIST_ITEMS, MAX_PRO_CON_LENGTH);
        tradeoffs = safeTruncateList(tradeoffs, MAX_LIST_ITEMS, MAX_PRO_CON_LENGTH);

        // 4. 构建书签
        AiBookmark bookmark = new AiBookmark();
        bookmark.setProgramId(programId);
        bookmark.setSchoolName(schoolName);
        bookmark.setProgramName(programName);
        bookmark.setReason(reason);
        bookmark.setPros(pros);
        bookmark.setCons(cons);
        bookmark.setTradeoffs(tradeoffs);
        bookmark.setRecommendedAction(recommendedAction);
        bookmark.setSource("conversation_ai");
        bookmark.setStatus("discussed");
        bookmark.setUserConfirmed(false);

        // 4a. 后端裁决：AI 的 judgement 必须经过事实层校验
        bookmark.setAiJudgement(judgement);
        AiRecommendationSafety.JudgementResult ruling =
            AiRecommendationSafety.finalJudgement(
                matchedDto.toMap(), judgement);
        bookmark.setFinalJudgement(ruling.finalJudgement());
        bookmark.setJudgement(ruling.finalJudgement()); // 兼容旧代码，judgement = finalJudgement
        bookmark.setAdjusted(ruling.adjusted());
        bookmark.setAdjustReason(ruling.adjustReason());
        if (ruling.adjusted()) {
            log.info("[Tool] addToReport judgement adjusted: programId={}, AI={} → final={}, reason={}",
                programId, judgement, ruling.finalJudgement(), ruling.adjustReason());
        }

        // 5. 读取现有书签列表，同 programId 覆盖
        String key = AiConstants.keyBookmarks(conversationId);
        String existing = redisTemplate.opsForValue().get(key);
        List<AiBookmark> bookmarks;
        if (existing != null && !existing.isBlank()) {
            bookmarks = JSON.parseArray(existing, AiBookmark.class);
        } else {
            bookmarks = new ArrayList<>();
        }
        bookmarks.removeIf(b -> b.getProgramId() == programId);
        bookmarks.add(bookmark);

        // 6. 写回 Redis
        redisTemplate.opsForValue().set(key, JSON.toJSONString(bookmarks),
            AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

        // 6a. 书签变更后持久化到 DB，防止 Redis 过期丢失
        persistBookmarkState(conversationId);

        // 7. 记录 trace
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("programId", programId);
        args.put("judgement", judgement);
        CURRENT_TRACE.get().record("addToReport", args, Map.of("totalBookmarks", bookmarks.size()));

        log.info("[Tool] addToReport — programId={}, judgement={}, total={}", programId, judgement, bookmarks.size());
        return "{\"ok\":true,\"total\":" + bookmarks.size() + "}";
    }

    @Tool("从推荐报告候选中移除一所学校")
    public String removeFromReport(@P("programId") long programId) {
        String conversationId = CURRENT_CONVERSATION.get();
        if (conversationId == null) return "{\"error\":\"no_conversation\"}";

        String key = AiConstants.keyBookmarks(conversationId);
        String existing = redisTemplate.opsForValue().get(key);
        if (existing == null || existing.isBlank()) {
            return "{\"total\":0}";
        }
        List<AiBookmark> bookmarks = JSON.parseArray(existing, AiBookmark.class);
        bookmarks.removeIf(b -> b.getProgramId() == programId);
        redisTemplate.opsForValue().set(key, JSON.toJSONString(bookmarks),
            AiConstants.TTL_CONVERSATION, AiConstants.TTL_CONVERSATION_UNIT);

        log.info("[Tool] removeFromReport — programId={}, remaining={}", programId, bookmarks.size());
        return "{\"ok\":true,\"total\":" + bookmarks.size() + "}";
    }

    /** 书签变更后持久化 conv+bookmarks 到 DB */
    private void persistBookmarkState(String conversationId) {
        try {
            String owner = redisTemplate.opsForValue().get(AiConstants.keyOwner(conversationId));
            if (owner == null) return;
            String convJson = redisTemplate.opsForValue().get(AiConstants.keyConv(conversationId));
            if (convJson == null || convJson.isBlank()) return;
            String bookmarkJson = redisTemplate.opsForValue().get(AiConstants.keyBookmarks(conversationId));

            RecommendationLog logEntry = new RecommendationLog();
            logEntry.setUserId(Long.parseLong(owner));
            logEntry.setProfileSnapshot(JSON.toJSONString(Map.of("userId", owner)));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("conversationId", conversationId);
            state.put("messages", JSON.parseArray(convJson, Map.class));
            if (bookmarkJson != null && !bookmarkJson.isBlank()) {
                state.put("bookmarksJson", bookmarkJson);
            }
            state.put("savedAt", System.currentTimeMillis());
            logEntry.setResultJson(JSON.toJSONString(state));
            logEntry.setRuleVersion("ai-conversation-state");
            logEntry.setDataVersion("1.0");
            logEntry.setIsPaid(0);
            recommendationLogMapper.insertRecommendationLog(logEntry);
        } catch (Exception ignored) {
        }
    }

    private String loadPoolJson(String conversationId) {
        String poolJson = redisTemplate.opsForValue().get(AiConstants.keyAgentPool(conversationId));
        if (poolJson == null) {
            poolJson = redisTemplate.opsForValue().get(AiConstants.keyPool(conversationId));
        }
        return poolJson;
    }

    private List<CandidateProgramDTO> loadPoolAsDto(String conversationId) {
        String json = loadPoolJson(conversationId);
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return JSON.parseArray(json, CandidateProgramDTO.class);
        } catch (Exception e) {
            log.warn("[Tool] Failed to parse pool as DTO: {}", e.getMessage());
            return Collections.emptyList();
        }
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

    private static String safeTruncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private static List<String> safeTruncateList(List<String> list, int maxItems, int maxItemLen) {
        if (list == null) return List.of();
        return list.stream()
            .limit(maxItems)
            .map(s -> safeTruncate(s, maxItemLen))
            .collect(Collectors.toList());
    }

    private static Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val == null) return null;
        try { return Long.parseLong(String.valueOf(val)); } catch (NumberFormatException e) { return null; }
    }
}
