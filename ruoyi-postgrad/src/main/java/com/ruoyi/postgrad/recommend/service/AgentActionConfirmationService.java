package com.ruoyi.postgrad.recommend.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2DraftActionTools;
import com.ruoyi.postgrad.observe.tool.domain.AiToolCallTrace;
import com.ruoyi.postgrad.observe.tool.service.AiToolTraceService;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentActionConfirmationService {
    private final AgentActionProposalService proposals;
    private final DraftVersionService versions;
    private final IDraftService drafts;
    private final V2DraftActionTools tools;
    private final StringRedisTemplate redis;
    private final RecommendationMapper mapper;
    private final AgentActionAuditRecorder audit;
    private final AiToolTraceService traces;

    public AgentActionConfirmationService(AgentActionProposalService proposals, DraftVersionService versions,
            IDraftService drafts, V2DraftActionTools tools, StringRedisTemplate redis,
            RecommendationMapper mapper, AgentActionAuditRecorder audit) {
        this(proposals, versions, drafts, tools, redis, mapper, audit, null);
    }

    @Autowired
    public AgentActionConfirmationService(AgentActionProposalService proposals, DraftVersionService versions,
            IDraftService drafts, V2DraftActionTools tools, StringRedisTemplate redis,
            RecommendationMapper mapper, AgentActionAuditRecorder audit, AiToolTraceService traces) {
        this.proposals = proposals; this.versions = versions; this.drafts = drafts; this.tools = tools;
        this.redis = redis; this.mapper = mapper; this.audit = audit; this.traces = traces;
    }

    public Map<String, Object> confirm(Long userId, String token) {
        AgentActionProposal proposal = proposals.consume(userId, token);
        if (proposal == null) {
            audit.record("REJECTED", userId, null, "unknown", "invalid_or_expired_token");
            return failure("invalid_or_expired_token", null, null);
        }
        String childCallId = UUID.randomUUID().toString();
        insertChildTrace(proposal, childCallId);
        if (!versions.hash(drafts.getDraft(userId)).equals(proposal.getDraftHash())) {
            completeChildTrace(childCallId, "CONFLICT", "BUSINESS_CONFLICT", false, "draft version changed");
            audit.record("CONFLICT", userId, proposal.getRunId(), proposal.getAction(), "草稿已变化");
            return failure("stale_draft", proposal.getRunId(), proposal.getAction());
        }
        try {
            V2ChatToolContext.Context context = V2ChatToolContext.confirmed(userId, redis, mapper, proposal.getRunId());
            String result = V2ChatToolContext.callWith(context, () -> execute(proposal));
            Map<String, Object> parsed = JSON.parseObject(result);
            parsed.put("runId", proposal.getRunId());
            parsed.put("confirmationRequired", false);
            String resultingHash = Boolean.TRUE.equals(parsed.get("ok"))
                ? versions.hash(drafts.getDraft(userId)) : null;
            audit.record(Boolean.TRUE.equals(parsed.get("ok")) ? "EXECUTED" : "REJECTED",
                userId, proposal.getRunId(), proposal.getAction(), proposal.getSummary(), resultingHash);
            completeChildTrace(childCallId, Boolean.TRUE.equals(parsed.get("ok")) ? "SUCCEEDED" : "REJECTED",
                Boolean.TRUE.equals(parsed.get("ok")) ? "OK" : "BUSINESS_REJECTED", false, proposal.getSummary());
            return parsed;
        } catch (Exception e) {
            completeChildTrace(childCallId, "FAILED", "TOOL_EXECUTION_FAILED", false, "confirmed action failed");
            audit.record("FAILED", userId, proposal.getRunId(), proposal.getAction(), e.getMessage());
            return failure("execution_failed", proposal.getRunId(), proposal.getAction());
        }
    }

    public Map<String, Object> cancel(Long userId, String token) {
        AgentActionProposal proposal = proposals.consume(userId, token);
        if (proposal == null) return failure("invalid_or_expired_token", null, null);
        if (traces != null && proposal.getParentCallId() != null) {
            try { traces.cancel(proposal.getParentCallId()); } catch (RuntimeException ignored) { }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("ok", true); result.put("runId", proposal.getRunId()); result.put("action", proposal.getAction());
        return result;
    }

    private void insertChildTrace(AgentActionProposal proposal, String callId) {
        if (traces == null) return;
        try {
            AiToolCallTrace trace = new AiToolCallTrace();
            trace.setCallId(callId); trace.setParentCallId(proposal.getParentCallId()); trace.setRunId(proposal.getRunId());
            trace.setUserId(proposal.getUserId()); trace.setOperationKey("confirmed-action"); trace.setToolName(proposal.getAction());
            trace.setToolCategory("WRITE"); trace.setBudgetGroup("WRITE"); trace.setStatus("STARTED");
            trace.setRetryable(false); trace.setConfirmationRequired(false); trace.setResultTokenEstimate(0);
            trace.setStartedAt(LocalDateTime.now()); traces.insert(trace);
        } catch (RuntimeException ignored) { }
    }

    private void completeChildTrace(String callId, String status, String code, boolean retryable, String summary) {
        if (traces == null) return;
        try { traces.complete(callId, status, code, retryable, summary, 0, 0L, null); } catch (RuntimeException ignored) { }
    }

    private String execute(AgentActionProposal p) {
        Map<String, Object> a = p.getArguments();
        return switch (p.getAction()) {
            case "remove" -> tools.removeDraftCandidate(number(a, "programId"));
            case "add" -> tools.addDraftCandidate(number(a, "programId"), string(a, "tier"));
            case "confirm_refill" -> tools.confirmRefillCandidate(number(a, "programId"));
            case "replace" -> tools.replaceDraftCandidate(number(a, "removeProgramId"), number(a, "addProgramId"));
            case "fill_tier" -> tools.fillTier(string(a, "tier"));
            case "batch_remove" -> tools.batchRemoveDraftCandidates(JSON.parseArray(JSON.toJSONString(a.get("programIds")), Long.class));
            case "batch_replace" -> tools.batchReplaceDraftCandidates(
                JSON.parseArray(JSON.toJSONString(a.get("replacements")), DraftReplacementRequest.class));
            default -> throw new IllegalArgumentException("Unsupported action: " + p.getAction());
        };
    }

    private long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }
    private String string(Map<String, Object> values, String key) { return (String) values.get(key); }
    private Map<String, Object> failure(String error, String runId, String action) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("ok", false); result.put("error", error);
        if (runId != null) result.put("runId", runId);
        if (action != null) result.put("action", action);
        return result;
    }
}
