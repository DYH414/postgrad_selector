package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;

import java.util.List;

/**
 * AI 选校服务 —— 在给定候选事实卡列表内，由 AI 挑选最合适的候选并给出理由。
 * <p>AI 只做选择题，不编造数据。每档独立调用（单轮 completion，非对话）。</p>
 */
public interface IAiSelectorService {

    /**
     * AI 在指定档位的候选列表中挑选。
     *
     * @param tier       档位标识：reach / steady / safe
     * @param candidates 候选事实卡列表（已粗筛、已分档）
     * @param estimatedScore 用户预估分数（用于事实卡计算）
     * @return AI 选择结果（含选中的候选 + 被拦截的候选）
     */
    AiSelectionResult select(String tier, List<CandidateCardVO> candidates, int estimatedScore);
}
