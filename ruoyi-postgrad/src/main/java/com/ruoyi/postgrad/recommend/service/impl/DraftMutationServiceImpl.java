package com.ruoyi.postgrad.recommend.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftDecisionLogVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementItemResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ExcludedCandidateVO;
import com.ruoyi.postgrad.recommend.domain.RefillResultVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
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
    private static final int MAX_DRAFT_CANDIDATES = 10;
    private static final Set<String> VALID_TIERS = Set.of("reach", "steady", "safe");

    @Autowired
    private IRefillPolicyService refillPolicyService;

    @Autowired
    private IDraftDecisionLogService decisionLogService;

    @Autowired
    private RecommendationMapper recommendationMapper;

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

        // 4. 决策日志
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
        if (!addToDraft(draft, candidate, tier)) {
            return rejectedMutation(result, draft);
        }
        writeDraft(userId, draft);

        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        logDecision(userId, "manual_add", programId,
            candidate.getFact().getSchoolName(), tier, "user",
            "手动添加候选人");
        return result;
    }

    @Override
    public DraftMutationResultVO removeFromDraftDirect(Long userId, Long programId) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("remove_direct");
        DraftVO draft = removeFromDraft(userId, programId);
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        return result;
    }

    @Override
    public DraftMutationResultVO addCandidateDirect(Long userId, CandidateCardVO candidate, String tier) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("add_external");

        DraftVO draft = readDraft(userId);
        if (!addToDraft(draft, candidate, tier)) {
            return rejectedMutation(result, draft);
        }
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
        // 原子替换：先直接从草稿移除，再添加替换候选，不触发任何补位逻辑
        DraftVO before = readDraft(userId);
        String removedTier = findCandidateTier(before, removeProgramId);
        if (removedTier == null) {
            DraftMutationResultVO fail = new DraftMutationResultVO();
            fail.setOk(false);
            fail.setAction("replace");
            return fail;
        }
        String removedSchoolName = findCandidateName(before, removeProgramId);

        CandidateCardVO replacement = findInWorkspace(workspace, addProgramId, tier);
        if (replacement == null) {
            DraftMutationResultVO fail = new DraftMutationResultVO();
            fail.setOk(false);
            fail.setAction("replace");
            return fail;
        }

        if (!canAddToDraft(before, replacement, tier, removeProgramId)) {
            DraftMutationResultVO fail = new DraftMutationResultVO();
            fail.setOk(false);
            fail.setAction("replace");
            fail.setDraft(before);
            fail.setDraftCount(countDraft(before));
            return fail;
        }

        // 在内存快照上完成 swap，成功后只发布一次新草稿。
        // 避免先删入 Redis，后续添加/写入失败导致原候选丢失。
        DraftVO draft = JSON.parseObject(JSON.toJSONString(before), DraftVO.class);
        removeFromDraftInMemory(draft, removeProgramId);
        if (!addToDraft(draft, replacement, tier)) {
            return rejectedMutation(newMutationResult("replace"), before);
        }
        writeDraft(userId, draft);

        // 记录排除
        ExcludedCandidateVO excluded = new ExcludedCandidateVO();
        excluded.setProgramId(removeProgramId);
        excluded.setSchoolName(removedSchoolName);
        excluded.setReasonType("replaced");
        excluded.setTierAtRemoval(removedTier);
        excluded.setCreatedAt(LocalDateTime.now());
        saveExcluded(userId, excluded);

        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(true);
        result.setAction("replace");
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));

        String addedSchoolName = replacement.getFact() != null ? replacement.getFact().getSchoolName() : "unknown";
        logDecision(userId, "replace", addProgramId, addedSchoolName, tier, "user",
            "替换自 programId=" + removeProgramId + " (" + removedSchoolName + ")");

        log.info("[Mutation] replace userId={} removeProgramId={} ({} -> {}) draftCount={}",
            userId, removeProgramId, removedSchoolName, addedSchoolName, result.getDraftCount());
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
        if (!addToDraft(draft, candidate, tier)) {
            return rejectedMutation(result, draft);
        }
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

        // 1. 从 workspace 选取候选
        List<CandidateCardVO> available = new ArrayList<>();
        var wsTier = workspace != null ? workspace.tierByLevel(tier) : null;
        if (wsTier != null && wsTier.getCandidates() != null && !wsTier.getCandidates().isEmpty()) {
            available = wsTier.getCandidates().stream()
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
                .collect(Collectors.toCollection(ArrayList::new));
        }

        int wsAdded = available.size();
        int shortfall = needed - wsAdded;

        // 2. workspace 不够 → DB 回退
        if (shortfall > 0) {
            List<CandidateCardVO> dbFallback = queryDbFallback(draft, tier, preference,
                shortfall, excluded);
            available.addAll(dbFallback);
            log.info("[Mutation] fillTier userId={} tier={} wsAdded={} dbFallback={}",
                userId, tier, wsAdded, dbFallback.size());
        }

        if (available.isEmpty()) {
            result.setOk(false);
            return result;
        }

        int added = 0;
        for (CandidateCardVO c : available) {
            if (addToDraft(draft, c, tier)) {
                added++;
                logDecision(userId, "fill_tier", c.getFact().getProgramId(),
                    c.getFact().getSchoolName(), tier, "system",
                    "批量填充" + tier + "档");
            }
        }
        if (added == 0) {
            return rejectedMutation(result, draft);
        }

        writeDraft(userId, draft);
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        log.info("[Mutation] fillTier userId={} tier={} added={}", userId, tier, added);
        return result;
    }

    // ── DB fallback for fillTier ──

    private static final List<String> EXAM_408_SUBJECT_CODES = List.of(
        "101,204,302,408", "101,201,301,408");

    /**
     * workspace 候选不足时从 DB 补位。
     */
    private List<CandidateCardVO> queryDbFallback(DraftVO draft, String tier, String preference,
                                                   int shortfall, Set<Long> excluded) {
        if (draft.getProfileBasis() == null) return Collections.emptyList();
        Integer estimatedScore = draft.getProfileBasis().getEstimatedScore();
        String targetRegions = draft.getProfileBasis().getTargetRegions();
        if (estimatedScore == null || estimatedScore <= 0) return Collections.emptyList();

        List<String> regions = parseRegionsList(targetRegions);

        // 查 DB：双考试组合 + programId 去重
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<RowMap> allRows = new ArrayList<>();
        for (String subjectCodes : EXAM_408_SUBJECT_CODES) {
            List<RowMap> rows = recommendationMapper.selectCandidates(
                subjectCodes, regions, null, estimatedScore, 30, "full_time");
            if (rows != null) {
                for (RowMap row : rows) {
                    Object pid = row.get("programId");
                    if (pid instanceof Number n && seen.add(n.longValue())) {
                        allRows.add(row);
                    }
                }
            }
        }

        return allRows.stream()
            .map(SchoolFact::fromRow)
            .filter(f -> {
                Integer avg = f.getAvgAdmittedScore();
                int gap = avg != null ? estimatedScore - avg : 0;
                f.setScoreGap(gap);
                // 计算 canBeSafe（DB 回退也需要）
                int quota = f.getUnifiedExamQuota() != null ? f.getUnifiedExamQuota()
                    : (f.getPlanCount() != null ? f.getPlanCount() : 0);
                boolean safe = SchoolFact.canBeSafe(quota, f.getDataCompleteness(),
                    f.getAdmissionLow(), f.getAdmissionHigh());
                f.setCanBeSafe(safe);
                return gap >= -30;
            })
            .filter(f -> f.getProgramId() != null && !excluded.contains(f.getProgramId()))
            .filter(f -> !"C".equalsIgnoreCase(f.getDataCompleteness()))
            .filter(f -> {
                int gap = f.getScoreGap() != null ? f.getScoreGap() : 0;
                String classifiedTier = SchoolFact.classifyTier(gap, f.getCanBeSafe());
                return tier.equals(classifiedTier);
            })
            .sorted((a, b) -> {
                if ("safer".equals(preference)) {
                    Integer ga = a.getScoreGap(); Integer gb = b.getScoreGap();
                    return Integer.compare(gb != null ? gb : 0, ga != null ? ga : 0);
                }
                if ("higher_tier".equals(preference)) {
                    return Integer.compare(tierWeight(b.getSchoolTier()),
                                           tierWeight(a.getSchoolTier()));
                }
                return 0;
            })
            .limit(shortfall)
            .map(CandidateCardVO::fromFact)
            .toList();
    }

    private List<String> parseRegionsList(String raw) {
        if (raw == null || raw.isBlank() || "不限".equals(raw) || "[]".equals(raw)) {
            return Collections.emptyList();
        }
        try {
            return JSON.parseArray(raw, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ── batchRemove ──

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

            String schoolName = findCandidateName(draft, pid); // 移除前取名称
            draft = removeFromDraft(userId, pid);
            removed++;

            // 记录移除结果；补充候选必须由用户另行发起
            ExcludedCandidateVO excluded = new ExcludedCandidateVO();
            excluded.setProgramId(pid);
            excluded.setSchoolName(schoolName);
            excluded.setReasonType("user_removed");
            excluded.setTierAtRemoval(tier);
            excluded.setCreatedAt(LocalDateTime.now());
            saveExcluded(userId, excluded);

            logDecision(userId, "batch_remove", pid, schoolName, tier, "user", "批量移除");
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
            if (!canAddToDraft(draft, replacement, tier, removeId)) {
                item.setOk(false);
                item.setError("draft_invariant_violation");
                item.setMessage("Replacement would violate draft capacity or uniqueness constraints.");
                items.add(item);
                failed++;
                continue;
            }
            item.setAddedSchoolName(replacement.getFact() != null ? replacement.getFact().getSchoolName() : "unknown");

            // 4. Execute on a per-item copy, so even an unexpected add rejection cannot
            // leave the batch's working snapshot with only the removal applied.
            DraftVO nextDraft = JSON.parseObject(JSON.toJSONString(draft), DraftVO.class);
            removeFromDraftInMemory(nextDraft, removeId);
            if (!addToDraft(nextDraft, replacement, tier)) {
                item.setOk(false);
                item.setError("draft_invariant_violation");
                item.setMessage("Replacement would violate draft capacity or uniqueness constraints.");
                items.add(item);
                failed++;
                continue;
            }
            draft = nextDraft;

            item.setOk(true);
            items.add(item);
            replaced++;
        }

        if (replaced > 0) {
            writeDraft(userId, draft);
            // 草稿发布成功后再写排除与决策日志，避免日志成功但草稿仍为旧版。
            for (DraftReplacementItemResultVO item : items) {
                if (!item.isOk()) continue;
                ExcludedCandidateVO excluded = new ExcludedCandidateVO();
                excluded.setProgramId(item.getRemoveProgramId());
                excluded.setSchoolName(item.getRemovedSchoolName());
                excluded.setReasonType("replaced");
                excluded.setTierAtRemoval(item.getTier());
                excluded.setCreatedAt(LocalDateTime.now());
                saveExcluded(userId, excluded);
                logDecision(userId, "batch_replace", item.getAddProgramId(), item.getAddedSchoolName(),
                    item.getTier(), "user", "批量替换自 programId=" + item.getRemoveProgramId());
            }
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

    private boolean addToDraft(DraftVO draft, CandidateCardVO candidate, String tier) {
        if (!canAddToDraft(draft, candidate, tier, null)) return false;
        CandidateCardVO selected = candidate;
        selected.setStatus("selected");
        Long pid = selected.getFact().getProgramId();
        TierCandidates targetTier = findDraftTier(draft, tier);
        if (targetTier.getCandidates() == null) targetTier.setCandidates(new ArrayList<>());
        targetTier.getCandidates().add(selected);
        targetTier.setInsufficient(targetTier.getCandidates().size() < targetTier.getTargetCount());
        targetTier.setInsufficientReason(null);
        // 从 removedCandidates 中清除，防止重复累积
        if (pid != null && draft.getRemovedCandidates() != null) {
            draft.getRemovedCandidates().removeIf(
                c -> pid.equals(c.getFact() != null ? c.getFact().getProgramId() : null));
        }
        return true;
    }

    private boolean canAddToDraft(DraftVO draft, CandidateCardVO candidate, String tier,
                                  Long programIdToRemove) {
        if (draft == null || draft.getTiers() == null || !VALID_TIERS.contains(tier)
                || candidate == null || candidate.getFact() == null
                || candidate.getFact().getProgramId() == null) {
            return false;
        }
        TierCandidates targetTier = findDraftTier(draft, tier);
        if (targetTier == null) return false;

        Long candidateId = candidate.getFact().getProgramId();
        String existingTier = findCandidateTier(draft, candidateId);
        if (existingTier != null && !candidateId.equals(programIdToRemove)) return false;

        int totalAfterRemoval = countDraft(draft)
            - (programIdToRemove != null && findCandidateTier(draft, programIdToRemove) != null ? 1 : 0);
        if (totalAfterRemoval >= MAX_DRAFT_CANDIDATES) return false;

        int tierSize = targetTier.getCandidates() == null ? 0 : targetTier.getCandidates().size();
        if (programIdToRemove != null && tier.equals(findCandidateTier(draft, programIdToRemove))) {
            tierSize--;
        }
        return tierSize < targetTier.getTargetCount();
    }

    private DraftMutationResultVO rejectedMutation(DraftMutationResultVO result, DraftVO draft) {
        result.setOk(false);
        result.setDraft(draft);
        result.setDraftCount(countDraft(draft));
        return result;
    }

    private DraftMutationResultVO newMutationResult(String action) {
        DraftMutationResultVO result = new DraftMutationResultVO();
        result.setOk(false);
        result.setAction(action);
        return result;
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
        if (removeFromDraftInMemory(draft, programId)) {
            writeDraft(userId, draft);
        }
        return draft;
    }

    private boolean removeFromDraftInMemory(DraftVO draft, Long programId) {
        if (draft.getTiers() == null) return false;
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
                    return true;
                }
            }
        }
        return false;
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
