package com.ruoyi.postgrad.recommend.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AgentActionProposalService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "ai:v2:action:proposal:";
    private static final String USER_PREFIX = PREFIX + "user:";
    private static final DefaultRedisScript<String> PROPOSE_SCRIPT = new DefaultRedisScript<>(
        "redis.call('set',KEYS[1],ARGV[1],'EX',ARGV[3]); "
            + "redis.call('set',KEYS[2],ARGV[2],'EX',ARGV[3]); return ARGV[2]",
        String.class);
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
        "local v=redis.call('get',KEYS[1]); if not v then return nil end; "
            + "local o=cjson.decode(v); if tostring(o.userId)~=ARGV[1] then return nil end; "
            + "redis.call('del',KEYS[1]); "
            + "local current=redis.call('get',KEYS[2]); "
            + "if current==ARGV[2] then redis.call('del',KEYS[2]) end; return v",
        String.class);
    private static final DefaultRedisScript<String> PENDING_SCRIPT = new DefaultRedisScript<>(
        "local token=redis.call('get',KEYS[1]); if not token then return nil end; "
            + "local v=redis.call('get',ARGV[2]..token); "
            + "if not v then redis.call('del',KEYS[1]); return nil end; "
            + "local o=cjson.decode(v); if tostring(o.userId)~=ARGV[1] then "
            + "redis.call('del',KEYS[1]); return nil end; return v",
        String.class);
    private final StringRedisTemplate redis;
    public AgentActionProposalService(StringRedisTemplate redis) { this.redis = redis; }
    public AgentActionProposal propose(Long userId, String runId, String parentCallId, String action,
                                       Map<String, Object> arguments, String summary, String draftHash) {
        AgentActionProposal proposal = new AgentActionProposal();
        proposal.setToken(UUID.randomUUID().toString().replace("-", ""));
        proposal.setUserId(userId); proposal.setRunId(runId); proposal.setParentCallId(parentCallId); proposal.setAction(action);
        proposal.setArguments(arguments); proposal.setSummary(summary);
        proposal.setDraftHash(draftHash);
        proposal.setExpiresAt(LocalDateTime.now().plus(TTL));
        redis.execute(PROPOSE_SCRIPT,
            java.util.List.of(PREFIX + proposal.getToken(), USER_PREFIX + userId),
            JSON.toJSONString(proposal), proposal.getToken(), String.valueOf(TTL.toSeconds()));
        return proposal;
    }

    public AgentActionProposal pending(Long userId) {
        if (userId == null) return null;
        String json = redis.execute(PENDING_SCRIPT, java.util.List.of(USER_PREFIX + userId),
            String.valueOf(userId), PREFIX);
        return json == null ? null : JSON.parseObject(json, AgentActionProposal.class);
    }

    public AgentActionProposal consume(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) return null;
        String json = redis.execute(CONSUME_SCRIPT,
            java.util.List.of(PREFIX + token, USER_PREFIX + userId), String.valueOf(userId), token);
        if (json == null) return null;
        return JSON.parseObject(json, AgentActionProposal.class);
    }
}
