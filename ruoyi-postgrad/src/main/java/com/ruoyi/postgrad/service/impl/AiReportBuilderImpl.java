package com.ruoyi.postgrad.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.ai.AiReportSupport;
import com.ruoyi.postgrad.domain.ai.AiRecommendationSafety;
import com.ruoyi.postgrad.domain.dto.CandidateProgramDTO;
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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.domain.ai.AiBookmark;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.tool.AiRecommendationTools;

@Service
public class AiReportBuilderImpl implements AiReportBuilder {
    private static final int PROMPT_POOL_ROW_LIMIT = 120;
    private static final int PER_LAYER_LIMIT = 15;
    private static final int MAX_CONVERSATION_ROUNDS = 6;
    private static final int MAX_MESSAGE_CHARS = 500;
    private static final Logger log = LoggerFactory.getLogger(AiReportBuilderImpl.class);

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/report-build.txt")
    private org.springframework.core.io.Resource reportBuildPromptResource;

    @Autowired
    private RecommendationMapper recommendationMapper;

    private String loadReportPrompt() {
        try {
            return new String(reportBuildPromptResource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load report build prompt", e);
        }
    }

    @Override
    public Map<String, Object> buildConversationReport(ChatModel chatModel, String conversationJson,
        String poolJson, int estimatedScore, Map<String, Object> preferenceProfile) {
        // 从对话历史中提取讨论过的学校ID，确保它们在候选池摘要中优先出现
        Set<Long> discussedIds = extractDiscussedProgramIds(conversationJson);
        String poolSummary = buildPoolSummary(poolJson, estimatedScore, discussedIds);
        String trimmedConv = trimConversationForReport(conversationJson);
        String prompt = basePrompt(poolSummary, preferenceProfile, estimatedScore) + "\n## 对话历史\n" + trimmedConv;
        log.info("[AI-TRACE] ======== REPORT-CONVERSATION ========");
        log.info("[AI-TRACE] discussedProgramIds={}", discussedIds);
        log.info("[AI-TRACE] REPORT PROMPT (first 800 chars):\n{}...",
            prompt.length() > 800 ? prompt.substring(0, 800) : prompt);
        log.debug("[AI-TRACE] REPORT PROMPT (full):\n{}", prompt);
        String aiRaw = chatModel.chat(prompt);
        log.info("[AI-TRACE] REPORT AI RAW OUTPUT:\n{}", aiRaw);
        Map<String, Object> parsed = parseReportJson(aiRaw, poolJson);
        // 如果 AI 输出了空 tiers 或仅有 summary 的空壳，回退到兜底推荐
        if (isEmptyTiers(parsed)) {
            log.warn("[AI-TRACE] AI returned empty tiers, falling back to rule-based report");
            parsed = ruleBasedFallback(poolJson);
        }
        Map<String, Object> report = hydrateReportPrograms(parsed, estimatedScore, poolJson);
        fillMissingDiscussedSchools(report, discussedIds, poolJson, estimatedScore);
        return report;
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
        Map<String, Object> parsed = parseReportJson(aiRaw, poolJson);
        if (isEmptyTiers(parsed)) {
            log.warn("[AI-TRACE] AI returned empty tiers, falling back to rule-based report");
            parsed = ruleBasedFallback(poolJson);
        }
        return hydrateReportPrograms(parsed, estimatedScore, poolJson);
    }

    @Override
    public Map<String, Object> buildFromBookmarks(String bookmarkJson, String poolJson,
            int estimatedScore) {
        List<AiBookmark> bookmarks;
        try {
            bookmarks = com.alibaba.fastjson2.JSON.parseArray(bookmarkJson, AiBookmark.class);
        } catch (Exception e) {
            bookmarks = java.util.Collections.emptyList();
        }
        if (bookmarks == null || bookmarks.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("tiers", List.of(
                tier("reach", "冲刺档", java.util.Collections.emptyList()),
                tier("steady", "稳妥档", java.util.Collections.emptyList()),
                tier("safe", "保底档", java.util.Collections.emptyList())));
            empty.put("summary", "");
            return empty;
        }

        // 收集 programId，批量水合
        List<Long> ids = bookmarks.stream().map(AiBookmark::getProgramId)
            .distinct().collect(java.util.stream.Collectors.toList());
        List<RowMap> rows = recommendationMapper.selectProgramsByIds(ids, estimatedScore);
        Map<Long, RowMap> detailMap = new LinkedHashMap<>();
        for (RowMap r : rows) {
            Object pidObj = r.get("programId");
            if (pidObj instanceof Number n) detailMap.put(n.longValue(), r);
        }

        // 构建水合学校列表
        List<Map<String, Object>> reach = new ArrayList<>();
        List<Map<String, Object>> steady = new ArrayList<>();
        List<Map<String, Object>> safe = new ArrayList<>();

        for (AiBookmark bm : bookmarks) {
            Map<String, Object> detail = detailMap.get(bm.getProgramId());
            if (detail == null) continue;
            boolean fromSafeTier = "safe".equals(bm.getJudgement());
            Map<String, Object> opinion = buildBookmarkOpinion(bm, detail, estimatedScore, fromSafeTier);
            Map<String, Object> hydrated = hydratedReportSchool(opinion, detail, estimatedScore, fromSafeTier);
            if (fromSafeTier && Boolean.FALSE.equals(hydrated.get("canBeSafe"))) {
                steady.add(hydrated);
            } else {
                String tier = bm.getJudgement();
                ("reach".equals(tier) ? reach : "steady".equals(tier) ? steady : safe).add(hydrated);
            }
        }

        // 三档排序：每档按 avgScoreGap 降序
        java.util.Comparator<Map<String, Object>> byGap = (a, b) -> {
            Integer ga = a.get("avgScoreGap") instanceof Number na ? na.intValue() : 0;
            Integer gb = b.get("avgScoreGap") instanceof Number nb ? nb.intValue() : 0;
            return Integer.compare(gb, ga);
        };
        reach.sort(byGap); steady.sort(byGap); safe.sort(byGap);

        // summary 模板
        int total = reach.size() + steady.size() + safe.size();
        String summary = String.format(
            "基于对话中的讨论，为你整理 %d 所学校：冲刺 %d 所%s、稳妥 %d 所、保底 %d 所%s。",
            total, reach.size(), reach.isEmpty() ? "（可继续挖掘）" : "",
            steady.size(), safe.size(),
            safe.isEmpty() ? "建议继续讨论保底候选。" : "");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", summary);
        report.put("tiers", List.of(
            tier("reach", "冲刺档", reach),
            tier("steady", "稳妥档", steady),
            tier("safe", "保底档", safe)));

        // P1: 脱敏所有 school 对象
        for (Map<String, Object> tierMap : List.of(
            tier("reach", "", reach), tier("steady", "", steady), tier("safe", "", safe))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tierMap.get("schools");
            for (Map<String, Object> school : schools) sanitizeReportItem(school);
        }

        return report;
    }

    private Map<String, Object> buildBookmarkOpinion(AiBookmark bm, Map<String, Object> detail,
            int estimatedScore, boolean fromSafeTier) {
        Map<String, Object> opinion = new LinkedHashMap<>();
        opinion.put("judgement", bm.getJudgement());
        opinion.put("reason", bm.getReason());
        opinion.put("pros", bm.getPros() != null ? bm.getPros() : java.util.Collections.emptyList());
        opinion.put("cons", bm.getCons() != null ? bm.getCons() : java.util.Collections.emptyList());
        opinion.put("tradeoffs", bm.getTradeoffs() != null ? bm.getTradeoffs() : java.util.Collections.emptyList());
        opinion.put("recommendedAction", bm.getRecommendedAction());
        // risk/decision 由后端推断
        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(detail, estimatedScore);
        boolean blocked = fromSafeTier && Boolean.FALSE.equals(guard.get("canBeSafe"));
        opinion.put("risk", blocked ? "high" : "safe".equals(bm.getJudgement()) ? "low" :
            "reach".equals(bm.getJudgement()) ? "high" : "medium");
        opinion.put("decision", blocked ? "不宜作为保底，降级为稳妥待核验" :
            "safe".equals(bm.getJudgement()) ? "适合作为保底候选" :
            "steady".equals(bm.getJudgement()) ? "适合作为稳妥候选" :
            "适合作为冲刺候选");
        return opinion;
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
        return loadReportPrompt().formatted(
            JSON.toJSONString(defaultedPreferenceProfile(preferenceProfile)), poolSummary);
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
                // 跳过系统提示——它包含候选池摘要，ID:xxx 会误匹配全部学校
                if ("system".equals(msg.get("role"))) continue;
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

    /**
     * 裁剪对话历史用于报告 prompt：移除 system 消息、只保留最近 N 轮、
     * 每条消息截断到 MAX_MESSAGE_CHARS 字符，减少 token 消耗。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String trimConversationForReport(String conversationJson) {
        if (conversationJson == null || conversationJson.isBlank()) return "";
        try {
            List<Map<String, Object>> messages = (List) JSON.parseArray(conversationJson, Map.class);
            // 移除 system 消息（候选池摘要已通过 poolSummary 发送，无需重复）
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> msg : messages) {
                if (!"system".equals(msg.get("role"))) {
                    filtered.add(msg);
                }
            }
            // 只保留最后 N 轮对话（每轮 = user + assistant）
            int maxMsgs = MAX_CONVERSATION_ROUNDS * 2;
            if (filtered.size() > maxMsgs) {
                filtered = filtered.subList(filtered.size() - maxMsgs, filtered.size());
            }
            // 截断每条消息
            for (Map<String, Object> msg : filtered) {
                String content = (String) msg.get("content");
                if (content != null && content.length() > MAX_MESSAGE_CHARS) {
                    msg.put("content", content.substring(0, MAX_MESSAGE_CHARS) + "...[已截断]");
                }
            }
            return JSON.toJSONString(filtered);
        } catch (Exception e) {
            log.warn("[AI-TRACE] Failed to trim conversation: {}", e.getMessage());
            return conversationJson; // fallback: return original
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String buildPoolSummary(String poolJson, int estimatedScore, Set<Long> priorityIds) {
        if (poolJson == null || poolJson.isBlank() || "[]".equals(poolJson.trim())) {
            return "（无候选学校数据）";
        }
        try {
            List<CandidateProgramDTO> pool = JSON.parseArray(poolJson, CandidateProgramDTO.class);

            // 分离已讨论学校和其他学校
            List<Map<String, Object>> discussed = new ArrayList<>();
            List<Map<String, Object>> rest = new ArrayList<>();
            for (CandidateProgramDTO row : pool) {
                Map<String, Object> m = row.toMap();
                if (row.getProgramId() != null && priorityIds.contains(row.getProgramId())) discussed.add(m);
                else rest.add(m);
            }

            // 剩余学校按 gap 分层
            List<Map<String, Object>> reachLayer = new ArrayList<>();
            List<Map<String, Object>> steadyLayer = new ArrayList<>();
            List<Map<String, Object>> safeLayer = new ArrayList<>();
            for (Map<String, Object> row : rest) {
                int gap = integerValue(row.get("gap"));
                if (gap <= 0) reachLayer.add(row);
                else if (gap <= 14) steadyLayer.add(row);
                else safeLayer.add(row);
            }

            // 每层按综合得分排序取前 15 行
            reachLayer = selectTop(reachLayer, "reach", PER_LAYER_LIMIT);
            steadyLayer = selectTop(steadyLayer, "steady", PER_LAYER_LIMIT);
            safeLayer = selectTop(safeLayer, "safe", PER_LAYER_LIMIT);

            StringBuilder sb = new StringBuilder();
            int totalBefore = pool.size();
            int totalAfter = discussed.size() + reachLayer.size() + steadyLayer.size() + safeLayer.size();
            sb.append("候选池共 ").append(totalBefore).append(" 所学校，精选 ").append(totalAfter)
                .append(" 所供报告参考（按冲/稳/保分层）：\n");

            int index = 1;
            if (!discussed.isEmpty()) {
                sb.append("\n== 对话已讨论（必须纳入报告）==\n");
                for (Map<String, Object> row : discussed) {
                    appendPoolRow(sb, index++, row, estimatedScore, true);
                }
            }
            if (!reachLayer.isEmpty()) {
                sb.append("\n== 冲刺候选层（gap ≤ 0，分数接近或略低于均分）==\n");
                for (Map<String, Object> row : reachLayer) {
                    appendPoolRow(sb, index++, row, estimatedScore, false);
                }
            }
            if (!steadyLayer.isEmpty()) {
                sb.append("\n== 稳妥候选层（gap 1~14，分数有余量）==\n");
                for (Map<String, Object> row : steadyLayer) {
                    appendPoolRow(sb, index++, row, estimatedScore, false);
                }
            }
            if (!safeLayer.isEmpty()) {
                sb.append("\n== 保底候选层（gap ≥ 15，分数安全度高）==\n");
                for (Map<String, Object> row : safeLayer) {
                    appendPoolRow(sb, index++, row, estimatedScore, false);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "（候选学校数据解析失败）";
        }
    }

    /** 每层按综合得分排序，取前 limit 行 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> selectTop(List<Map<String, Object>> layer, String tier, int limit) {
        if (layer.size() <= limit) return layer;
        layer.sort((a, b) -> Integer.compare(compositeScore(b, tier), compositeScore(a, tier)));
        return new ArrayList<>(layer.subList(0, limit));
    }

    /**
     * 综合得分：综合数据完整度、招生规模、学校层次和 gap 适配度。
     * 不同分档对 gap 的"理想值"不同：冲刺档 0 最佳，稳妥档 ~10，保底档 ~20。
     */
    private int compositeScore(Map<String, Object> row, String tier) {
        int score = 0;
        // 数据完整度：A > B > C
        String comp = String.valueOf(row.getOrDefault("dataCompleteness", "C"));
        score += "A".equals(comp) ? 30 : "B".equals(comp) ? 20 : 10;
        // 招生风险：normal > medium > high
        String risk = String.valueOf(row.getOrDefault("quotaRisk", "unknown"));
        score += "normal".equals(risk) ? 30 : "medium".equals(risk) ? 20 : 10;
        // 学校层次：985 > 211/双一流 > 其他
        String schoolTier = String.valueOf(row.getOrDefault("schoolTier", "OTHER"));
        score += "985".equals(schoolTier) ? 25 : ("211".equals(schoolTier) || "DOUBLE_FIRST".equals(schoolTier)) ? 18 : 10;
        // gap 接近该档"理想值"的程度
        int gap = integerValue(row.get("gap"));
        int idealGap = "reach".equals(tier) ? 0 : "steady".equals(tier) ? 10 : 20;
        score += Math.max(0, 15 - Math.abs(gap - idealGap));
        return score;
    }

    /** 输出单行候选学校摘要，包含 复录比 供 AI 判断复试竞争程度 */
    private void appendPoolRow(StringBuilder sb, int index, Map<String, Object> row,
            int estimatedScore, boolean isDiscussed) {
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
        // 复录比 = 复试人数 / 录取人数，反映复试竞争激烈程度
        appendRetestRatio(sb, row);
        sb.append(" | 完整度:").append(row.getOrDefault("dataCompleteness", ""));
        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(row, estimatedScore);
        sb.append(" | quotaRisk:").append(guard.get("quotaRisk"));
        sb.append(" | canBeSafe:").append(guard.get("canBeSafe"));
        Object reason = guard.get("safeBlockReason");
        if (reason != null) sb.append(" | 保底限制:").append(reason);
        sb.append("\n");
    }

    /** 在摘要行中追加复录比信息 */
    private void appendRetestRatio(StringBuilder sb, Map<String, Object> row) {
        Integer retest = integerValue(row.get("retestCount"));
        Integer admitted = integerValue(row.get("admittedCount"));
        if (retest != null && admitted != null && retest > 0 && admitted > 0) {
            double ratio = (double) retest / admitted;
            sb.append(" | 复录比:").append(String.format("%.1f:1", ratio));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> parseReportJson(String aiResponse, String poolJson) {
        try {
            String cleaned = extractJson(aiResponse);
            return (Map<String, Object>) JSON.parseObject(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("[AI-TRACE] Failed to parse AI JSON, using fallback. Error: {}", e.getMessage());
            return ruleBasedFallback(poolJson);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> ruleBasedFallback(String poolJson) {
        List<CandidateProgramDTO> pool;
        try {
            pool = JSON.parseArray(poolJson == null ? "[]" : poolJson, CandidateProgramDTO.class);
        } catch (Exception e) {
            pool = Collections.emptyList();
        }
        if (pool == null) pool = Collections.emptyList();

        List<Map<String, Object>> reach = new ArrayList<>();
        List<Map<String, Object>> steady = new ArrayList<>();
        List<Map<String, Object>> safe = new ArrayList<>();
        for (CandidateProgramDTO row : pool) {
            if (reach.size() >= 5 && steady.size() >= 5 && safe.size() >= 5) break;
            int gap = row.getGap();
            String level = gap <= 0 ? "reach" : gap <= 14 ? "steady" : "safe";
            boolean blockedSafe = "safe".equals(level) && !row.isCanBeSafe();
            if (blockedSafe) level = "steady";
            List<Map<String, Object>> target = "reach".equals(level) ? reach : "steady".equals(level) ? steady : safe;
            if (target.size() >= 5) continue;

            Map<String, Object> school = new LinkedHashMap<>();
            school.put("programId", row.getProgramId());
            school.put("judgement", "reach".equals(level) ? "small_reach" : level);
            school.put("risk", "reach".equals(level) ? "high" : "safe".equals(level) ? "low" : "medium");
            school.put("decision", "reach".equals(level) ? "适合作为冲刺候选" : "safe".equals(level) ? "适合作为保底候选" : "适合作为稳妥候选");
            school.put("reason", "AI 输出解析异常，基于候选池数据自动编排的兜底推荐。建议重新生成报告获取 AI 分析。");
            school.put("pros", Collections.emptyList());
            school.put("cons", Collections.emptyList());
            school.put("tradeoffs", Collections.emptyList());
            school.put("recommendedAction", "建议核验院校官网或重新发起对话后再次生成报告");
            target.add(school);
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

        // 批量查询所有学校的完整数据（消除 N+1）
        Map<Long, Map<String, Object>> detailMap = batchLoadDetails(tiers, estimatedScore);

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
                Map<String, Object> detail = detailMap.get(programId);
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

        // P1: 脱敏
        for (Map<String, Object> tier : tiers) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
            if (schools != null) for (Map<String, Object> s : schools) sanitizeReportItem(s);
        }

        return result;
    }

    /** 收集 tiers 中所有学校的 programId，一次批量查询数据库 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<Long, Map<String, Object>> batchLoadDetails(List<Map<String, Object>> tiers, int estimatedScore) {
        Set<Long> allIds = new LinkedHashSet<>();
        for (Map<String, Object> tier : tiers) {
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
            if (schools == null) continue;
            for (Map<String, Object> school : schools) {
                Long pid = longValue(school.get("programId"));
                if (pid != null) allIds.add(pid);
            }
        }
        if (allIds.isEmpty()) return Collections.emptyMap();
        List<RowMap> rows = recommendationMapper.selectProgramsByIds(new ArrayList<>(allIds), estimatedScore);
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (RowMap row : rows) {
            Object pidObj = row.get("programId");
            if (pidObj instanceof Number n) {
                map.put(n.longValue(), row);
            }
        }
        return map;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<Long, Map<String, Object>> parsePoolMap(String poolJson) {
        Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
        if (poolJson == null || poolJson.isBlank() || "[]".equals(poolJson.trim())) return poolMap;
        List<CandidateProgramDTO> list = JSON.parseArray(poolJson, CandidateProgramDTO.class);
        if (list != null) {
            for (CandidateProgramDTO dto : list) {
                if (dto.getProgramId() != null) poolMap.put(dto.getProgramId(), dto.toMap());
            }
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
        // 将 schoolTier 从 DB 枚举值映射为中文标签
        if (item.containsKey("schoolTier")) {
            item.put("schoolTier", AiRecommendationTools.tierDisplayLabel(item.get("schoolTier")));
        }

        Integer avg = integerValue(detail.get("avgAdmittedScore"));
        item.put("avgAdmittedScore", avg);
        item.put("avgScoreGap", avg == null || estimatedScore <= 0 ? null : estimatedScore - avg);
        item.put("admissionRange", admissionRange(detail.get("admissionLow"), detail.get("admissionHigh")));
        item.put("dataCompleteness", AiRecommendationSafety.computedCompleteness(detail));

        Map<String, Object> guard = AiRecommendationSafety.safeEligibility(detail, estimatedScore);
        item.put("quotaRisk", guard.get("quotaRisk"));
        item.put("canBeSafe", guard.get("canBeSafe"));
        if (guard.get("safeBlockReason") != null) item.put("safeBlockReason", guard.get("safeBlockReason"));
        item.put("opinion", buildOpinion(opinionSource, guard, fromSafeTier));
        mirrorOpinionForCurrentFrontend(item);
        return item;
    }

    /** 将内部字段转为 tags 后删除，避免 raw 字段泄露到前端 */
    private void sanitizeReportItem(Map<String, Object> item) {
        List<String> tags = new ArrayList<>();

        // canBeSafe → 是否满足严格保底
        if (Boolean.FALSE.equals(item.get("canBeSafe"))) {
            tags.add("不满足严格保底条件");
        }

        // quotaRisk → 名额风险
        String qr = String.valueOf(item.getOrDefault("quotaRisk", ""));
        if ("very_high".equals(qr)) tags.add("名额风险极高");
        else if ("high".equals(qr)) tags.add("名额风险较高");
        else if ("medium".equals(qr)) tags.add("名额风险中等");

        // dataCompleteness → 数据完整度
        String dc = String.valueOf(item.getOrDefault("dataCompleteness", ""));
        if (!dc.isBlank() && !"null".equalsIgnoreCase(dc)) {
            tags.add("数据完整度:" + dc);
        }

        // safeBlockReason → 直接作为 tag
        String sbr = String.valueOf(item.getOrDefault("safeBlockReason", ""));
        if (!sbr.isBlank() && !"null".equalsIgnoreCase(sbr)) {
            tags.add(sbr);
        }

        item.put("tags", tags);

        // 删除内部裁决字段（展示用字段保留）
        // - canBeSafe / quotaRisk / safeBlockReason: 内部推断中间值,已经转成 tags/pros/cons
        // ★ dataYear 保留：前端报告页直接展示，不再依赖 fallback
        // - 不删 dataCompleteness: 前端 schoolCompleteness() 直接读
        // - 不删 scoreLine / admissionLow / admissionHigh / planCount / unifiedExamQuota /
        //   admittedCount / retestCount / sourceUrl / sourceOwner: 都是 UI 展示字段
        for (String key : List.of("canBeSafe", "quotaRisk", "safeBlockReason")) {
            item.remove(key);
        }
        // 同时将 dataYear 加入 tags（前端卡片可同时展示标签和年份字段）
        Object dy = item.get("dataYear");
        if (dy != null && !"null".equals(String.valueOf(dy)) && !"0".equals(String.valueOf(dy))) {
            tags.add("数据年份:" + dy);
        }
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

    /** 检查报告是否没有实质性内容（空 tiers 或无学校） */
    @SuppressWarnings("unchecked")
    private boolean isEmptyTiers(Map<String, Object> report) {
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) report.get("tiers");
        if (tiers == null || tiers.isEmpty()) return true;
        for (Map<String, Object> tier : tiers) {
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
            if (schools != null && !schools.isEmpty()) return false;
        }
        return true;
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

    /**
     * 从 AI 原始回复中提取 JSON 对象。
     * 处理常见情况：前置确认语/说明、markdown 代码块、尾部补充说明。
     */
    private String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();

        // 1. 优先处理 markdown 代码块：```json ... ``` 或 ``` ... ```
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("```(?:json)?\\s*([\\s\\S]*?)```", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(cleaned);
        if (m.find()) {
            cleaned = m.group(1).trim();
        }

        // 2. 找到第一个 { 和最后一个 }，提取 JSON 对象
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1).trim();
        }

        return cleaned;
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

    /**
     * Backstop: after the report is built, check whether any schools that were explicitly
     * discussed in the conversation are missing. If so, add them to the appropriate tier
     * so the user never sees "AI recommended it in chat but the report dropped it".
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fillMissingDiscussedSchools(Map<String, Object> report, Set<Long> discussedIds,
        String poolJson, int estimatedScore) {
        if (discussedIds.isEmpty() || poolJson == null || poolJson.isBlank()) return;

        // Collect all programIds already in the report
        Set<Long> presentIds = new LinkedHashSet<>();
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) report.get("tiers");
        if (tiers == null) return;
        for (Map<String, Object> tier : tiers) {
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
            if (schools == null) continue;
            for (Map<String, Object> school : schools) {
                Long pid = longValue(school.get("programId"));
                if (pid != null) presentIds.add(pid);
            }
        }

        // Find missing discussed schools in the pool
        Map<Long, Map<String, Object>> poolMap = parsePoolMap(poolJson);
        List<Long> missing = new ArrayList<>();
        for (Long id : discussedIds) {
            if (!presentIds.contains(id) && poolMap.containsKey(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) return;

        log.info("[AI-TRACE] fillMissingDiscussedSchools: adding {} missing schools: {}", missing.size(), missing);

        // 批量查询缺失学校的完整数据
        Map<Long, Map<String, Object>> missingDetailMap;
        {
            List<RowMap> rows = recommendationMapper.selectProgramsByIds(missing, estimatedScore);
            missingDetailMap = new LinkedHashMap<>();
            for (RowMap row : rows) {
                Object pidObj = row.get("programId");
                if (pidObj instanceof Number n) missingDetailMap.put(n.longValue(), row);
            }
        }

        // For each missing school, create a basic opinion entry and add to the right tier
        for (Long pid : missing) {
            Map<String, Object> detail = missingDetailMap.get(pid);
            if (detail == null) continue;
            Map<String, Object> poolRow = poolMap.get(pid);
            if (poolRow == null) continue;

            Map<String, Object> hydrated = hydratedReportSchool(buildFallbackOpinion(poolRow, estimatedScore),
                detail, estimatedScore, false);

            // Determine tier from gap + canBeSafe
            Object gapObj = hydrated.get("avgScoreGap");
            int gap = gapObj instanceof Number n ? n.intValue() : 0;
            boolean blockedSafe = Boolean.FALSE.equals(hydrated.get("canBeSafe"));
            String targetLevel;
            if (blockedSafe) {
                targetLevel = "steady";
            } else if (gap >= 15) {
                targetLevel = "safe";
            } else if (gap >= 5) {
                targetLevel = "steady";
            } else {
                targetLevel = "reach";
            }

            Map<String, Object> targetTier = findTier(tiers, targetLevel);
            if (targetTier != null) {
                List<Map<String, Object>> schools = (List<Map<String, Object>>) targetTier.get("schools");
                if (schools == null) {
                    schools = new ArrayList<>();
                    targetTier.put("schools", schools);
                }
                schools.add(hydrated);
                log.info("[AI-TRACE] fillMissingDiscussedSchools: added programId={} to tier={}", pid, targetLevel);
            }
        }
    }

    /** Build a minimal opinion object for a fallback-added school so the frontend can render it. */
    private Map<String, Object> buildFallbackOpinion(Map<String, Object> poolRow, int estimatedScore) {
        Map<String, Object> opinion = new LinkedHashMap<>();
        Object gapObj = poolRow.get("gap");
        int gap = gapObj instanceof Number n ? n.intValue() : 0;
        if (gap >= 15) {
            opinion.put("judgement", "safe");
            opinion.put("risk", "low");
            opinion.put("decision", "适合作为保底候选");
        } else if (gap >= 5) {
            opinion.put("judgement", "steady");
            opinion.put("risk", "medium");
            opinion.put("decision", "适合作为稳妥候选");
        } else {
            opinion.put("judgement", "small_reach");
            opinion.put("risk", "high");
            opinion.put("decision", "适合作为冲刺候选");
        }
        opinion.put("reason", "对话中已讨论，系统自动补入报告");
        opinion.put("pros", Collections.emptyList());
        opinion.put("cons", Collections.emptyList());
        opinion.put("tradeoffs", Collections.emptyList());
        opinion.put("recommendedAction", "建议核验院校官网后再加入最终备选");
        return opinion;
    }

    private String displaySignedInt(Object value) {
        Integer number = integerValue(value);
        if (number == null) return "-";
        return number > 0 ? "+" + number : String.valueOf(number);
    }

}

