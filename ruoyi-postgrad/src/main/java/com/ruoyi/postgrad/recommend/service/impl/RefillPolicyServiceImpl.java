package com.ruoyi.postgrad.recommend.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.ExcludedCandidateVO;
import com.ruoyi.postgrad.recommend.domain.RefillCandidateVO;
import com.ruoyi.postgrad.recommend.domain.RefillResultVO;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.IRefillPolicyService;

/**
 * 填充策略服务实现 —— 半自动填充规则。
 *
 * <pre>
 * safe/steady + low/normal risk → auto refill
 * reach 或 high risk             → confirm（返回 3 个候选）
 * 无候选                          → none
 * </pre>
 */
@Service
public class RefillPolicyServiceImpl implements IRefillPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RefillPolicyServiceImpl.class);

    private static final int CONFIRM_CANDIDATE_COUNT = 3;

    @Override
    public RefillResultVO evaluate(CandidateWorkspaceVO workspace,
                                    List<ExcludedCandidateVO> excluded,
                                    String removedTier,
                                    Set<Long> draftIds) {
        RefillResultVO result = new RefillResultVO();

        // 收集排除的 programId
        Set<Long> excludedIds = new java.util.HashSet<>();
        if (excluded != null) {
            for (ExcludedCandidateVO e : excluded) excludedIds.add(e.getProgramId());
        }
        if (draftIds != null) excludedIds.addAll(draftIds);

        // 从工作集同档位获取候选
        WorkspaceTierVO tier = workspace.tierByLevel(removedTier);
        if (tier == null || tier.getCandidates() == null || tier.getCandidates().isEmpty()) {
            result.setPolicy("none");
            result.setReason("工作集中该档位没有候选。");
            return result;
        }

        // 过滤已排除和已在草稿中的
        List<CandidateCardVO> available = tier.getCandidates().stream()
            .filter(c -> c.getFact().getProgramId() != null
                && !excludedIds.contains(c.getFact().getProgramId()))
            .sorted(Comparator.comparingInt(
                c -> Math.abs(c.getFact().getScoreGap() != null ? c.getFact().getScoreGap() : 0)))
            .toList();

        if (available.isEmpty()) {
            result.setPolicy("none");
            result.setReason("工作集中该档位所有候选都已排除或在草稿中。");
            return result;
        }

        // 判断 auto vs confirm
        boolean isReach = "reach".equals(removedTier);
        CandidateCardVO best = available.get(0);
        boolean isHighRisk = isHighRisk(best);

        if (!isReach && !isHighRisk) {
            // 自动填充
            result.setPolicy("auto");
            result.setFilled(best);
            log.info("[Refill] auto — tier={} programId={} school={}",
                removedTier, best.getFact().getProgramId(), best.getFact().getSchoolName());
        } else {
            // 需要确认：返回 top 3
            result.setPolicy("confirm");
            List<RefillCandidateVO> candidates = new ArrayList<>();
            int limit = Math.min(CONFIRM_CANDIDATE_COUNT, available.size());
            for (int i = 0; i < limit; i++) {
                candidates.add(toRefillCandidate(available.get(i), removedTier));
            }
            result.setCandidates(candidates);
            log.info("[Refill] confirm — tier={} candidates={}",
                removedTier, candidates.size());
        }
        return result;
    }

    @Override
    public CandidateCardVO confirmCandidate(CandidateWorkspaceVO workspace, Long programId, String tier) {
        WorkspaceTierVO wsTier = workspace.tierByLevel(tier);
        if (wsTier == null || wsTier.getCandidates() == null) return null;
        return wsTier.getCandidates().stream()
            .filter(c -> programId.equals(c.getFact().getProgramId()))
            .findFirst().orElse(null);
    }

    private boolean isHighRisk(CandidateCardVO c) {
        String risk = c.getFact().getQuotaRisk();
        String completeness = c.getFact().getDataCompleteness();
        return "very_high".equals(risk) || "high".equals(risk)
            || "C".equalsIgnoreCase(completeness);
    }

    private RefillCandidateVO toRefillCandidate(CandidateCardVO c, String tier) {
        RefillCandidateVO vo = new RefillCandidateVO();
        vo.setProgramId(c.getFact().getProgramId());
        vo.setSchoolName(c.getFact().getSchoolName());
        vo.setProgramName(c.getFact().getProgramName());
        vo.setTier(tier);
        vo.setRiskLevel(c.getFact().getQuotaRisk());
        vo.setReason(buildReason(c));
        return vo;
    }

    private String buildReason(CandidateCardVO c) {
        int gap = c.getFact().getScoreGap() != null ? c.getFact().getScoreGap() : 0;
        String quota = c.getFact().getQuotaLabel() != null ? c.getFact().getQuotaLabel() : "";
        return String.format("分差%s，%s", gap >= 0 ? "+" + gap : String.valueOf(gap), quota);
    }
}
