package com.ruoyi.postgrad.recommend.service.impl;

import org.springframework.stereotype.Service;

import java.util.List;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.IDraftSummaryMessageService;

@Service
public class DraftSummaryMessageServiceImpl implements IDraftSummaryMessageService {

    @Override
    public String generateSummary(DraftVO draft) {
        if (draft == null || draft.getTiers() == null || draft.getTiers().isEmpty()) return null;

        // 1. Gather stats
        int totalCount = 0;
        int reachCount = 0, steadyCount = 0, safeCount = 0;
        int count985 = 0, count211 = 0, countDoubleFirst = 0;
        int smallQuotaCount = 0;
        int tightGapCount = 0;
        StringBuilder insufficientTiers = new StringBuilder();

        for (TierCandidates t : draft.getTiers()) {
            int c = t.getCandidates() != null ? t.getCandidates().size() : 0;
            totalCount += c;
            switch (t.getLevel()) {
                case "reach" -> reachCount += c;
                case "steady" -> steadyCount += c;
                case "safe" -> safeCount += c;
            }
            if (t.isInsufficient()) {
                if (insufficientTiers.length() > 0) insufficientTiers.append("、");
                insufficientTiers.append(t.getLabel());
            }
            for (CandidateCardVO card : t.getCandidates() != null ? t.getCandidates() : List.<CandidateCardVO>of()) {
                SchoolFact f = card.getFact();
                if (f == null) continue;
                String tier = f.getSchoolTier();
                if ("985".equals(tier)) count985++;
                else if ("211".equals(tier)) count211++;
                else if ("双一流".equals(tier)) countDoubleFirst++;
                int quota = f.getUnifiedExamQuota() != null ? f.getUnifiedExamQuota() : (f.getPlanCount() != null ? f.getPlanCount() : 99);
                if (quota > 0 && quota <= 10) smallQuotaCount++;
                Integer gap = f.getScoreGap();
                if (gap != null && gap < 10) tightGapCount++;
            }
        }

        if (totalCount == 0) return null;

        // 2. Build summary
        StringBuilder sb = new StringBuilder();
        sb.append("草稿已经生成完成。\n\n");

        // Structure line
        sb.append("这份方案共 ").append(totalCount).append(" 所：冲刺 ")
          .append(reachCount).append(" 所、稳妥 ").append(steadyCount)
          .append(" 所、保底 ").append(safeCount).append(" 所。");

        // Overall judgment
        if (reachCount >= 2 && count985 + count211 > 0) {
            sb.append("整体冲刺档保留了更高层次的选择，");
        }
        if (safeCount >= 3) {
            sb.append("保底档比较扎实。");
        } else if (safeCount == 0) {
            sb.append("但保底档暂时空缺，需要补齐。");
        } else if (safeCount < 3) {
            sb.append("保底档数量偏少，建议补齐后再生成报告。");
        }

        // Risk alerts
        if (smallQuotaCount > 0) {
            sb.append("\n\n⚠️ 有 ").append(smallQuotaCount).append(" 所学校的名额不超过 10 人，需要关注录取稳定性。");
        }

        // Insufficient tiers
        if (insufficientTiers.length() > 0) {
            sb.append("\n\n当前 ").append(insufficientTiers).append(" 候选不足，建议优先补齐。");
        }

        // Next steps
        sb.append("\n\n你可以先检查：冲刺档的风险是否可接受；保底档的学校和城市是否真的愿意去。");
        sb.append("\n\n接下来可以让我帮你分析具体学校、调整草稿，或直接生成报告。");

        return sb.toString();
    }
}
