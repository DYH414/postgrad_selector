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
          "tier 可选值: reach/steady/safe，region 为省份或城市名。搜索结果来自工作集（CandidateWorkspace），包含不在当前草稿中的候选。")
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

            if (!kw.isEmpty()) {
                String school = f.getSchoolName() != null ? f.getSchoolName().toLowerCase() : "";
                String program = f.getProgramName() != null ? f.getProgramName().toLowerCase() : "";
                if (!school.contains(kw) && !program.contains(kw)) continue;
            }
            if (t != null && !t.equals(f.inferTier())) continue;
            if (r != null) {
                String prov = f.getProvince() != null ? f.getProvince() : "";
                String city = f.getCity() != null ? f.getCity() : "";
                if (!prov.contains(r) && !city.contains(r)) continue;
            }

            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("programId", f.getProgramId());
            item.put("schoolName", f.getSchoolName());
            item.put("programName", f.getProgramName());
            item.put("tier", f.inferTier());
            item.put("city", f.getCity());
            item.put("gap", f.getScoreGap());
            item.put("quota", f.getUnifiedExamQuota() != null ? f.getUnifiedExamQuota() : f.getPlanCount());
            results.add(item);
            if (results.size() >= 10) break;
        }

        return JSON.toJSONString(results);
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
