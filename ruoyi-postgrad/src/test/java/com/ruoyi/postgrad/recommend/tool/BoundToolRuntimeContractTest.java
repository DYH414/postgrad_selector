package com.ruoyi.postgrad.recommend.tool;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import com.ruoyi.postgrad.recommend.domain.DraftReplacementRequest;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;
import org.junit.jupiter.api.Test;

class BoundToolRuntimeContractTest {

    @Test
    void everyBoundToolDelegatesThroughRuntime() {
        V2ChatTools readDelegate = mock(V2ChatTools.class);
        V2DraftActionTools writeDelegate = mock(V2DraftActionTools.class);
        AgentToolRuntime runtime = mock(AgentToolRuntime.class);
        V2ChatToolContext.Context context = V2ChatToolContext.init(7L, null, null, "run-1");
        V2BoundChatTools reads = new V2BoundChatTools(readDelegate, context, runtime);
        V2BoundDraftActionTools writes = new V2BoundDraftActionTools(writeDelegate, context, runtime);

        reads.getProgramDetail(1L);
        reads.getProgramEvidence(1L, "risk");
        reads.searchPrograms("keyword", "steady", "上海");
        reads.comparePrograms(1L, 2L);
        reads.searchKnowledgeBase("question");
        reads.getUserProfile();
        reads.analyzeDraftHealth();
        reads.getDraftContext();
        reads.getRefillCandidates("safe", 3);
        writes.removeDraftCandidate(1L);
        writes.addDraftCandidate(1L, "safe");
        writes.confirmRefillCandidate(1L);
        writes.replaceDraftCandidate(1L, 2L);
        writes.fillTier("safe");
        writes.batchRemoveDraftCandidates(List.of(1L));
        writes.batchReplaceDraftCandidates(List.<DraftReplacementRequest>of());

        verify(runtime, times(16)).execute(any(), any(), any(), any());
        verifyNoInteractions(readDelegate, writeDelegate);
    }
}
