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

    @Tool("Remove one school from the current report draft. Only one write action is allowed per turn, and programId must come from the current draft.")
    public String removeDraftCandidate(long programId) {
        return V2ChatToolContext.callWith(context, () -> delegate.removeDraftCandidate(programId));
    }
}
