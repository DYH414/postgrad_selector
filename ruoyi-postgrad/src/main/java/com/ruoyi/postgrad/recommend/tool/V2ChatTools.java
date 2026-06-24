package com.ruoyi.postgrad.recommend.tool;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.impl.DraftServiceImpl;

import dev.langchain4j.agent.tool.Tool;

/**
 * AI 对话工具集 —— 供对话 AI 在分析学校时调用。
 * <p>所有工具只做数据查询，不修改草稿状态。</p>
 */
@Component
public class V2ChatTools {

    private static final Logger log = LoggerFactory.getLogger(V2ChatTools.class);

    @Tool("查询指定 programId 对应学校的完整详细数据，包括学校层次、录取均分、招生人数、复试线等")
    public String getProgramDetail(long programId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return "工具上下文未初始化，无法查询。";

        StringBuilder sb = new StringBuilder();

        // 先查 Workspace，回退到候选池快照
        List<CandidateCardVO> pool = loadWorkspaceCandidates(ctx.userId());
        if (pool.isEmpty()) pool = loadPoolSnapshot(ctx.userId());
        for (CandidateCardVO c : pool) {
            if (c.getFact() != null && programId == c.getFact().getProgramId()) {
                sb.append(formatFactCard(c.getFact()));
                break;
            }
        }

        // 池中无 → 查 DB
        if (sb.isEmpty()) {
            RowMap row = ctx.mapper().selectProgramForRecommendation(programId);
            if (row == null) return "未找到 programId=" + programId + " 的学校数据。";
            sb.append(formatRowMap(row));
        }

        // 附加历年趋势（近 3 年复试线+录取均分）
        List<RowMap> trends = ctx.mapper().selectTrends(programId);
        if (trends != null && !trends.isEmpty()) {
            sb.append("\n\n【近 3 年趋势】\n");
            for (RowMap t : trends) {
                sb.append(String.format("%s年 | 复试线:%s | 录取均分:%s | 最低:%s | 最高:%s | 录取:%s人\n",
                    t.get("year"),
                    t.get("scoreLine") != null ? t.get("scoreLine") : "-",
                    t.get("avgAdmittedScore") != null ? t.get("avgAdmittedScore") : "-",
                    t.get("admissionLow") != null ? t.get("admissionLow") : "-",
                    t.get("admissionHigh") != null ? t.get("admissionHigh") : "-",
                    t.get("admittedCount") != null ? t.get("admittedCount") : "-"));
            }
        }

        return sb.toString();
    }

    @Tool("在候选工作集中搜索学校，支持按关键词、档位(tier)、地区(region)过滤。" +
          "tier 可选值: reach/steady/safe，region 为省份或城市名。" +
          "工作集无结果时自动回退到全国数据库搜索（适用于探索画像地区以外的学校）。")
    public String searchPrograms(String keyword, String tier, String region) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return "[]";

        // 优先搜索 Workspace（包含更广泛的候选），回退到旧池快照
        List<CandidateCardVO> candidates = loadWorkspaceCandidates(ctx.userId());
        if (candidates.isEmpty()) {
            candidates = loadPoolSnapshot(ctx.userId());
        }

        List<java.util.Map<String, Object>> results = new ArrayList<>();
        String kw = keyword != null ? keyword.toLowerCase() : "";
        String t = tier != null && !tier.isBlank() ? tier : null;
        String r = region != null && !region.isBlank() ? region : null;

        for (CandidateCardVO c : candidates) {
            var f = c.getFact();
            if (f == null) continue;
            if (!matchSearch(f, kw, t, r)) continue;
            results.add(toSearchItem(f));
            if (results.size() >= 10) break;
        }

        // 工作集无结果且有地区筛选 → 回退到全国 DB 搜索
        if (results.isEmpty() && r != null) {
            log.info("[ChatTool] searchPrograms workspace empty for region='{}', falling back to DB", r);
            results = fallbackDbSearch(ctx, kw, t, r);
        }

