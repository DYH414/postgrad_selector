package com.ruoyi.postgrad.recommend.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ReplaceResultVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;

/**
 * 草稿调整服务 —— 移除、替换、加回、候选列表查询。
 * <p>从 DraftServiceImpl 拆分，保持每个类职责单一且不超过 400 行。</p>
 */
@Service
public class DraftAdjustServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(DraftAdjustServiceImpl.class);

    private static final String DRAFT_KEY_PREFIX = "ai:v2:draft:";
    private static final String DRAFT_POOL_KEY_PREFIX = "ai:v2:draft:pool:";
    private static final long TTL_DAYS = 7;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 从草稿中移除候选。
     */
    public DraftVO removeCandidate(Long userId, Long programId) {
        DraftVO draft = readDraft(userId);

        for (TierCandidates tier : draft.getTiers()) {
            List<CandidateCardVO> candidates = tier.getCandidates();
            for (int i = 0; i < candidates.size(); i++) {
                CandidateCardVO c = candidates.get(i);
                if (programId.equals(c.getFact().getProgramId())) {
                    c.setStatus("removed");
                    candidates.remove(i);
                    if (draft.getRemovedCandidates() == null) {
                        draft.setRemovedCandidates(new ArrayList<>());
                    }
                    draft.getRemovedCandidates().add(c);
                    tier.setInsufficient(candidates.size() < tier.getTargetCount());
                    if (tier.isInsufficient()) {
                        tier.setInsufficientReason(String.format(
                            "%s候选不足，当前仅 %d 所。可手动加回或替换。",
                            tier.getLabel(), candidates.size()));
                    }
                    writeDraft(userId, draft);
                    log.info("[DraftAdjust] userId={} removed programId={} from tier={}",
                        userId, programId, tier.getLevel());
                    return draft;
                }
            }
        }
        return draft;
    }

    /**
     * 替换草稿中的候选。
     */
    public ReplaceResultVO replaceCandidate(Long userId, Long removeProgramId, String tier, String preference) {
        List<CandidateCardVO> poolSnapshot = readPoolSnapshot(userId);
        if (poolSnapshot.isEmpty()) {
            throw new IllegalStateException("候选池快照已过期，请重新生成草稿");
        }

        DraftVO draft = readDraft(userId);
        Set<Long> draftIds = collectDraftProgramIds(draft);
        draftIds.add(removeProgramId);

        List<CandidateCardVO> sameTier = poolSnapshot.stream()
            .filter(c -> tier.equals(c.getFact().inferTier()) && !draftIds.contains(c.getFact().getProgramId()))
            .collect(Collectors.toList());

        if (sameTier.isEmpty()) {
            throw new IllegalStateException("该档没有可替换的候选");
        }

        CandidateCardVO replacement = pickReplacement(sameTier, preference);

        draft = removeCandidate(userId, removeProgramId);

        replacement.setStatus("selected");
        for (TierCandidates t : draft.getTiers()) {
            if (t.getLevel().equals(tier)) {
                t.getCandidates().add(replacement);
                t.setInsufficient(t.getCandidates().size() < t.getTargetCount());
                break;
            }
        }

        writeDraft(userId, draft);

        ReplaceResultVO result = new ReplaceResultVO();
        result.setDraft(draft);
        result.setReplacedWith(replacement);
        return result;
    }

    /**
     * 将之前移除的候选加回草稿。
     */
    public DraftVO addBackCandidate(Long userId, Long programId) {
        DraftVO draft = readDraft(userId);
        if (draft.getRemovedCandidates() == null) return draft;

        CandidateCardVO toRestore = null;
        for (CandidateCardVO c : draft.getRemovedCandidates()) {
            if (programId.equals(c.getFact().getProgramId())) {
                toRestore = c;
                break;
            }
        }
        if (toRestore == null) return draft;

        toRestore.setStatus("selected");
        String targetTier = toRestore.getFact().inferTier();
        for (TierCandidates t : draft.getTiers()) {
            if (t.getLevel().equals(targetTier)) {
                t.getCandidates().add(toRestore);
                t.setInsufficient(t.getCandidates().size() < t.getTargetCount());
                break;
            }
        }
        draft.getRemovedCandidates().remove(toRestore);
        writeDraft(userId, draft);
        return draft;
    }

    /**
     * 获取同档替代候选列表。
     */
    public List<CandidateCardVO> getAlternatives(Long userId, String tier, Long excludeId) {
        List<CandidateCardVO> poolSnapshot = readPoolSnapshot(userId);
        DraftVO draft = readDraft(userId);
        Set<Long> draftIds = collectDraftProgramIds(draft);
        if (excludeId != null) draftIds.add(excludeId);

        return poolSnapshot.stream()
            .filter(c -> tier.equals(c.getFact().inferTier()) && !draftIds.contains(c.getFact().getProgramId()))
            .sorted((a, b) -> Integer.compare(
                Math.abs(a.getFact().getScoreGap() != null ? a.getFact().getScoreGap() : 0),
                Math.abs(b.getFact().getScoreGap() != null ? b.getFact().getScoreGap() : 0)))
            .limit(10)
            .collect(Collectors.toList());
    }

    // ── Redis 读写 ──

    DraftVO readDraft(Long userId) {
        String json = redisTemplate.opsForValue().get(DRAFT_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) {
            return emptyDraft();
        }
        try {
            return JSON.parseObject(json, DraftVO.class);
        } catch (Exception e) {
            log.warn("[DraftAdjust] Failed to parse draft: {}", e.getMessage());
            return emptyDraft();
        }
    }

    void writeDraft(Long userId, DraftVO draft) {
        redisTemplate.opsForValue().set(DRAFT_KEY_PREFIX + userId, JSON.toJSONString(draft), Duration.ofDays(TTL_DAYS));
    }

    List<CandidateCardVO> readPoolSnapshot(Long userId) {
        String json = redisTemplate.opsForValue().get(DRAFT_POOL_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return JSON.parseArray(json, CandidateCardVO.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ── helpers ──

    private Set<Long> collectDraftProgramIds(DraftVO draft) {
        Set<Long> ids = new LinkedHashSet<>();
        for (TierCandidates t : draft.getTiers()) {
            for (CandidateCardVO c : t.getCandidates()) {
                if (c.getFact().getProgramId() != null) ids.add(c.getFact().getProgramId());
            }
        }
        return ids;
    }

    private CandidateCardVO pickReplacement(List<CandidateCardVO> candidates, String preference) {
        if (candidates.isEmpty()) return null;
        String pref = preference != null ? preference : "safer";
        return switch (pref) {
            case "safer" -> candidates.stream()
                .min((a, b) -> {
                    Integer ga = a.getFact().getScoreGap();
                    Integer gb = b.getFact().getScoreGap();
                    if (ga == null) return 1;
                    if (gb == null) return -1;
                    return Integer.compare(gb, ga);
                }).orElse(candidates.get(0));
            case "higher_tier" -> candidates.stream()
                .max((a, b) -> Integer.compare(
                    tierWeight(a.getFact().getSchoolTier()),
                    tierWeight(b.getFact().getSchoolTier())))
                .orElse(candidates.get(0));
            default -> candidates.get(0);
        };
    }

    private int tierWeight(String label) {
        if (label == null) return 0;
        if (label.contains("985")) return 3;
        if (label.contains("211") || label.contains("双一流")) return 2;
        return 1;
    }

    private DraftVO emptyDraft() {
        DraftVO d = new DraftVO();
        List<TierCandidates> tiers = new ArrayList<>(3);
        tiers.add(emptyTier("reach", "冲刺档", 3));
        tiers.add(emptyTier("steady", "稳妥档", 4));
        tiers.add(emptyTier("safe", "保底档", 3));
        d.setTiers(tiers);
        d.setRemovedCandidates(Collections.emptyList());
        d.setBlockedCandidates(Collections.emptyList());
        return d;
    }

    private TierCandidates emptyTier(String level, String label, int target) {
        TierCandidates t = new TierCandidates();
        t.setLevel(level); t.setLabel(label); t.setTargetCount(target);
        t.setCandidates(Collections.emptyList());
        t.setInsufficient(true);
        t.setInsufficientReason("点击「生成 AI 推荐草稿」开始");
        return t;
    }
}
