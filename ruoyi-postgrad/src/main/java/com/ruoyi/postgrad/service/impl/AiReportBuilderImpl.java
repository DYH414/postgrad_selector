package com.ruoyi.postgrad.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.AiReportSupport;
import com.ruoyi.postgrad.domain.AiRecommendationSafety;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.service.AiReportBuilder;
import dev.langchain4j.model.chat.ChatModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiReportBuilderImpl implements AiReportBuilder {
    private static final int PROMPT_POOL_ROW_LIMIT = 120;
    private static final Logger log = LoggerFactory.getLogger(AiReportBuilderImpl.class);

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Override
    public Map<String, Object> buildConversationReport(ChatModel chatModel, String conversationJson,
        String poolJson, int estimatedScore, Map<String, Object> preferenceProfile) {
        // 从对话历史中提取讨论过的学校ID，确保它们在候选池摘要中优先出现
        Set<Long> discussedIds = extractDiscussedProgramIds(conversationJson);
        String poolSummary = buildPoolSummary(poolJson, estimatedScore, discussedIds);
        String prompt = basePrompt(poolSummary, preferenceProfile, estimatedScore) + "\n## 对话历史\n" + conversationJson;
        log.info("[AI-TRACE] ======== REPORT-CONVERSATION ========");
        log.info("[AI-TRACE] discussedProgramIds={}", discussedIds);
        log.info("[AI-TRACE] REPORT PROMPT (first 800 chars):\n{}...",
            prompt.length() > 800 ? prompt.substring(0, 800) : prompt);
        log.debug("[AI-TRACE] REPORT PROMPT (full):\n{}", prompt);
        String aiRaw = chatModel.chat(prompt);
        log.info("[AI-TRACE] REPORT AI RAW OUTPUT:\n{}", aiRaw);
        return hydrateReportPrograms(parseReportJson(aiRaw, poolJson), estimatedScore, poolJson);
    }

    @Override
    public Map<String, Object> buildAnalyzeReport(ChatModel chatModel, String poolJson,
        int estimatedScore, Map<String, Object> preferenceProfile) {
        String prompt = buildAnalyzePrompt(poolJson, estimatedScore, preferenceProfile);
        log.info("[AI-TRACE] ======== REPORT-ANALYZE ========");
        log.info("[AI-TRACE] REPORT PROMPT (first 800 chars):\n{}...",
            prompt.length() > 800 ? prompt.substring(0, 800) : prompt);
        log.debug("[AI-TRACE] REPORT PROMPT (full):\n{}", prompt);
        String aiRaw = chatModel.chat(prompt);
        log.info("[AI-TRACE] REPORT AI RAW OUTPUT:\n{}", aiRaw);
        return hydrateReportPrograms(parseReportJson(aiRaw, poolJson), estimatedScore, poolJson);
    }

    String buildConversationPrompt(String convJson, String poolJson, Map<String, Object> preferenceProfile) {
        Set<Long> discussedIds = extractDiscussedProgramIds(convJson);
        String poolSummary = buildPoolSummary(poolJson, 0, discussedIds);
        return basePrompt(poolSummary, preferenceProfile, 0) + "\n## 对话历史\n" + convJson;
    }

    String buildAnalyzePrompt(String poolJson, int estimatedScore, Map<String, Object> preferenceProfile) {
        String poolSummary = buildPoolSummary(poolJson, estimatedScore, Set.of());
        return basePrompt(poolSummary, preferenceProfile, estimatedScore) + "\n## 用户预估分\n" + estimatedScore;
    }

    private String basePrompt(String poolSummary, Map<String, Object> preferenceProfile, int estimatedScore) {
        return """
            这不是对话。请直接输出推荐报告 JSON，不要回复确认语。

            ## preferenceProfile
            %s

            ## 候选学校事实摘要
            %s

            ## 要求
            1. 优先使用对话历史中你已明确推荐/认可过的学校。对话中讨论过的学校是用户和你共同筛选的结果，不要重新选一批不同的学校。如果对话历史的推荐学校与候选摘要矛盾（如被canBeSafe=false封锁），请在 reason/cons 中说明并替换
            2. 只能从候选列表中选学校，programId 必须与候选列表一致
            3. 按冲刺/稳妥/保底三档推荐，每档 1-3 所
            4. AI 只输出观点字段，事实字段由后端数据库补全
            5. 不要输出 schoolName、collegeName、programName、分数、招生人数等事实字段
            6. 推荐理由必须基于候选事实摘要和 preferenceProfile 的取舍
            7. canBeSafe=false 是事实硬约束，禁止放入保底档；这类项目即使分数差较大，也只能作为稳妥/待核验/线索

            ## 输出格式（严格 JSON）
            {"summary":"一句话总结","tiers":[{"level":"reach","label":"冲刺档","schools":[{"programId":1,"judgement":"small_reach","risk":"high","decision":"适合作为冲刺候选","reason":"推荐理由","pros":["优势"],"cons":["风险"],"tradeoffs":["取舍"],"recommendedAction":"行动建议"}]},{"level":"steady","label":"稳妥档","schools":[]},{"level":"safe","label":"保底档","schools":[]}]}
            """.formatted(JSON.toJSONString(defaultedPreferenceProfile(preferenceProfile)), poolSummary);
    }

    private Map<String, Object> defaultedPreferenceProfile(Map<String, Object> preferenceProfile) {
        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> source = preferenceProfile == null ? Collections.emptyMap() : preferenceProfile;
        profile.put("riskPreference", source.getOrDefault("riskPreference", "balanced"));
        profile.put("schoolTierPreference", source.getOrDefault("schoolTierPreference", "no_strict_requirement"));
        profile.put("regionStrategy", source.getOrDefault("regionStrategy", "no_strict_requirement"));
        profile.put("targetRegions", source.getOrDefault("targetRegions", "不限"));
        return profile;
    }

    /**
     * 从对话 JSON 中提取所有出现过的 programId（来自工具调用和 AI 回复）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Set<Long> extractDiscussedProgramIds(String conversationJson) {
        Set<Long> ids = new LinkedHashSet<>();
        if (conversationJson == null || conversationJson.isBlank()) return ids;
        try {
            List<Map<String, Object>> messages = (List) JSON.parseArray(conversationJson, Map.class);
            for (Map<String, Object> msg : messages) {
                String content = (String) msg.get("content");
                if (content == null) continue;
                // 匹配 "programId":123 或 ID:123 或 getProgramDetail(123)
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(?:programId[\"']?\\s*[:=]\\s*|ID[:]?\\s*|getProgramDetail\\()\\s*(\\d{1,10})\\b").matcher(content);
                while (m.find()) {
                    try { ids.add(Long.parseLong(m.group(1))); } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            log.warn("[AI-TRACE] Failed to extract discussed programIds: {}", e.getMessage());
        }
        return ids;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String buildPoolSummary(String poolJson, int estimatedScore, Set<Long> priorityIds) {
        if (poolJson == null || poolJson.isBlank() || "[]".equals(poolJson.trim())) {
            return "（无候选学校数据）";
        }
        try {
            List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
            // 把对话中讨论过的学校排到最前面，确保报告 AI 一定能看到它们
            if (!priorityIds.isEmpty()) {
                pool.sort((a, b) -> {
                    long idA = longValue(a.get("programId"));
                    long idB = longValue(b.get("programId"));
                    boolean aPrio = priorityIds.contains(idA);
                    boolean bPrio = priorityIds.contains(idB);
                    if (aPrio && !bPrio) return -1;
                    if (!aPrio && bPrio) return 1;
                    return 0;
                });
            }
            StringBuilder sb = new StringBuilder();
            if (!priorityIds.isEmpty()) {
                sb.append("⚠ 以下「对话已讨论」的学校已排在候选列表最前面，报告应优先从中选择：\n");
            }
            int index = 1;
            for (Map<String, Object> row : pool) {
                if (index > PROMPT_POOL_ROW_LIMIT) {
                    sb.append("... 已截断，仅发送前 ").append(PROMPT_POOL_ROW_LIMIT).append(" 条代表行给模型\n");
                    break;
                }
                long pid = longValue(row.get("programId"));
                boolean isDiscussed = priorityIds.contains(pid);
                sb.append(index).append(". ID:").append(row.get("programId"));
                if (isDiscussed) sb.append(" [对话已讨论]");
                sb.append(" | ").append(row.getOrDefault("schoolName", "?"));
                sb.append(" | 专业:").append(row.getOrDefault("programName", ""));
                sb.append(" | 学院:").append(row.getOrDefault("collegeName", ""));
                sb.append(" | 地区:").append(row.getOrDefault("province", row.getOrDefault("city", "")));
                sb.append(" | 层次:").append(row.getOrDefault("schoolTier", ""));
                sb.append(" | 均分:").append(displayInt(row.get("avgAdmittedScore")));
                sb.append(" | 差距:").append(displaySignedInt(row.get("gap")));
                sb.append(" | 最低录取:").append(displayInt(row.get("admissionLow")));
                sb.append(" | 招生:").append(displayInt(row.getOrDefault("unifiedExamQuota", row.get("planCount"))));
                sb.append(" | 完整度:").append(row.getOrDefault("dataCompleteness", ""));
                Map<String, Object> guard = AiRecommendationSafety.safeEligibility(row, estimatedScore);
                sb.append(" | quotaRisk:").append(guard.get("quotaRisk"));
                sb.append(" | canBeSafe:").append(guard.get("canBeSafe"));
                Object reason = guard.get("safeBlockReason");
                if (reason != null) sb.append(" | 保底限制:").append(reason);
                sb.append("\n");
                index++;
            }
            return sb.toString();
        } catch (Exception e) {
            return "（候选学校数据解析失败）";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> parseReportJson(String aiResponse, String poolJson) {
        try {
            String cleaned = stripMarkdown(aiResponse);
            return (Map<String, Object>) JSON.parseObject(cleaned, Map.class);
        } catch (Exception e) {
            return ruleBasedFallback(poolJson);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> ruleBasedFallback(String poolJson) {
        List<Map<String, Object>> pool;
        try {
            pool = (List) JSON.parseArray(poolJson == null ? "[]" : poolJson, Map.class);
        } catch (Exception e) {
            pool = Collections.emptyList();
        }

        List<Map<String, Object>> reach = new ArrayList<>();
        List<Map<String, Object>> steady = new ArrayList<>();
        List<Map<String, Object>> safe = new ArrayList<>();
        for (int i = 0; i < pool.size() && i < 6; i++) {
            Map<String, Object> row = pool.get(i);
            Map<String, Object> school = new LinkedHashMap<>();
            school.put("programId", row.get("programId"));
            String level = i < 2 ? "reach" : i < 4 ? "steady" : "safe";
            school.put("judgement", "reach".equals(level) ? "small_reach" : level);
            school.put("risk", "reach".equals(level) ? "high" : "safe".equals(level) ? "low" : "medium");
            school.put("decision", "reach".equals(level) ? "适合作为冲刺候选" : "safe".equals(level) ? "适合作为保底候选" : "适合作为稳妥候选");
            school.put("reason", "基于候选池数据自动生成的兜底推荐");
            school.put("pros", Collections.emptyList());
            school.put("cons", Collections.emptyList());
            school.put("tradeoffs", Collections.emptyList());
            school.put("recommendedAction", "建议核验院校官网后再加入最终备选");
            if ("reach".equals(level)) reach.add(school);
            else if ("steady".equals(level)) steady.add(school);
            else safe.add(school);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", pool.isEmpty() ? "候选学校不足，暂无法生成完整推荐" : "基于本地候选池生成兜底推荐");
        report.put("tiers", List.of(tier("reach", "冲刺档", reach), tier("steady", "稳妥档", steady), tier("safe", "保底档", safe)));
        return report;
    }

    private Map<String, Object> tier(String level, String label, List<Map<String, Object>> schools) {
        Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("level", level);
        tier.put("label", label);
        tier.put("schools", schools);
        return tier;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> hydrateReportPrograms(Map<String, Object> report, int estimatedScore, String poolJson) {
        Map<String, Object> result = new LinkedHashMap<>(report);
        Map<Long, Map<String, Object>> poolMap = parsePoolMap(poolJson);
        List<Long> invalidIds = new ArrayList<>();
        List<Long> duplicateIds = new ArrayList<>();
        List<Long> missingDetailIds = new ArrayList<>();
        List<Long> safeDowngradedIds = new ArrayList<>();
        List<Map<String, Object>> safeDowngradedSchools = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();

        List<Map<String, Object>> tiers = (List<Map<String, Object>>) result.get("tiers");
        if (tiers == null) {
            result.put("tiers", Collections.emptyList());
            return result;
        }

        for (Map<String, Object> tier : tiers) {
            String tierLevel = String.valueOf(tier.getOrDefault("level", ""));
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
            if (schools == null) {
                tier.put("schools", Collections.emptyList());
                continue;
            }
            List<Map<String, Object>> hydratedSchools = new ArrayList<>();
            for (Map<String, Object> school : schools) {
                Long programId = longValue(school.get("programId"));
                if (programId == null || !poolMap.containsKey(programId)) {
                    if (programId != null) invalidIds.add(programId);
                    continue;
                }
                if (!seen.add(programId)) {
                    duplicateIds.add(programId);
                    continue;
                }
                Map<String, Object> detail = recommendationMapper.selectProgramForRecommendation(programId);
                if (detail == null) {
                    missingDetailIds.add(programId);
                    continue;
                }
                Map<String, Object> hydrated = hydratedReportSchool(school, detail, estimatedScore, "safe".equals(tierLevel));
                if ("safe".equals(tierLevel) && Boolean.FALSE.equals(hydrated.get("canBeSafe"))) {
                    safeDowngradedIds.add(programId);
                    safeDowngradedSchools.add(hydrated);
                    continue;
                }
                hydratedSchools.add(hydrated);
            }
            tier.put("schools", hydratedSchools);
        }

        if (!safeDowngradedSchools.isEmpty()) {
            Map<String, Object> steadyTier = findTier(tiers, "steady");
            if (steadyTier != null) {
                List<Map<String, Object>> steadySchools = (List<Map<String, Object>>) steadyTier.get("schools");
                if (steadySchools == null) steadySchools = new ArrayList<>();
                steadySchools.addAll(safeDowngradedSchools);
                steadyTier.put("schools", steadySchools);
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        Object existingMeta = result.get("meta") != null ? result.get("meta") : result.get("metadata");
        if (existingMeta instanceof Map) meta.putAll((Map) existingMeta);
        meta.put("invalidProgramIds", invalidIds);
        meta.put("duplicateProgramIds", duplicateIds);
        meta.put("missingDetailProgramIds", missingDetailIds);
        meta.put("safeDowngradedProgramIds", safeDowngradedIds);
        result.put("meta", meta);
        result.put("metadata", meta);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<Long, Map<String, Object>> parsePoolMap(String poolJson) {
        Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
        if (poolJson == null || poolJson.isBlank() || "[]".equals(poolJson.trim())) return poolMap;
        for (Object item : JSON.parseArray(poolJson)) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> row = (Map<String, Object>) item;
            Long programId = longValue(row.get("programId"));
            if (programId != null) poolMap.put(programId, row);
        }
        return poolMap;
    }

    private Map<String, Object> hydratedReportSchool(Map<String, Object> opinionSource, Map<String, Object> detail,
        int estimatedScore, boolean fromSafeTier) {
        Map<String, Object> item = new LinkedHashMap<>();
        for (String key : List.of("programId", "schoolId", "schoolName", "province", "city", "collegeName",
            "programName", "programCode", "degreeType", "examCombo", "schoolTier", "scoreLine", "admissionLow",
            "admissionHigh", "planCount", "unifiedExamQuota", "admittedCount", "retestCount", "dataYear",
            "dataCompleteness", "sourceUrl", "sourceOwner")) {
            if (detail.containsKey(key)) item.put(key, detail.get(key));
        }

        Integer avg = integerValue(detail.get("avgAdmittedScore"));
        item.put("avgAdmittedScore", avg);
        item.put("avgScoreGap", avg == null || estimatedScore <= 0 ? null : estimatedScore - avg);
        item.put("admissionRange", admissionRange(detail.get("admissionLow"), detail.get("admissionHigh")));
        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(detail, estimatedScore);
        item.put("quotaRisk", guard.get("quotaRisk"));
        item.put("canBeSafe", guard.get("canBeSafe"));
        if (guard.get("safeBlockReason") != null) item.put("safeBlockReason", guard.get("safeBlockReason"));
        item.put("opinion", buildOpinion(opinionSource, guard, fromSafeTier));
        mirrorOpinionForCurrentFrontend(item);
        return item;
    }

    private Map<String, Object> buildOpinion(Map<String, Object> source, Map<String, Object> guard, boolean fromSafeTier) {
        Map<String, Object> opinion = new LinkedHashMap<>();
        String judgement = AiReportSupport.normalizeJudgement(source.getOrDefault("judgement", source.get("aiJudgement")));
        if (AiReportSupport.JUDGEMENT_DATA_INSUFFICIENT_PENDING.equals(judgement)) {
            judgement = "steady";
        }
        boolean blockedSafe = fromSafeTier && Boolean.FALSE.equals(guard.get("canBeSafe"));
        if (blockedSafe) {
            judgement = "steady";
        }
        opinion.put("judgement", judgement);
        opinion.put("risk", blockedSafe ? "high" : source.getOrDefault("risk", "medium"));
        opinion.put("decision", blockedSafe ? "不宜作为保底，降级为稳妥待核验" : source.getOrDefault("decision", ""));
        opinion.put("reason", source.getOrDefault("reason", ""));
        opinion.put("pros", source.getOrDefault("pros", Collections.emptyList()));
        opinion.put("cons", appendIfPresent(source.getOrDefault("cons", Collections.emptyList()), guard.get("safeBlockReason")));
        opinion.put("tradeoffs", source.getOrDefault("tradeoffs", Collections.emptyList()));
        opinion.put("recommendedAction", blockedSafe ? "仅作为稳妥待核验选项，优先复查当年统考名额和拟录取名单" : source.getOrDefault("recommendedAction", ""));
        return opinion;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findTier(List<Map<String, Object>> tiers, String level) {
        for (Map<String, Object> tier : tiers) {
            if (level.equals(String.valueOf(tier.get("level")))) return tier;
        }
        return null;
    }

    private List<Object> appendIfPresent(Object existing, Object value) {
        List<Object> list = new ArrayList<>();
        if (existing instanceof List<?> values) list.addAll(values);
        if (value != null && !list.contains(value)) list.add(value);
        return list;
    }

    @SuppressWarnings("unchecked")
    private void mirrorOpinionForCurrentFrontend(Map<String, Object> item) {
        Map<String, Object> opinion = (Map<String, Object>) item.get("opinion");
        item.put("judgement", opinion.get("judgement"));
        item.put("risk", opinion.get("risk"));
        item.put("decision", opinion.get("decision"));
        item.put("reason", opinion.get("reason"));
        item.put("pros", opinion.get("pros"));
        item.put("cons", opinion.get("cons"));
        item.put("tradeoffs", opinion.get("tradeoffs"));
        item.put("recommendedAction", opinion.get("recommendedAction"));
    }

    private String stripMarkdown(String text) {
        if (text == null) return "";
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value)).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String admissionRange(Object low, Object high) {
        Integer lowValue = integerValue(low);
        Integer highValue = integerValue(high);
        if (lowValue == null && highValue == null) return null;
        if (lowValue == null) return String.valueOf(highValue);
        if (highValue == null) return String.valueOf(lowValue);
        return lowValue + "-" + highValue;
    }

    private String displayInt(Object value) {
        Integer number = integerValue(value);
        return number == null ? "-" : String.valueOf(number);
    }

    private String displaySignedInt(Object value) {
        Integer number = integerValue(value);
        if (number == null) return "-";
        return number > 0 ? "+" + number : String.valueOf(number);
    }
}
