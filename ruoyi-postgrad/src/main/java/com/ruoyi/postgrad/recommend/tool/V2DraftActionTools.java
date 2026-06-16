package com.ruoyi.postgrad.recommend.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.IDraftMutationService;
import com.ruoyi.postgrad.recommend.service.IDraftService;

import dev.langchain4j.agent.tool.Tool;

/**
 * AI 草稿写操作工具集 —— 移除、添加、替换、确认填充。
 * <p>所有写操作通过 {@link IDraftMutationService} 执行，确保填充策略一致触发。</p>
 */
@Component
public class V2DraftActionTools {

    private static final Logger log = LoggerFactory.getLogger(V2DraftActionTools.class);

    private static final String WORKSPACE_KEY_PREFIX = "ai:v2:workspace:";

    @Autowired
    private IDraftService draftService;

    @Autowired
    private IDraftMutationService draftMutationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Tool("Remove one school from the current report draft. Returns refill info: auto-refill may add a replacement, confirm-refill returns candidates for the user to choose from.")
    public String removeDraftCandidate(long programId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");

        DraftVO before = draftService.getDraft(ctx.userId());
        CandidateCardVO target = findCandidate(before, programId);
        if (target == null) return error("program_not_in_draft", "The requested school is not in the current draft.");

        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());
        DraftMutationResultVO mutation = draftMutationService.removeCandidate(
            ctx.userId(), programId, workspace);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", "remove");
        result.put("programId", programId);
        result.put("schoolName", schoolName(target));
        result.put("draftCount", mutation.getDraftCount());

        if (mutation.getRefill() != null) {
            Map<String, Object> refill = new LinkedHashMap<>();
            refill.put("policy", mutation.getRefill().getPolicy());
            if (mutation.getRefill().getFilled() != null) {
                CandidateCardVO f = mutation.getRefill().getFilled();
                Map<String, Object> filled = new LinkedHashMap<>();
                filled.put("programId", f.getFact().getProgramId());
                filled.put("schoolName", f.getFact().getSchoolName());
                filled.put("programName", f.getFact().getProgramName());
                filled.put("reason", "同档位自动填充");
                refill.put("filled", filled);
            }
            if (mutation.getRefill().getCandidates() != null && !mutation.getRefill().getCandidates().isEmpty()) {
                refill.put("candidates", mutation.getRefill().getCandidates());
            }
            if (mutation.getRefill().getReason() != null) {
                refill.put("reason", mutation.getRefill().getReason());
            }
            result.put("refill", refill);
        }

