package com.ruoyi.postgrad.recommend.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.ruoyi.postgrad.recommend.tool.V2BoundChatTools;
import com.ruoyi.postgrad.recommend.tool.V2BoundDraftActionTools;
import com.ruoyi.postgrad.recommend.tool.V2ChatToolContext;
import com.ruoyi.postgrad.recommend.tool.V2ChatTools;
import com.ruoyi.postgrad.recommend.tool.V2DraftActionTools;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolRuntime;
import dev.langchain4j.service.tool.AiServiceTool;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AgentToolScopeTest {

    private final V2BoundChatTools reads = new V2BoundChatTools(
        mock(V2ChatTools.class), mock(V2ChatToolContext.Context.class), mock(AgentToolRuntime.class));
    private final V2BoundDraftActionTools writes = new V2BoundDraftActionTools(
        mock(V2DraftActionTools.class), mock(V2ChatToolContext.Context.class), mock(AgentToolRuntime.class));

    @Test
    void knowledgeRouteOnlyExposesKnowledgeSearch() {
        assertEquals(Set.of("searchKnowledgeBase"), names(AgentToolScope.select(
            AgentIntentRoute.KNOWLEDGE_RAG, reads, writes)));
    }

    @Test
    void programRouteDoesNotExposeDraftTools() {
        Set<String> names = names(AgentToolScope.select(
            AgentIntentRoute.PROGRAM_FACTS, reads, writes));

        assertTrue(names.containsAll(Set.of("searchPrograms", "getProgramDetail", "comparePrograms")));
        assertFalse(names.contains("getDraftContext"));
        assertFalse(names.contains("analyzeDraftHealth"));
        assertFalse(names.contains("removeDraftCandidate"));
    }

    @Test
    void draftWriteRouteIncludesReadContextAndConfirmedWriteTools() {
        Set<String> names = names(AgentToolScope.select(
            AgentIntentRoute.DRAFT_WRITE, reads, writes));

        assertTrue(names.contains("getDraftContext"));
        assertTrue(names.contains("removeDraftCandidate"));
        assertTrue(names.contains("replaceDraftCandidate"));
    }

    @Test
    void fallbackNeverExposesWriteTools() {
        Set<String> names = names(AgentToolScope.select(
            AgentIntentRoute.FALLBACK, reads, writes));

        assertTrue(names.contains("searchKnowledgeBase"));
        assertTrue(names.contains("searchPrograms"));
        assertFalse(names.contains("removeDraftCandidate"));
        assertFalse(names.contains("replaceDraftCandidate"));
        assertFalse(names.contains("batchReplaceDraftCandidates"));
    }

    private Set<String> names(List<AiServiceTool> tools) {
        return tools.stream().map(AiServiceTool::name).collect(Collectors.toSet());
    }
}
