package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.tool.V2DraftActionTools;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class AgentActionConfirmationServiceTest {
    @Test
    void rejectsExpiredOrForeignToken() {
        Fixture f = new Fixture();
        when(f.proposals.consume(7L, "bad")).thenReturn(null);
        assertEquals("invalid_or_expired_token", f.service.confirm(7L, "bad").get("error"));
        verifyNoInteractions(f.tools);
    }

    @Test
    void rejectsProposalWhenDraftChanged() {
        Fixture f = new Fixture();
        AgentActionProposal p = proposal("remove", Map.of("programId", 12L), "old");
        when(f.proposals.consume(7L, "token")).thenReturn(p);
        when(f.drafts.getDraft(7L)).thenReturn(new DraftVO());
        when(f.versions.hash(any())).thenReturn("new");
        assertEquals("stale_draft", f.service.confirm(7L, "token").get("error"));
        verifyNoInteractions(f.tools);
    }

    @Test
    void executesConsumedProposalExactlyOnce() {
        Fixture f = new Fixture();
        AgentActionProposal p = proposal("remove", Map.of("programId", 12L), "same");
        when(f.proposals.consume(7L, "token")).thenReturn(p);
        when(f.drafts.getDraft(7L)).thenReturn(new DraftVO());
        when(f.versions.hash(any())).thenReturn("same");
        when(f.tools.removeDraftCandidate(12L)).thenReturn("{\"ok\":true,\"action\":\"remove\"}");
        assertEquals(true, f.service.confirm(7L, "token").get("ok"));
        verify(f.tools, times(1)).removeDraftCandidate(12L);
    }

    private static AgentActionProposal proposal(String action, Map<String, Object> args, String hash) {
        AgentActionProposal p = new AgentActionProposal();
        p.setUserId(7L); p.setRunId("run-1"); p.setAction(action); p.setArguments(args);
        p.setDraftHash(hash); p.setSummary("summary"); return p;
    }

    private static class Fixture {
        final AgentActionProposalService proposals = mock(AgentActionProposalService.class);
        final DraftVersionService versions = mock(DraftVersionService.class);
        final IDraftService drafts = mock(IDraftService.class);
        final V2DraftActionTools tools = mock(V2DraftActionTools.class);
        final StringRedisTemplate redis = mock(StringRedisTemplate.class);
        final RecommendationMapper mapper = mock(RecommendationMapper.class);
        final AgentActionAuditRecorder audit = mock(AgentActionAuditRecorder.class);
        final AgentActionConfirmationService service = new AgentActionConfirmationService(
            proposals, versions, drafts, tools, redis, mapper, audit);
    }
}