        String json = JSON.toJSONString(result);
        V2ChatToolContext.markWriteExecuted(json);
        log.info("[DraftTool] remove userId={} programId={} refill={}",
            ctx.userId(), programId,
            mutation.getRefill() != null ? mutation.getRefill().getPolicy() : "null");
        return json;
    }

    @Tool("Add a candidate to the draft. Searches workspace first, falls back to national DB if not found (for schools outside profile regions). Requires programId and tier (reach/steady/safe).")
    public String addDraftCandidate(long programId, String tier) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");

        // 1. 先尝试从 Workspace 添加
        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());
        DraftMutationResultVO mutation = draftMutationService.addCandidate(
            ctx.userId(), programId, tier, workspace);
        if (mutation.isOk()) {
            return buildAddResult(programId, tier, mutation, "add");
        }

        // 2. Workspace 找不到 → 回退到 DB
        log.info("[DraftTool] addDraftCandidate programId={} not in workspace, falling back to DB", programId);
        CandidateCardVO candidate = buildCandidateFromDb(ctx, programId);
        if (candidate == null) return error("add_failed", "Candidate not found in workspace or database for programId=" + programId);

        DraftMutationResultVO extMutation = draftMutationService.addCandidateDirect(
            ctx.userId(), candidate, tier);
        if (!extMutation.isOk()) return error("add_failed", "Failed to add external candidate.");

        return buildAddResult(programId, tier, extMutation, "add_external");
    }

    private String buildAddResult(long programId, String tier, DraftMutationResultVO mutation, String action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", action);
        result.put("programId", programId);
        result.put("tier", tier);
        result.put("draftCount", mutation.getDraftCount());
        String json = JSON.toJSONString(result);
        V2ChatToolContext.markWriteExecuted(json);
        return json;
    }

    @Tool("Confirm a refill candidate. Use when the user picks one from the confirm-refill candidate list returned by removeDraftCandidate.")
    public String confirmRefillCandidate(long programId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");

        // Determine tier from workspace
        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());
        String tier = findTierInWorkspace(workspace, programId);
        if (tier == null) return error("not_in_workspace", "Candidate not found in any workspace tier.");

        DraftMutationResultVO mutation = draftMutationService.confirmRefillCandidate(
            ctx.userId(), programId, tier, workspace);
        if (!mutation.isOk()) return error("confirm_failed", "Failed to confirm refill candidate.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", "confirm_refill");
        result.put("programId", programId);
        result.put("tier", tier);
        result.put("draftCount", mutation.getDraftCount());

        String json = JSON.toJSONString(result);
        V2ChatToolContext.markWriteExecuted(json);
        return json;
    }

    @Tool("Replace one draft school with another. Searches workspace first for the replacement, falls back to national DB if not found (for schools outside profile regions).")
    public String replaceDraftCandidate(long removeProgramId, long addProgramId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");

        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());

        // 1. 确定档位 —— 从被移除的候选所在档位推断
        DraftVO draft = draftService.getDraft(ctx.userId());
        String tier = findTierInDraft(draft, removeProgramId);
        if (tier == null) return error("not_in_draft", "removeProgramId not found in draft.");

        // 2. 先尝试从 Workspace 替换
        String wsTier = findTierInWorkspace(workspace, addProgramId);
        if (wsTier != null) {
            DraftMutationResultVO mutation = draftMutationService.replaceCandidate(
                ctx.userId(), removeProgramId, addProgramId, wsTier, workspace);
            if (mutation.isOk()) {
                return buildReplaceResult(removeProgramId, addProgramId, wsTier, mutation);
            }
        }

        // 3. Workspace 找不到替换目标 → 回退到 DB
        log.info("[DraftTool] replaceDraftCandidate addProgramId={} not in workspace, falling back to DB", addProgramId);
        CandidateCardVO candidate = buildCandidateFromDb(ctx, addProgramId);
        if (candidate == null) return error("not_found", "Replacement candidate not found in workspace or database.");

        // 先移除旧候选
        DraftMutationResultVO removeMutation = draftMutationService.removeCandidate(
            ctx.userId(), removeProgramId, workspace);
        if (!removeMutation.isOk()) return error("remove_failed", "Failed to remove existing candidate.");

        // 再添加新候选
        DraftMutationResultVO addMutation = draftMutationService.addCandidateDirect(
            ctx.userId(), candidate, tier);

        return buildReplaceResult(removeProgramId, addProgramId, tier, addMutation);
    }

    private String buildReplaceResult(long removeProgramId, long addProgramId, String tier, DraftMutationResultVO mutation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", "replace");
        result.put("removedProgramId", removeProgramId);
        result.put("addedProgramId", addProgramId);
        result.put("tier", tier);
        result.put("draftCount", mutation.getDraftCount());
        String json = JSON.toJSONString(result);
        V2ChatToolContext.markWriteExecuted(json);
        return json;
    }

    @Tool("Fill one draft tier to its target count from workspace candidates. Use when a tier has fewer schools than its target. Backend handles candidate selection; AI only chooses the tier. tier must be reach/steady/safe.")
    public String fillTier(String tier) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");
        if (tier == null || tier.isBlank()) return error("invalid_tier", "tier is required: reach/steady/safe");

        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());
        if (workspace == null) return error("no_workspace", "Workspace expired. Regenerate draft first.");

        DraftMutationResultVO mutation = draftMutationService.fillTier(ctx.userId(), tier, workspace);
        if (!mutation.isOk()) return error("fill_failed", "No available candidates in workspace for tier=" + tier);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", "fill_tier");
        result.put("tier", tier);
        result.put("draftCount", mutation.getDraftCount());

        String json = JSON.toJSONString(result);
        V2ChatToolContext.markWriteExecuted(json);
        log.info("[DraftTool] fillTier userId={} tier={} count={}", ctx.userId(), tier, mutation.getDraftCount());
        return json;
    }

    @Tool("Remove multiple schools from the draft at once. programIds must be from the draft context. Each removal triggers auto-refill or confirm-refill per tier rules.")
    public String batchRemoveDraftCandidates(List<Long> programIds) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");
        if (programIds == null || programIds.isEmpty()) return error("empty_ids", "programIds is empty.");

        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());
        DraftMutationResultVO mutation = draftMutationService.batchRemove(ctx.userId(), programIds, workspace);
        if (!mutation.isOk()) return error("none_in_draft", "None of the given programIds are in the current draft.");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", "batch_remove");
        result.put("draftCount", mutation.getDraftCount());

        String json = JSON.toJSONString(result);
        V2ChatToolContext.markWriteExecuted(json);
        log.info("[DraftTool] batchRemove userId={} count={}", ctx.userId(), programIds.size());
        return json;
    }

    void setDraftServiceForTest(IDraftService draftService) {
        this.draftService = draftService;
    }

    void setDraftMutationServiceForTest(IDraftMutationService service) {
        this.draftMutationService = service;
    }

    // ── helpers ──

    private String error(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("error", code);
        result.put("message", message);
        return JSON.toJSONString(result);
    }

    private CandidateCardVO findCandidate(DraftVO draft, long programId) {
        if (draft == null || draft.getTiers() == null) return null;
        for (TierCandidates tier : draft.getTiers()) {
            if (tier.getCandidates() == null) continue;
            for (CandidateCardVO c : tier.getCandidates()) {
                SchoolFact fact = c.getFact();
                if (fact != null && fact.getProgramId() != null && fact.getProgramId() == programId) return c;
            }
        }
        return null;
    }

    private String schoolName(CandidateCardVO c) {
        return c != null && c.getFact() != null ? c.getFact().getSchoolName() : "";
    }

    private CandidateWorkspaceVO loadWorkspace(Long userId) {
        String json = redisTemplate.opsForValue().get(WORKSPACE_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) return null;
        try { return JSON.parseObject(json, CandidateWorkspaceVO.class); }
        catch (Exception e) { return null; }
    }

    private String findTierInWorkspace(CandidateWorkspaceVO workspace, long programId) {
        if (workspace == null || workspace.getTiers() == null) return null;
        for (var tier : workspace.getTiers()) {
            if (tier.getCandidates() != null) {
                for (CandidateCardVO c : tier.getCandidates()) {
                    if (c.getFact().getProgramId() != null && c.getFact().getProgramId() == programId)
                        return tier.getLevel();
                }
            }
        }
        return null;
    }

    /** 在草稿中查找候选所在档位 */
    private String findTierInDraft(DraftVO draft, long programId) {
        if (draft == null || draft.getTiers() == null) return null;
        for (TierCandidates tier : draft.getTiers()) {
            if (tier.getCandidates() != null) {
                for (CandidateCardVO c : tier.getCandidates()) {
                    if (c.getFact() != null && c.getFact().getProgramId() != null
                        && c.getFact().getProgramId() == programId)
                        return tier.getLevel();
                }
            }
        }
        return null;
    }

    /** 从 DB 构建 CandidateCardVO（用于画像地区以外的学校） */
    private CandidateCardVO buildCandidateFromDb(V2ChatToolContext.Context ctx, long programId) {
        try {
            RowMap row = ctx.mapper().selectProgramForRecommendation(programId);
            if (row == null) return null;

            SchoolFact fact = SchoolFact.fromRow(row);

            // 读取预估分数
            int estimatedScore = 300;
            try {
                String draftJson = ctx.redis().opsForValue()
                    .get("ai:v2:draft:" + ctx.userId());
                if (draftJson != null && !draftJson.isBlank()) {
                    DraftVO draft = JSON.parseObject(draftJson, DraftVO.class);
                    if (draft.getProfileBasis() != null
                        && draft.getProfileBasis().getEstimatedScore() != null) {
                        estimatedScore = draft.getProfileBasis().getEstimatedScore();
                    }
                }
            } catch (Exception ignored) {}

            Integer avg = fact.getAvgAdmittedScore();
            int gap = avg != null ? estimatedScore - avg : 0;
            fact.setScoreGap(gap);
            fact.setGapLabel(gap >= 0 ? "+" + gap : String.valueOf(gap));

            int quota = fact.getUnifiedExamQuota() != null ? fact.getUnifiedExamQuota()
                : (fact.getPlanCount() != null ? fact.getPlanCount() : 0);
            fact.setCanBeSafe(SchoolFact.canBeSafe(quota, fact.getDataCompleteness(),
                fact.getAdmissionLow(), fact.getAdmissionHigh()));

            CandidateCardVO candidate = new CandidateCardVO();
            candidate.setFact(fact);
            candidate.setStatus("selected");
            return candidate;
        } catch (Exception e) {
            log.warn("[DraftTool] buildCandidateFromDb failed: {}", e.getMessage());
            return null;
        }
    }
}