        return JSON.toJSONString(results);
    }

    /** 工作集候选匹配检查 */
    private boolean matchSearch(SchoolFact f, String kw, String tier, String region) {
        if (!kw.isEmpty()) {
            String school = f.getSchoolName() != null ? f.getSchoolName().toLowerCase() : "";
            String program = f.getProgramName() != null ? f.getProgramName().toLowerCase() : "";
            if (!school.contains(kw) && !program.contains(kw)) return false;
        }
        if (tier != null && !tier.equals(f.inferTier())) return false;
        if (region != null) {
            String prov = f.getProvince() != null ? f.getProvince() : "";
            String city = f.getCity() != null ? f.getCity() : "";
            if (!prov.contains(region) && !city.contains(region)) return false;
        }
        return true;
    }

    /** 搜索结果项（包含足够分析所需的所有字段） */
    private java.util.Map<String, Object> toSearchItem(SchoolFact f) {
        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("programId", f.getProgramId());
        item.put("schoolName", f.getSchoolName());
        item.put("programName", f.getProgramName());
        String inferredTier = f.inferTier();
        item.put("tier", inferredTier != null ? inferredTier : "far_reach");
        item.put("city", f.getCity());
        item.put("province", f.getProvince());
        item.put("gap", f.getScoreGap());
        int quota = f.getUnifiedExamQuota() != null ? f.getUnifiedExamQuota()
            : (f.getPlanCount() != null ? f.getPlanCount() : 0);
        item.put("quota", quota);
        item.put("schoolTier", f.getSchoolTier());
        item.put("avgAdmittedScore", f.getAvgAdmittedScore());
        item.put("scoreLine", f.getScoreLine());
        item.put("riskLevel", quotaRisk(quota));
        item.put("quotaLabel", quotaLabel(quota));
        item.put("canBeSafe", Boolean.TRUE.equals(f.getCanBeSafe()));
        return item;
    }

    private String quotaLabel(int quota) {
        if (quota <= 0) return "名额未知";
        if (quota <= 3) return "名额极少";
        if (quota < 10) return "名额偏少";
        if (quota < 20) return "名额正常";
        return "名额充裕";
    }

    private String quotaRisk(int quota) {
        if (quota <= 0) return "unknown";
        if (quota <= 3) return "very_high";
        if (quota < 10) return "high";
        if (quota < 20) return "medium";
        return "normal";
    }

    /** DB 回退搜索：不限制地区，从全国数据库搜索指定地区的学校 */
    private List<java.util.Map<String, Object>> fallbackDbSearch(
            V2ChatToolContext.Context ctx, String kw, String tier, String region) {
        List<java.util.Map<String, Object>> results = new ArrayList<>();

        try {
            // 从草稿中获取用户预估分数
            int estimatedScore = getEstimatedScore(ctx);
            RecommendationMapper mapper = ctx.mapper();

            // 两套考试组合，不限制省份
            List<String> examCombos = List.of("101,204,302,408", "101,201,301,408");
            java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();

            for (String subjectCodes : examCombos) {
                List<RowMap> rows = mapper.selectCandidates(
                    subjectCodes, null, null, estimatedScore, 40, "full_time");
                if (rows == null) continue;
                for (RowMap row : rows) {
                    Object pid = row.get("programId");
                    if (!(pid instanceof Number n) || !seen.add(n.longValue())) continue;

                    SchoolFact f = SchoolFact.fromRow(row);
                    // 计算 gap
                    Integer avg = f.getAvgAdmittedScore();
                    int gap = avg != null ? estimatedScore - avg : 0;
                    f.setScoreGap(gap);
                    if (gap < -30) continue; // 差距过大

                    int quota = f.getUnifiedExamQuota() != null ? f.getUnifiedExamQuota()
                        : (f.getPlanCount() != null ? f.getPlanCount() : 0);
                    f.setCanBeSafe(SchoolFact.canBeSafe(quota, f.getDataCompleteness(),
                        f.getAdmissionLow(), f.getAdmissionHigh()));

                    if (!matchSearch(f, kw, tier, region)) continue;

                    results.add(toSearchItem(f));
                    if (results.size() >= 10) break;
                }
                if (results.size() >= 10) break;
            }

            log.info("[ChatTool] DB fallback found {} results for region='{}'", results.size(), region);
        } catch (Exception e) {
            log.warn("[ChatTool] DB fallback search failed: {}", e.getMessage());
        }

        return results;
    }

    /** 从 Redis 草稿中读取用户预估分数，默认 300 */
    private int getEstimatedScore(V2ChatToolContext.Context ctx) {
        try {
            String json = ctx.redis().opsForValue()
                .get(DraftServiceImpl.DRAFT_KEY_PREFIX + ctx.userId());
            if (json != null && !json.isBlank()) {
                DraftVO draft = JSON.parseObject(json, DraftVO.class);
                if (draft.getProfileBasis() != null
                    && draft.getProfileBasis().getEstimatedScore() != null) {
                    return draft.getProfileBasis().getEstimatedScore();
                }
            }
        } catch (Exception e) {
            log.warn("[ChatTool] Failed to read estimatedScore from draft: {}", e.getMessage());
        }
        return 300; // fallback default
    }

    @Tool("获取指定档位的候选填充列表。在移除学校后如需手动选择替代候选时使用。" +
          "tier 可选值: reach/steady/safe, count 默认为 3。")
    public String getRefillCandidates(String tier, int count) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return "[]";

        List<CandidateCardVO> candidates = loadWorkspaceCandidates(ctx.userId());
        if (candidates.isEmpty()) return "[]";

        String t = tier != null && !tier.isBlank() ? tier : "steady";
        int limit = count > 0 && count <= 5 ? count : 3;

        List<java.util.Map<String, Object>> results = new ArrayList<>();
        for (CandidateCardVO c : candidates) {
            var f = c.getFact();
            if (f == null || !t.equals(f.inferTier())) continue;
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("programId", f.getProgramId());
            item.put("schoolName", f.getSchoolName());
            item.put("programName", f.getProgramName());
            item.put("gap", f.getScoreGap());
            item.put("riskLevel", f.getQuotaRisk());
            results.add(item);
            if (results.size() >= limit) break;
        }
        return JSON.toJSONString(results);
    }

    @Tool("并排对比两所候选学校的核心指标，包括学校层次、录取均分、分数差距、招生名额、城市")
    public String comparePrograms(long programId1, long programId2) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return "工具上下文未初始化，无法对比。";

        List<CandidateCardVO> pool = loadPoolSnapshot(ctx.userId());
        CandidateCardVO c1 = findByProgramId(pool, programId1);
        CandidateCardVO c2 = findByProgramId(pool, programId2);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 学校对比 ===\n\n");

        sb.append(String.format("%-20s %-30s %-30s\n", "指标", schoolLabel(c1, programId1), schoolLabel(c2, programId2)));
        sb.append(String.format("%-20s %-30s %-30s\n", "学校层次", val(c1, f -> f.getSchoolTier()), val(c2, f -> f.getSchoolTier())));
        sb.append(String.format("%-20s %-30s %-30s\n", "录取均分", val(c1, f -> f.getAvgAdmittedScore()), val(c2, f -> f.getAvgAdmittedScore())));
        sb.append(String.format("%-20s %-30s %-30s\n", "分数差距", val(c1, f -> f.getScoreGap()), val(c2, f -> f.getScoreGap())));
        sb.append(String.format("%-20s %-30s %-30s\n", "招生名额", quotaVal(c1), quotaVal(c2)));
        sb.append(String.format("%-20s %-30s %-30s\n", "城市", val(c1, f -> f.getCity()), val(c2, f -> f.getCity())));
        sb.append(String.format("%-20s %-30s %-30s\n", "数据完整度", val(c1, f -> f.getDataCompleteness()), val(c2, f -> f.getDataCompleteness())));

        return sb.toString();
    }

    @Tool("查看当前报告草稿的状态——已选了几所、分别在哪些档位、每所学校的关键信息。")
    public String getDraftContext() {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return "草稿上下文未初始化，无法获取。";

        String json = ctx.redis().opsForValue().get(DraftServiceImpl.DRAFT_KEY_PREFIX + ctx.userId());
        if (json == null || json.isBlank()) return "尚未生成草稿。";

        try {
            DraftVO draft = JSON.parseObject(json, DraftVO.class);
            return formatDraftSummary(draft);
        } catch (Exception e) {
            return "草稿数据解析失败。";
        }
    }

    // ── helpers ──

    private static final String WORKSPACE_KEY_PREFIX = "ai:v2:workspace:";

    private List<CandidateCardVO> loadWorkspaceCandidates(Long userId) {
        String json = V2ChatToolContext.current().redis()
            .opsForValue().get(WORKSPACE_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            CandidateWorkspaceVO ws = JSON.parseObject(json, CandidateWorkspaceVO.class);
            List<CandidateCardVO> all = new ArrayList<>();
            if (ws.getTiers() != null) {
                for (var tier : ws.getTiers()) {
                    if (tier.getCandidates() != null) all.addAll(tier.getCandidates());
                }
            }
            return all;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private List<CandidateCardVO> loadPoolSnapshot(Long userId) {
        String json = V2ChatToolContext.current().redis()
            .opsForValue().get(DraftServiceImpl.DRAFT_POOL_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return JSON.parseArray(json, CandidateCardVO.class);
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private CandidateCardVO findByProgramId(List<CandidateCardVO> pool, long pid) {
        return pool.stream()
            .filter(c -> c.getFact() != null && c.getFact().getProgramId() != null
                && c.getFact().getProgramId() == pid)
            .findFirst().orElse(null);
    }

    private String formatFactCard(com.ruoyi.postgrad.recommend.domain.SchoolFact f) {
        if (f == null) return "无数据";
        return String.format(
            "%s | %s | 层次:%s | 城市:%s | 均分:%s | 差距:%s | 招生:%s | %s | 可保底:%s | 数据:%s",
            f.getSchoolName(), f.getProgramName(), f.getSchoolTier(), f.getCity(),
            f.getAvgAdmittedScore() != null ? String.valueOf(f.getAvgAdmittedScore()) : "-",
            f.getGapLabel() != null ? f.getGapLabel() : "-",
            f.getUnifiedExamQuota() != null ? String.valueOf(f.getUnifiedExamQuota())
                : (f.getPlanCount() != null ? String.valueOf(f.getPlanCount()) : "-"),
            f.getQuotaLabel() != null ? f.getQuotaLabel() : "",
            Boolean.TRUE.equals(f.getCanBeSafe()) ? "是" : "否",
            f.getDataCompleteness() != null ? f.getDataCompleteness() : "?"
        );
    }

    private String formatRowMap(RowMap row) {
        StringBuilder sb = new StringBuilder();
        sb.append(row.get("schoolName")).append(" | ");
        sb.append(row.get("programName")).append(" | ");
        sb.append("层次:").append(row.get("schoolTier")).append(" | ");
        sb.append("城市:").append(row.get("city")).append(" | ");
        sb.append("均分:").append(row.get("avgAdmittedScore") != null ? row.get("avgAdmittedScore") : "-").append(" | ");
        sb.append("招生:").append(row.get("unifiedExamQuota") != null ? row.get("unifiedExamQuota")
            : (row.get("planCount") != null ? row.get("planCount") : "-")).append(" | ");
        sb.append("数据:").append(row.get("dataCompleteness") != null ? row.get("dataCompleteness") : "?");
        return sb.toString();
    }

    private String formatDraftSummary(DraftVO draft) {
        if (draft == null || draft.getTiers() == null) return "尚未生成草稿。";

        StringBuilder sb = new StringBuilder("当前草稿状态：\n");
        int total = 0;
        for (TierCandidates t : draft.getTiers()) {
            int count = t.getCandidates() != null ? t.getCandidates().size() : 0;
            total += count;
            sb.append(t.getLabel()).append(" ").append(count).append("/").append(t.getTargetCount());
            if (t.isInsufficient()) sb.append(" (不足)");
            sb.append("：");
            if (t.getCandidates() != null && !t.getCandidates().isEmpty()) {
                List<String> names = new ArrayList<>();
                for (var c : t.getCandidates()) {
                    var f = c.getFact();
                    names.add(f.getSchoolName() + "-" + f.getProgramName() + "【操作ID:" + f.getProgramId() + "】");
                }
                sb.append(String.join("、", names));
            } else {
                sb.append("暂无");
            }
            sb.append("\n");
        }
        sb.append("共 ").append(total).append(" 所学校。");
        return sb.toString();
    }

    private String schoolLabel(CandidateCardVO c, long fallbackId) {
        if (c == null || c.getFact() == null) return "候选学校";
        return c.getFact().getSchoolName();
    }

    @FunctionalInterface
    private interface FactGetter {
        Object get(com.ruoyi.postgrad.recommend.domain.SchoolFact f);
    }

    private String val(CandidateCardVO c, FactGetter getter) {
        if (c == null || c.getFact() == null) return "-";
        Object v = getter.get(c.getFact());
        return v != null ? v.toString() : "-";
    }

    private String quotaVal(CandidateCardVO c) {
        if (c == null || c.getFact() == null) return "-";
        Integer q = c.getFact().getUnifiedExamQuota();
        if (q == null) q = c.getFact().getPlanCount();
        return q != null ? q.toString() : "-";
    }
}
