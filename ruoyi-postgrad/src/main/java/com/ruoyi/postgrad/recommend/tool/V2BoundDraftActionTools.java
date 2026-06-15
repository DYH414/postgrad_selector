package com.ruoyi.postgrad.recommend.tool;

import dev.langchain4j.agent.tool.Tool;

/**
 * Per-chat wrapper for draft write tools.
 */
public class V2BoundDraftActionTools {

    private final V2DraftActionTools delegate;
    private final V2ChatToolContext.Context context;

    public V2BoundDraftActionTools(V2DraftActionTools delegate, V2ChatToolContext.Context context) {
        this.delegate = delegate;
        this.context = context;
    }

    @Tool("Remove one school from the current report draft. Returns refill info: auto-refill may add a replacement, confirm-refill returns candidates for the user to choose from.")
    public String removeDraftCandidate(long programId) {
        return V2ChatToolContext.callWith(context, () -> delegate.removeDraftCandidate(programId));
    }

    @Tool("Add a candidate from the workspace to the draft. Requires programId and tier (reach/steady/safe).")
    public String addDraftCandidate(long programId, String tier) {
        return V2ChatToolContext.callWith(context, () -> delegate.addDraftCandidate(programId, tier));
    }

    @Tool("Confirm a refill candidate chosen by the user. Use after user picks from the confirm-refill list.")
    public String confirmRefillCandidate(long programId) {
        return V2ChatToolContext.callWith(context, () -> delegate.confirmRefillCandidate(programId));
    }

    @Tool("Replace one draft school with another from the workspace. Removes and adds in the same tier.")
    public String replaceDraftCandidate(long removeProgramId, long addProgramId) {
        return V2ChatToolContext.callWith(context, () -> delegate.replaceDraftCandidate(removeProgramId, addProgramId));
    }
}
