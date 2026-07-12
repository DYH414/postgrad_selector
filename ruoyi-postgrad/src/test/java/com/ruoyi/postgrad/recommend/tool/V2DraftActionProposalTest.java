package com.ruoyi.postgrad.recommend.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.*;
import java.lang.reflect.Field;
import java.util.List;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class V2DraftActionProposalTest {
    @Test
    void rawWriteDelegateCannotBeRegisteredAsAnAiTool() {
        for (var method : V2DraftActionTools.class.getDeclaredMethods()) {
            assertNull(method.getAnnotation(Tool.class), method.getName());
        }
    }

    @Test
    void proposalTokenStaysOutOfModelVisibleToolResult() throws Exception {
        V2DraftActionTools tools = new V2DraftActionTools();
        IDraftService drafts = mock(IDraftService.class);
        AgentActionProposalService proposals = mock(AgentActionProposalService.class);
        DraftVersionService versions = mock(DraftVersionService.class);
        AgentActionAuditRecorder audit = mock(AgentActionAuditRecorder.class);
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(12L);
        fact.setSchoolName("深圳大学");
        fact.setProgramName("电子信息");
        CandidateCardVO candidate = CandidateCardVO.fromFact(fact);
        TierCandidates tier = new TierCandidates();
        tier.setCandidates(List.of(candidate));
        DraftVO draft = new DraftVO();
        draft.setTiers(List.of(tier));
        when(drafts.getDraft(7L)).thenReturn(draft);
        when(versions.hash(any())).thenReturn("hash");
        AgentActionProposal proposal = new AgentActionProposal(); proposal.setToken("secret-token");
        when(proposals.propose(eq(7L), eq("run-1"), any(), eq("remove"), anyMap(), anyString(), eq("hash")))
            .thenReturn(proposal);
        inject(tools, "draftService", drafts); inject(tools, "proposalService", proposals);
        inject(tools, "draftVersionService", versions); inject(tools, "actionAuditRecorder", audit);

        var context = V2ChatToolContext.init(7L, mock(StringRedisTemplate.class), mock(RecommendationMapper.class), "run-1");
        String modelVisible = V2ChatToolContext.callWith(context, () -> tools.removeDraftCandidate(12L));

        assertFalse(modelVisible.contains("secret-token"));
        assertTrue(context.lastActionResultJson().contains("secret-token"));
        assertTrue(context.lastActionResultJson().contains("深圳大学 - 电子信息"));
        assertFalse(context.lastActionResultJson().contains("programId"));
        verify(audit).record(eq("PROPOSED"), eq(7L), eq("run-1"), eq("remove"),
            contains("深圳大学 - 电子信息"), eq("hash"));
    }

    @Test
    void rejectsInvalidBatchProposalBeforePersistingToken() throws Exception {
        V2DraftActionTools tools = new V2DraftActionTools();
        AgentActionProposalService proposals = mock(AgentActionProposalService.class);
        inject(tools, "proposalService", proposals);
        var context = V2ChatToolContext.init(7L, mock(StringRedisTemplate.class),
            mock(RecommendationMapper.class), "run-1");

        String result = V2ChatToolContext.callWith(context,
            () -> tools.batchRemoveDraftCandidates(List.of(0L)));

        assertTrue(result.contains("invalid_action"));
        verifyNoInteractions(proposals);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = V2DraftActionTools.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
