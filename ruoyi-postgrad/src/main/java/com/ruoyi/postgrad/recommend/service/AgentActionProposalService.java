package com.ruoyi.postgrad.recommend.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AgentActionProposalService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "ai:v2:action:proposal:";
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
        "local v=redis.call('get',KEYS[1]); if not v then return nil end; "
            + "local o=cjson.decode(v); if tostring(o.userId)~=ARGV[1] then return nil end; "
            + "redis.call('del',KEYS[1]); return v",
        String.class);
    private final StringRedisTemplate redis;
    public AgentActionProposalService(StringRedisTemplate redis) { this.redis = redis; }
    public AgentActionProposal propose(Long userId, String runId, String action,
                                       Map<String, Object> arguments, String summary, String draftHash) {
        AgentActionProposal proposal = new AgentActionProposal();
        proposal.setToken(UUID.randomUUID().toString().replace("-", ""));
        proposal.setUserId(userId); proposal.setRunId(runId); proposal.setAction(action);
        proposal.setArguments(arguments); proposal.setSummary(summary);
        proposal.setDraftHash(draftHash);
        redis.opsForValue().set(PREFIX + proposal.getToken(), JSON.toJSONString(proposal), TTL);
        return proposal;
    }
    public AgentActionProposal consume(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) return null;
        String json = redis.execute(CONSUME_SCRIPT, java.util.List.of(PREFIX + token), String.valueOf(userId));
        if (json == null) return null;
        return JSON.parseObject(json, AgentActionProposal.class);
    }
}
