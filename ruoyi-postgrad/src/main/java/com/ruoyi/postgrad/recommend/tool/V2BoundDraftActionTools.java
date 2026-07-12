package com.ruoyi.postgrad.recommend.tool;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;
import dev.langchain4j.agent.tool.Tool;

/**
 * Per-chat wrapper for draft write tools.
 */
public class V2BoundDraftActionTools {

    private final V2DraftActionTools delegate;
    private final V2ChatToolContext.Context context;
    private final AgentToolRuntime runtime;

    public V2BoundDraftActionTools(V2DraftActionTools delegate, V2ChatToolContext.Context context,
        AgentToolRuntime runtime) {
        this.delegate = delegate;
        this.context = context;
        this.runtime = runtime;
    }

    @Tool("Remove one school from the current report draft. Returns refill info: auto-refill may add a replacement, confirm-refill returns candidates for the user to choose from.")
    public String removeDraftCandidate(long programId) {
        return runtime.execute(context, "removeDraftCandidate", args("programId", programId),
            () -> delegate.removeDraftCandidate(programId));
    }

    @Tool("Add a candidate from the workspace to the draft. Requires programId and tier (reach/steady/safe).")
    public String addDraftCandidate(long programId, String tier) {
        return runtime.execute(context, "addDraftCandidate", args("programId", programId, "tier", tier),
            () -> delegate.addDraftCandidate(programId, tier));
    }

    @Tool("Confirm a refill candidate chosen by the user. Use after user picks from the confirm-refill list.")
    public String confirmRefillCandidate(long programId) {
        return runtime.execute(context, "confirmRefillCandidate", args("programId", programId),
            () -> delegate.confirmRefillCandidate(programId));
    }

    @Tool("Replace one draft school with another from the workspace. Removes and adds in the same tier.")
    public String replaceDraftCandidate(long removeProgramId, long addProgramId) {
        return runtime.execute(context, "replaceDraftCandidate",
            args("removeProgramId", removeProgramId, "addProgramId", addProgramId),
            () -> delegate.replaceDraftCandidate(removeProgramId, addProgramId));
    }

    @Tool("Fill one draft tier to its target count from workspace candidates. Use when a tier has fewer schools than its target. Backend selects candidates; AI only chooses the tier (reach/steady/safe).")
    public String fillTier(String tier) {
        return runtime.execute(context, "fillTier", args("tier", tier), () -> delegate.fillTier(tier));
    }

    @Tool("Remove multiple schools from the draft at once. programIds must come from the draft context. Each removal triggers refill.")
    public String batchRemoveDraftCandidates(List<Long> programIds) {
        return runtime.execute(context, "batchRemoveDraftCandidates",
            args("programIdsCount", programIds == null ? null : programIds.size()),
            () -> delegate.batchRemoveDraftCandidates(programIds));
    }

    @Tool("Batch replace multiple schools in the draft. Each entry maps a removeProgramId (must be in draft) to an addProgramId (must be in workspace or searchable in DB). At least one success triggers draftChanged refresh. Returns per-item ok/error details.")
    public String batchReplaceDraftCandidates(List<DraftReplacementRequest> replacements) {
        return runtime.execute(context, "batchReplaceDraftCandidates",
            args("replacementsCount", replacements == null ? null : replacements.size()),
            () -> delegate.batchReplaceDraftCandidates(replacements));
    }

    private static Map<String, Object> args(Object... values) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            arguments.put(String.valueOf(values[index]), values[index + 1]);
        }
        return arguments;
    }
}
