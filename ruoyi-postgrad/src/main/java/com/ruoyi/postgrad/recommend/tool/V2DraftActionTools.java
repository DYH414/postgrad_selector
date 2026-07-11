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
import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.service.IDraftMutationService;
import com.ruoyi.postgrad.recommend.service.IDraftService;
import com.ruoyi.postgrad.recommend.service.AgentActionProposalService;
import com.ruoyi.postgrad.recommend.service.AgentActionAuditRecorder;
import com.ruoyi.postgrad.recommend.service.DraftVersionService;

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

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Autowired
    private AgentActionProposalService proposalService;

    @Autowired
    private DraftVersionService draftVersionService;

    @Autowired
    private AgentActionAuditRecorder actionAuditRecorder;

    public String removeDraftCandidate(long programId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        String proposal = proposeIfNeeded(ctx, "remove", args("programId", programId), "确认从草稿中移除该候选");
        if (proposal != null) return proposal;
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

    public String addDraftCandidate(long programId, String tier) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        String proposal = proposeIfNeeded(ctx, "add", args("programId", programId, "tier", tier), "确认把该候选加入草稿");
        if (proposal != null) return proposal;
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

    public String confirmRefillCandidate(long programId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        String proposal = proposeIfNeeded(ctx, "confirm_refill", args("programId", programId), "确认补充该候选");
        if (proposal != null) return proposal;
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");

        // 1. 先在 workspace 中找 tier
        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());
        String tier = findTierInWorkspace(workspace, programId);

        // 2. workspace 找不到 → 从 draft 中找（DB 回退候选已在草稿中）
        if (tier == null) {
            DraftVO draft = draftService.getDraft(ctx.userId());
            tier = findTierInDraft(draft, programId);
        }
        if (tier == null) return error("not_found", "Candidate not found in workspace or draft tiers.");

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

    public String replaceDraftCandidate(long removeProgramId, long addProgramId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        String proposal = proposeIfNeeded(ctx, "replace", args("removeProgramId", removeProgramId, "addProgramId", addProgramId), "确认执行候选替换");
        if (proposal != null) return proposal;
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

        // 直接移除旧候选（不触发 refill，避免双重添加 H2 bug）
        DraftMutationResultVO removeMutation = draftMutationService.removeFromDraftDirect(
            ctx.userId(), removeProgramId);

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

    public String fillTier(String tier) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        String proposal = proposeIfNeeded(ctx, "fill_tier", args("tier", tier), "确认填充该档位");
        if (proposal != null) return proposal;
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

    public String batchRemoveDraftCandidates(List<Long> programIds) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        String proposal = proposeIfNeeded(ctx, "batch_remove", args("programIds", programIds), "确认批量移除所选候选");
        if (proposal != null) return proposal;
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

    public String batchReplaceDraftCandidates(java.util.List<DraftReplacementRequest> replacements) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) return error("no_tool_context", "Tool context is not initialized.");
        String proposal = proposeIfNeeded(ctx, "batch_replace", args("replacements", replacements), "确认批量替换所选候选");
        if (proposal != null) return proposal;
        if (V2ChatToolContext.writeExecuted()) return error("write_already_executed", "Write already executed this turn.");
        if (replacements == null || replacements.isEmpty()) return error("empty_replacements", "replacements is empty.");

        CandidateWorkspaceVO workspace = loadWorkspace(ctx.userId());
        java.util.Map<String, Object> result = draftMutationService.batchReplace(
            ctx.userId(), replacements, workspace,
            pid -> buildCandidateFromDb(pid, ctx.userId())
        );

        if (Boolean.TRUE.equals(result.get("ok"))) {
            V2ChatToolContext.markWriteExecuted(com.alibaba.fastjson2.JSON.toJSONString(result));
        }

        log.info("[DraftTool] batchReplace userId={} requested={} replaced={} failed={}",
            ctx.userId(), result.get("requested"), result.get("replaced"), result.get("failed"));
        return com.alibaba.fastjson2.JSON.toJSONString(result);
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

    private String proposeIfNeeded(V2ChatToolContext.Context ctx, String action,
                                   Map<String, Object> arguments, String summary) {
        if (ctx.confirmed()) return null;
        String validationError = validateProposal(action, arguments);
        if (validationError != null) return error("invalid_action", validationError);
        DraftVO draft = draftService.getDraft(ctx.userId());
        String proposalSummary = buildProposalSummary(ctx, draft, action, arguments, summary);
        String draftHash = draftVersionService.hash(draft);
        var proposal = proposalService.propose(ctx.userId(), ctx.runId(), action, arguments,
            proposalSummary, draftHash);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("confirmationRequired", true);
        result.put("confirmationToken", proposal.getToken());
        result.put("summary", proposalSummary);
        result.put("action", action);
        result.put("runId", ctx.runId());
        String json = JSON.toJSONString(result);
        V2ChatToolContext.markProposal(json);
        actionAuditRecorder.record("PROPOSED", ctx.userId(), ctx.runId(), action, proposalSummary, draftHash);
        Map<String, Object> modelResult = new LinkedHashMap<>(result);
        modelResult.remove("confirmationToken");
        return JSON.toJSONString(modelResult);
    }

    private String validateProposal(String action, Map<String, Object> arguments) {
        if (List.of("remove", "add", "confirm_refill").contains(action)
            && !positiveNumber(arguments.get("programId"))) return "programId must be positive";
        if ("replace".equals(action)
            && (!positiveNumber(arguments.get("removeProgramId")) || !positiveNumber(arguments.get("addProgramId")))) {
            return "replacement program ids must be positive";
        }
        if (("add".equals(action) || "fill_tier".equals(action))) {
            Object tier = arguments.get("tier");
            if (!(tier instanceof String value) || !List.of("reach", "steady", "safe").contains(value)) {
                return "tier must be reach/steady/safe";
            }
        }
        if ("batch_remove".equals(action)) {
            if (!(arguments.get("programIds") instanceof List<?> ids) || ids.isEmpty()
                || ids.stream().anyMatch(id -> !positiveNumber(id))) {
                return "programIds must contain positive ids";
            }
        }
        if ("batch_replace".equals(action)) {
            if (!(arguments.get("replacements") instanceof List<?> rows) || rows.isEmpty()
                || rows.stream().anyMatch(row -> !(row instanceof DraftReplacementRequest replacement)
                    || !positiveNumber(replacement.getRemoveProgramId())
                    || !positiveNumber(replacement.getAddProgramId()))) {
                return "replacements must contain positive program ids";
            }
        }
        return null;
    }

    private boolean positiveNumber(Object value) {
        return value instanceof Number number && number.longValue() > 0;
    }

    private String buildProposalSummary(V2ChatToolContext.Context ctx, DraftVO draft, String action,
                                        Map<String, Object> arguments, String fallback) {
        return switch (action) {
            case "remove" -> "从草稿移除：" + candidateLabel(ctx, draft, numberArgument(arguments, "programId"));
            case "add" -> "加入" + tierLabel(String.valueOf(arguments.get("tier"))) + "："
                + candidateLabel(ctx, draft, numberArgument(arguments, "programId"));
            case "confirm_refill" -> "确认补充：" + candidateLabel(ctx, draft, numberArgument(arguments, "programId"));
            case "replace" -> "替换候选："
                + candidateLabel(ctx, draft, numberArgument(arguments, "removeProgramId")) + " -> "
                + candidateLabel(ctx, draft, numberArgument(arguments, "addProgramId"));
            case "fill_tier" -> "填充" + tierLabel(String.valueOf(arguments.get("tier"))) + "至目标数量";
            case "batch_remove" -> "批量移除：" + candidateLabels(ctx, draft, arguments.get("programIds"));
            case "batch_replace" -> "批量替换 " + ((List<?>) arguments.get("replacements")).size() + " 组候选";
            default -> fallback;
        };
    }

    private long numberArgument(Map<String, Object> arguments, String key) {
        return ((Number) arguments.get(key)).longValue();
    }

    private String candidateLabels(V2ChatToolContext.Context ctx, DraftVO draft, Object rawIds) {
        List<?> ids = (List<?>) rawIds;
        String joined = ids.stream().limit(4)
            .map(id -> candidateLabel(ctx, draft, ((Number) id).longValue()))
            .collect(java.util.stream.Collectors.joining("、"));
        return ids.size() > 4 ? joined + "等 " + ids.size() + " 项" : joined;
    }

    private String candidateLabel(V2ChatToolContext.Context ctx, DraftVO draft, long programId) {
        CandidateCardVO candidate = findCandidate(draft, programId);
        if (candidate == null) candidate = findCandidate(loadWorkspace(ctx.userId()), programId);
        if (candidate == null) {
            try {
                RowMap row = ctx.mapper().selectProgramForRecommendation(programId);
                if (row != null) {
                    SchoolFact fact = SchoolFact.fromRow(row);
                    return factLabel(fact);
                }
            } catch (Exception e) {
                log.debug("[DraftTool] Unable to resolve proposal label: {}", e.getMessage());
            }
        }
        return candidate != null ? factLabel(candidate.getFact()) : "目标候选";
    }

    private CandidateCardVO findCandidate(CandidateWorkspaceVO workspace, long programId) {
        if (workspace == null || workspace.getTiers() == null) return null;
        for (var tier : workspace.getTiers()) {
            if (tier.getCandidates() == null) continue;
            for (CandidateCardVO candidate : tier.getCandidates()) {
                if (candidate.getFact() != null && candidate.getFact().getProgramId() != null
                    && candidate.getFact().getProgramId() == programId) return candidate;
            }
        }
        return null;
    }

    private String factLabel(SchoolFact fact) {
        if (fact == null) return "目标候选";
        String school = fact.getSchoolName() == null ? "目标院校" : fact.getSchoolName();
        String program = fact.getProgramName();
        return program == null || program.isBlank() ? school : school + " - " + program;
    }

    private String tierLabel(String tier) {
        return switch (tier) {
            case "reach" -> "冲刺档";
            case "steady" -> "稳妥档";
            case "safe" -> "保底档";
            default -> "目标档位";
        };
    }

    private Map<String, Object> args(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
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

    /** 从 DB 构建 CandidateCardVO（batchReplace 回退路径，使用注入依赖） */
    private CandidateCardVO buildCandidateFromDb(long programId, Long userId) {
        try {
            RowMap row = recommendationMapper.selectProgramForRecommendation(programId);
            if (row == null) return null;

            SchoolFact fact = SchoolFact.fromRow(row);

            int estimatedScore = 300;
            try {
                String draftJson = redisTemplate.opsForValue()
                    .get("ai:v2:draft:" + userId);
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
            log.warn("[DraftTool] buildCandidateFromDb(pid, userId) failed: {}", e.getMessage());
            return null;
        }
    }
}
