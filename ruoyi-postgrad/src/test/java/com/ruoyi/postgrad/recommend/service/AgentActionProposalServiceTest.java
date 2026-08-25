package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import java.util.Map;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class AgentActionProposalServiceTest {
    @Test
    void createsProposalWithOwnerRunAndDraftVersion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentActionProposalService service = new AgentActionProposalService(redis);
        AgentActionProposal result = service.propose(7L, "run-1", "call-parent", "remove",
            Map.of("programId", 12L), "移除候选", "hash-1");
        assertEquals(7L, result.getUserId());
        assertEquals("run-1", result.getRunId());
        assertEquals("call-parent", result.getParentCallId());
        assertNotNull(result.getExpiresAt());
        assertEquals("hash-1", result.getDraftHash());
        assertNotNull(result.getToken());
        verify(redis).execute(any(), eq(List.of(
            "ai:v2:action:proposal:" + result.getToken(),
            "ai:v2:action:proposal:user:7")), anyString(), eq(result.getToken()), eq("300"));
    }

    @Test
    void proposeWritesProposalAndUserPointerAtomically() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentActionProposalService service = new AgentActionProposalService(redis);

        AgentActionProposal result = service.propose(7L, "run-1", "call-parent", "remove",
            Map.of("programId", 12L), "移除候选", "hash-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(), keys.capture(), anyString(), eq(result.getToken()), anyString());
        assertEquals(List.of(
            "ai:v2:action:proposal:" + result.getToken(),
            "ai:v2:action:proposal:user:7"), keys.getValue());
    }

    @Test
    void consumesOnlyValueReturnedByOwnerCheckingScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentActionProposal expected = new AgentActionProposal();
        expected.setUserId(7L); expected.setToken("token");
        when(redis.execute(any(), anyList(), eq("7"), eq("token"))).thenReturn(JSON.toJSONString(expected));
        AgentActionProposalService service = new AgentActionProposalService(redis);
        assertEquals(7L, service.consume(7L, "token").getUserId());
        when(redis.execute(any(), anyList(), eq("8"), eq("token"))).thenReturn(null);
        assertNull(service.consume(8L, "token"));
    }

    @Test
    void returnsOnlyCurrentUnexpiredProposalOwnedByUser() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentActionProposal expected = new AgentActionProposal();
        expected.setUserId(7L); expected.setToken("token");
        when(redis.execute(any(), eq(List.of("ai:v2:action:proposal:user:7")),
            eq("7"), eq("ai:v2:action:proposal:")))
            .thenReturn(JSON.toJSONString(expected));

        AgentActionProposalService service = new AgentActionProposalService(redis);

        assertEquals("token", service.pending(7L).getToken());
        assertNull(service.pending(8L));
        assertNull(service.pending(null));
    }

    @Test
    void consumeAtomicallyDeletesProposalAndMatchingUserIndex() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentActionProposal expected = new AgentActionProposal();
        expected.setUserId(7L); expected.setToken("token");
        when(redis.execute(any(), anyList(), eq("7"), eq("token")))
            .thenReturn(JSON.toJSONString(expected));
        AgentActionProposalService service = new AgentActionProposalService(redis);

        assertNotNull(service.consume(7L, "token"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(), keys.capture(), eq("7"), eq("token"));
        assertEquals(List.of("ai:v2:action:proposal:token", "ai:v2:action:proposal:user:7"), keys.getValue());
    }
}
