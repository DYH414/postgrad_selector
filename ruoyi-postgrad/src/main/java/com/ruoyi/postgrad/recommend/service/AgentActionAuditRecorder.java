package com.ruoyi.postgrad.recommend.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentActionAuditRecorder {
    private static final Logger log = LoggerFactory.getLogger(AgentActionAuditRecorder.class);
    private static final int LIMIT = 200;
    private final ConcurrentLinkedDeque<Map<String, Object>> records = new ConcurrentLinkedDeque<>();
    public void record(String outcome, Long userId, String runId, String action, String summary) {
        record(outcome, userId, runId, action, summary, null);
    }
    public void record(String outcome, Long userId, String runId, String action, String summary, String draftHash) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("occurredAt", Instant.now().toString()); row.put("outcome", outcome);
        row.put("userId", userId); row.put("runId", runId); row.put("action", action); row.put("summary", summary);
        if (draftHash != null) row.put("draftHash", draftHash);
        records.addFirst(row); while (records.size() > LIMIT) records.pollLast();
        log.info("[AgentAction] outcome={} userId={} runId={} action={}", outcome, userId, runId, action);
    }
    public java.util.List<Map<String, Object>> recent() { return java.util.List.copyOf(records); }
}
