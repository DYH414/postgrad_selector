package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AgentActionProposalServiceTest {
    @Test
    void createsProposalWithOwnerRunAndDraftVersion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AgentActionProposalService service = new AgentActionProposalService(redis);
        AgentActionProposal result = service.propose(7L, "run-1", "call-parent", "remove",
            Map.of("programId", 12L), "移除候选", "hash-1");
        assertEquals(7L, result.getUserId());
        assertEquals("run-1", result.getRunId());
        assertEquals("call-parent", result.getParentCallId());
        assertNotNull(result.getExpiresAt());
        assertEquals("hash-1", result.getDraftHash());
        assertNotNull(result.getToken());
        verify(values).set(startsWith("ai:v2:action:proposal:"), anyString(), any());
    }

    @Test
    void consumesOnlyValueReturnedByOwnerCheckingScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AgentActionProposal expected = new AgentActionProposal();
        expected.setUserId(7L); expected.setToken("token");
        when(redis.execute(any(), anyList(), eq("7"))).thenReturn(JSON.toJSONString(expected));
        AgentActionProposalService service = new AgentActionProposalService(redis);
        assertEquals(7L, service.consume(7L, "token").getUserId());
        when(redis.execute(any(), anyList(), eq("8"))).thenReturn(null);
        assertNull(service.consume(8L, "token"));
    }
}
