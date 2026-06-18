package com.ruoyi.postgrad.recommend.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftDecisionLogVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementItemResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ExcludedCandidateVO;
import com.ruoyi.postgrad.recommend.domain.RefillResultVO;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.IDraftDecisionLogService;
import com.ruoyi.postgrad.recommend.service.IDraftMutationService;
import com.ruoyi.postgrad.recommend.service.IRefillPolicyService;

/**
 * 草稿变更服务实现 —— 编排移除、添加、替换与填充策略。
 */
@Service
public class DraftMutationServiceImpl implements IDraftMutationService {

    private static final Logger log = LoggerFactory.getLogger(DraftMutationServiceImpl.class);

    @Autowired
    private IRefillPolicyService refillPolicyService;

    @Autowired
    private IDraftDecisionLogService decisionLogService;

    @Override
    public DraftMutationResultVO removeCandidate(Long userId, Long programId,
                                                  CandidateWorkspaceVO workspace) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("remove");

        // 1. 先找到被移除候选的信息（用于日志和排除）
        DraftVO before = readDraft(userId);
        String removedTier = findCandidateTier(before, programId);
        String schoolName = findCandidateName(before, programId);

        // 2. 执行移除（直接操作Redis中的DraftVO）
        DraftVO draft = removeFromDraft(userId, programId);
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));

        // 3. 记录排除
        ExcludedCandidateVO excluded = new ExcludedCandidateVO();
        excluded.setProgramId(programId);
        excluded.setSchoolName(schoolName);
        excluded.setReasonType("user_removed");
        excluded.setTierAtRemoval(removedTier);
        excluded.setCreatedAt(LocalDateTime.now());
        saveExcluded(userId, excluded);

        // 4. 执行填充策略
        if (removedTier != null && workspace != null) {
            List<ExcludedCandidateVO> allExcluded = loadExcluded(userId);
            Set<Long> draftIds = collectDraftIds(draft);
            RefillResultVO refill = refillPolicyService.evaluate(
                workspace, allExcluded, removedTier, draftIds);
            result.setRefill(refill);

            // 自动填充：直接添加
            if ("auto".equals(refill.getPolicy()) && refill.getFilled() != null) {
                CandidateCardVO filled = refill.getFilled();
                draft = addToDraft(draft, filled, removedTier);
                result.setDraft(draft);
                result.setDraftCount(countDraft(draft));
                logDecision(userId, "auto_refill", filled.getFact().getProgramId(),
                    filled.getFact().getSchoolName(), removedTier, "system",
                    "同档位自动填充");
            }
        }

        // 5. 决策日志
        logDecision(userId, "remove", programId, schoolName, removedTier, "user",
            "用户移除" + (schoolName != null ? " " + schoolName : ""));
        return result;
    }

    @Override
    public DraftMutationResultVO addCandidate(Long userId, Long programId, String tier,
                                               CandidateWorkspaceVO workspace) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("add");

        CandidateCardVO candidate = findInWorkspace(workspace, programId, tier);
        if (candidate == null) {
            result.setOk(false);
            return result;
        }

        DraftVO draft = readDraft(userId);
        draft = addToDraft(draft, candidate, tier);
        writeDraft(userId, draft);

        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        logDecision(userId, "manual_add", programId,
            candidate.getFact().getSchoolName(), tier, "user",
            "手动添加候选人");
        return result;
    }

    @Override
    public DraftMutationResultVO addCandidateDirect(Long userId, CandidateCardVO candidate, String tier) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("add_external");

        DraftVO draft = readDraft(userId);
        draft = addToDraft(draft, candidate, tier);
        writeDraft(userId, draft);

        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        logDecision(userId, "manual_add_external",
            candidate.getFact() != null ? candidate.getFact().getProgramId() : null,
            candidate.getFact() != null ? candidate.getFact().getSchoolName() : null,
            tier, "user", "手动添加外部候选");
        log.info("[Mutation] addCandidateDirect userId={} programId={} tier={}",
            userId,
            candidate.getFact() != null ? candidate.getFact().getProgramId() : null,
            tier);
        return result;
    }

    @Override
    public DraftMutationResultVO replaceCandidate(Long userId, Long removeProgramId,
                                                   Long addProgramId, String tier,
                                                   CandidateWorkspaceVO workspace) {
        // 先移除，再添加
        DraftMutationResultVO removeResult = removeCandidate(userId, removeProgramId, workspace);
        if (!removeResult.isOk()) return removeResult;

        CandidateCardVO replacement = findInWorkspace(workspace, addProgramId, tier);
        if (replacement == null) {
            DraftMutationResultVO fail = new DraftMutationResultVO();
            fail.setOk(false);
            fail.setAction("replace");
            return fail;
        }

        DraftVO draft = removeResult.getDraft();
        draft = addToDraft(draft, replacement, tier);
        writeDraft(userId, draft);

        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("replace");
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        logDecision(userId, "replace", addProgramId,
            replacement.getFact().getSchoolName(), tier, "user",
            "替换自 programId=" + removeProgramId);
        return result;
    }

    @Override
    public DraftMutationResultVO confirmRefillCandidate(Long userId, Long programId, String tier,
                                                         CandidateWorkspaceVO workspace) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("add");

        CandidateCardVO candidate = refillPolicyService.confirmCandidate(workspace, programId, tier);
        if (candidate == null) {
            result.setOk(false);
            return result;
        }

        DraftVO draft = readDraft(userId);
        draft = addToDraft(draft, candidate, tier);
        writeDraft(userId, draft);

        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        logDecision(userId, "confirm_refill", programId,
            candidate.getFact().getSchoolName(), tier, "user",
            "确认填充候选");
        return result;
    }

    // ── fillTier / batchRemove ──

    @Override
    public DraftMutationResultVO fillTier(Long userId, String tier,
                                           CandidateWorkspaceVO workspace) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("fill_tier");

        DraftVO draft = readDraft(userId);
        TierCandidates draftTier = findDraftTier(draft, tier);
        int current = draftTier != null && draftTier.getCandidates() != null
            ? draftTier.getCandidates().size() : 0;
        int target = draftTier != null ? draftTier.getTargetCount() : ("steady".equals(tier) ? 4 : 3);
        int needed = target - current;
        if (needed <= 0) {
            result.setDraft(draft);
            result.setDraftCount(countDraft(draft));
            return result;
        }

        var wsTier = workspace.tierByLevel(tier);
        if (wsTier == null || wsTier.getCandidates() == null || wsTier.getCandidates().isEmpty()) {
            result.setOk(false);
            return result;
        }

        Set<Long> excluded = collectDraftIds(draft);
        if (draft.getRemovedCandidates() != null) {
            for (CandidateCardVO c : draft.getRemovedCandidates()) {
                if (c.getFact().getProgramId() != null)
                    excluded.add(c.getFact().getProgramId());
            }
        }

        String preference = switch (tier) {
            case "safe" -> "safer";
            case "reach" -> "higher_tier";
            default -> "balanced";
        };

        List<CandidateCardVO> available = wsTier.getCandidates().stream()
            .filter(c -> c.getFact().getProgramId() != null
                && !excluded.contains(c.getFact().getProgramId()))
            .sorted((a, b) -> {
                if ("safer".equals(preference)) {
                    Integer ga = a.getFact().getScoreGap(); Integer gb = b.getFact().getScoreGap();
                    return Integer.compare(gb != null ? gb : 0, ga != null ? ga : 0);
                }
                if ("higher_tier".equals(preference)) {
                    return Integer.compare(tierWeight(b.getFact().getSchoolTier()),
                                           tierWeight(a.getFact().getSchoolTier()));
                }
                return 0;
            })
            .limit(needed)
            .toList();

        for (CandidateCardVO c : available) {
            draft = addToDraft(draft, c, tier);
            logDecision(userId, "fill_tier", c.getFact().getProgramId(),
                c.getFact().getSchoolName(), tier, "system",
                "批量填充" + tier + "档");
        }

        writeDraft(userId, draft);
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        log.info("[Mutation] fillTier userId={} tier={} added={}", userId, tier, available.size());
        return result;
    }

    @Override
    public DraftMutationResultVO batchRemove(Long userId, List<Long> programIds,
                                              CandidateWorkspaceVO workspace) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("batch_remove");

        DraftVO draft = readDraft(userId);
        int removed = 0;
        for (Long pid : programIds) {
            if (pid == null) continue;
            String tier = findCandidateTier(draft, pid);
            if (tier == null) continue; // 不在草稿中，跳过

            draft = removeFromDraft(userId, pid);
            removed++;

            // 每个移除触发 refill
            ExcludedCandidateVO excluded = new ExcludedCandidateVO();
            excluded.setProgramId(pid);
            excluded.setReasonType("user_removed");
            excluded.setTierAtRemoval(tier);
            excluded.setCreatedAt(LocalDateTime.now());
            saveExcluded(userId, excluded);

            logDecision(userId, "batch_remove", pid, findCandidateName(draft, pid),
                tier, "user", "批量移除");
        }

        if (removed == 0) {
            result.setOk(false);
        }
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        log.info("[Mutation] batchRemove userId={} removed={}/{}", userId, removed, programIds.size());
        return result;
    }

    @Override
    public Map<String, Object> batchReplace(Long userId, List<DraftReplacementRequest> replacements,
                                             CandidateWorkspaceVO workspace,
                                             Function<Long, CandidateCardVO> externalCandidateResolver) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "batch_replace");
        result.put("requested", replacements.size());

        DraftVO draft = readDraft(userId);
        List<DraftReplacementItemResultVO> items = new ArrayList<>();
        int replaced = 0;
        int failed = 0;

        for (DraftReplacementRequest req : replacements) {
            Long removeId = req.getRemoveProgramId();
            Long addId = req.getAddProgramId();
            DraftReplacementItemResultVO item = new DraftReplacementItemResultVO();
            item.setRemoveProgramId(removeId);
            item.setAddProgramId(addId);

            // 1. Find removeProgramId in draft → determine tier
            String tier = findCandidateTier(draft, removeId);
            if (tier == null) {
                item.setOk(false);
                item.setError("not_in_draft");
                item.setMessage("Remove candidate not found in draft.");
                items.add(item);
                failed++;
                continue;
            }
            item.setTier(tier);
            item.setRemovedSchoolName(findCandidateName(draft, removeId));

            // 2. Check addProgramId is not already in draft (different from removeId)
            if (!addId.equals(removeId) && findCandidateTier(draft, addId) != null) {
                item.setOk(false);
                item.setError("duplicate_candidate");
                item.setMessage("Replacement candidate already exists in draft.");
                items.add(item);
                failed++;
                continue;
            }

            // 3. Find replacement candidate — workspace first, then DB fallback
            CandidateCardVO replacement = findInWorkspace(workspace, addId, tier);
            if (replacement == null && externalCandidateResolver != null) {
                replacement = externalCandidateResolver.apply(addId);
            }
            if (replacement == null) {
                item.setOk(false);
                item.setError("not_found");
                item.setMessage("Replacement candidate not found in workspace or database.");
                items.add(item);
                failed++;
                continue;
            }
            item.setAddedSchoolName(replacement.getFact() != null ? replacement.getFact().getSchoolName() : "unknown");

            // 4. Execute: remove then add (no refill — this is a swap)
            draft = removeFromDraft(userId, removeId);
            draft = addToDraft(draft, replacement, tier);
            writeDraft(userId, draft);

            // 5. Record exclusion for removed candidate
            ExcludedCandidateVO excluded = new ExcludedCandidateVO();
            excluded.setProgramId(removeId);
            excluded.setSchoolName(item.getRemovedSchoolName());
            excluded.setReasonType("replaced");
            excluded.setTierAtRemoval(tier);
            excluded.setCreatedAt(LocalDateTime.now());
            saveExcluded(userId, excluded);

            // 6. Log decision
            logDecision(userId, "batch_replace", addId, item.getAddedSchoolName(), tier, "user",
                "批量替换自 programId=" + removeId);

            item.setOk(true);
            items.add(item);
            replaced++;
        }

        result.put("ok", replaced > 0);
        result.put("replaced", replaced);
        result.put("failed", failed);
        result.put("draftCount", countDraft(draft));
        result.put("items", items);

        log.info("[Mutation] batchReplace userId={} requested={} replaced={} failed={}",
            userId, replacements.size(), replaced, failed);
        return result;
    }

    private TierCandidates findDraftTier(DraftVO draft, String tier) {
        if (draft.getTiers() == null) return null;
        return draft.getTiers().stream()
            .filter(t -> tier.equals(t.getLevel())).findFirst().orElse(null);
    }

    private int tierWeight(String label) {
        if (label == null) return 0;
        if (label.contains("985")) return 3;
        if (label.contains("211") || label.contains("双一流")) return 2;
        return 1;
    }

    // ── helpers ──

    private String findCandidateTier(DraftVO draft, Long programId) {
        if (draft.getTiers() == null) return null;
        for (TierCandidates t : draft.getTiers()) {
            if (t.getCandidates() != null) {
                for (CandidateCardVO c : t.getCandidates()) {
                    if (programId.equals(c.getFact().getProgramId())) return t.getLevel();
                }
            }
        }
        return null;
    }

    private String findCandidateName(DraftVO draft, Long programId) {
        if (draft.getTiers() == null) return null;
        for (TierCandidates t : draft.getTiers()) {
            if (t.getCandidates() != null) {
                for (CandidateCardVO c : t.getCandidates()) {
                    if (programId.equals(c.getFact().getProgramId()))
                        return c.getFact().getSchoolName();
                }
            }
        }
        return null;
    }

    private DraftVO addToDraft(DraftVO draft, CandidateCardVO candidate, String tier) {
        candidate.setStatus("selected");
        Long pid = candidate.getFact() != null ? candidate.getFact().getProgramId() : null;
        for (TierCandidates t : draft.getTiers()) {
            if (t.getLevel().equals(tier)) {
                if (t.getCandidates() == null) t.setCandidates(new ArrayList<>());
                // 去重：已存在则跳过
                if (pid != null && t.getCandidates().stream()
                        .anyMatch(c -> pid.equals(c.getFact() != null ? c.getFact().getProgramId() : null))) {
                    return draft;
                }
                t.getCandidates().add(candidate);
                t.setInsufficient(t.getCandidates().size() < t.getTargetCount());
                break;
            }
        }
        // 从 removedCandidates 中清除，防止重复累积
        if (pid != null && draft.getRemovedCandidates() != null) {
            draft.getRemovedCandidates().removeIf(
                c -> pid.equals(c.getFact() != null ? c.getFact().getProgramId() : null));
        }
        return draft;
    }

    private int countDraft(DraftVO draft) {
        if (draft.getTiers() == null) return 0;
        return draft.getTiers().stream()
            .mapToInt(t -> t.getCandidates() != null ? t.getCandidates().size() : 0).sum();
    }

    private Set<Long> collectDraftIds(DraftVO draft) {
        Set<Long> ids = new LinkedHashSet<>();
        if (draft.getTiers() != null) {
            for (TierCandidates t : draft.getTiers()) {
                if (t.getCandidates() != null) {
                    for (CandidateCardVO c : t.getCandidates()) {
                        if (c.getFact().getProgramId() != null)
                            ids.add(c.getFact().getProgramId());
                    }
                }
            }
        }
        // 已移除的候选也纳入排除范围，防止 refill/fillTier 重复加入
        if (draft.getRemovedCandidates() != null) {
            for (CandidateCardVO c : draft.getRemovedCandidates()) {
                if (c.getFact().getProgramId() != null)
                    ids.add(c.getFact().getProgramId());
            }
        }
        return ids;
    }

    private CandidateCardVO findInWorkspace(CandidateWorkspaceVO workspace,
                                             Long programId, String tier) {
        if (workspace == null) return null;
        var tierVO = workspace.tierByLevel(tier);
        if (tierVO == null || tierVO.getCandidates() == null) return null;
        return tierVO.getCandidates().stream()
            .filter(c -> programId.equals(c.getFact().getProgramId()))
            .findFirst().orElse(null);
    }

    // ── DraftVO Redis 读写（取代 DraftAdjustServiceImpl）──

    DraftVO readDraft(Long userId) {
        String json = redisTemplate.opsForValue().get(DraftServiceImpl.DRAFT_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) return emptyDraft();
        try {
            return com.alibaba.fastjson2.JSON.parseObject(json, DraftVO.class);
        } catch (Exception e) {
            log.warn("[Mutation] Failed to parse draft userId={}: {}", userId, e.getMessage());
            return emptyDraft();
        }
    }

    void writeDraft(Long userId, DraftVO draft) {
        redisTemplate.opsForValue().set(
            DraftServiceImpl.DRAFT_KEY_PREFIX + userId,
            com.alibaba.fastjson2.JSON.toJSONString(draft),
            java.time.Duration.ofDays(7));
    }

    private DraftVO removeFromDraft(Long userId, Long programId) {
        DraftVO draft = readDraft(userId);
        if (draft.getTiers() == null) return draft;
        for (TierCandidates tier : draft.getTiers()) {
            List<CandidateCardVO> candidates = tier.getCandidates();
            if (candidates == null) continue;
            for (int i = 0; i < candidates.size(); i++) {
                CandidateCardVO c = candidates.get(i);
                if (programId.equals(c.getFact().getProgramId())) {
                    c.setStatus("removed");
                    candidates.remove(i);
                    if (draft.getRemovedCandidates() == null) {
                        draft.setRemovedCandidates(new ArrayList<>());
                    }
                    // 去重：已在 removedCandidates 中则跳过
                    boolean alreadyRemoved = draft.getRemovedCandidates().stream()
                        .anyMatch(rc -> programId.equals(rc.getFact() != null ? rc.getFact().getProgramId() : null));
                    if (!alreadyRemoved) {
                        draft.getRemovedCandidates().add(c);
                    }
                    tier.setInsufficient(candidates.size() < tier.getTargetCount());
                    if (tier.isInsufficient()) {
                        tier.setInsufficientReason(String.format(
                            "%s候选不足，当前仅 %d 所。可手动加回或替换。",
                            tier.getLabel(), candidates.size()));
                    }
                    writeDraft(userId, draft);
                    return draft;
                }
            }
        }
        return draft;
    }

    private DraftVO emptyDraft() {
        DraftVO d = new DraftVO();
        List<TierCandidates> tiers = new ArrayList<>(3);
        for (String[] t : new String[][]{{"reach", "冲刺档", "3"}, {"steady", "稳妥档", "4"}, {"safe", "保底档", "3"}}) {
            TierCandidates tc = new TierCandidates();
            tc.setLevel(t[0]); tc.setLabel(t[1]); tc.setTargetCount(Integer.parseInt(t[2]));
            tc.setCandidates(new ArrayList<>()); tc.setInsufficient(true);
            tc.setInsufficientReason("点击「生成 AI 推荐草稿」开始");
            tiers.add(tc);
        }
        d.setTiers(tiers);
        d.setRemovedCandidates(new ArrayList<>());
        d.setBlockedCandidates(new ArrayList<>());
        return d;
    }

    // ── ExcludedCandidate Redis 存储 ──

    private static final String EXCLUDED_KEY_PREFIX = "ai:v2:excluded:";
    private static final java.time.Duration EXCLUDED_TTL = java.time.Duration.ofDays(7);

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private void saveExcluded(Long userId, ExcludedCandidateVO e) {
        List<ExcludedCandidateVO> list = loadExcluded(userId);
        list.add(e);
        redisTemplate.opsForValue().set(
            EXCLUDED_KEY_PREFIX + userId,
            com.alibaba.fastjson2.JSON.toJSONString(list),
            EXCLUDED_TTL);
    }

    private List<ExcludedCandidateVO> loadExcluded(Long userId) {
        String json = redisTemplate.opsForValue().get(EXCLUDED_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return com.alibaba.fastjson2.JSON.parseArray(json, ExcludedCandidateVO.class);
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private void logDecision(Long userId, String eventType, Long programId,
                              String schoolName, String tier, String actor, String reason) {
        DraftDecisionLogVO entry = new DraftDecisionLogVO();
        entry.setEventType(eventType);
        entry.setProgramId(programId);
        entry.setSchoolName(schoolName);
        entry.setTier(tier);
        entry.setActor(actor);
        entry.setReason(reason);
        entry.setCreatedAt(LocalDateTime.now());
        decisionLogService.append(userId, entry);
    }
}
