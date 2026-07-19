package com.ruoyi.postgrad.rag.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import com.ruoyi.postgrad.rag.mapper.RagRetrievalTraceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 有界内存 RAG Trace 记录器 —— 保留最近 N 条 trace，倒序排列。
 * <p>内存记录用于兼容现有查询，数据库写入为 fail-open 的 best effort。</p>
 */
@Component
public class RagTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(RagTraceRecorder.class);
    private static final int MAX_TRACES = 200;

    private final ConcurrentLinkedDeque<RagTrace> traces = new ConcurrentLinkedDeque<>();
    private final RagRetrievalTraceMapper mapper;
    private final RagTracePersistenceService persistenceService;

    public RagTraceRecorder() {
        this.mapper = null;
        this.persistenceService = null;
    }

    public RagTraceRecorder(RagRetrievalTraceMapper mapper) {
        this.mapper = mapper;
        this.persistenceService = null;
    }

    @Autowired
    public RagTraceRecorder(RagRetrievalTraceMapper mapper,
                            RagTracePersistenceService persistenceService) {
        this.mapper = mapper;
        this.persistenceService = persistenceService;
    }

    /**
     * 记录一条 trace。超过上限时淘汰最旧记录。
     */
    public void record(RagTrace trace) {
        if (trace == null) return;
        traces.addFirst(trace);
        while (traces.size() > MAX_TRACES) {
            traces.pollLast();
        }
        persist(trace);
        log.debug("[RagTrace] Recorded traceId={} operation={} outcome={} total={}/{}",
            trace.getTraceId(), trace.getOperationKey(), trace.getOutcome(), traces.size(), MAX_TRACES);
    }

    private void persist(RagTrace trace) {
        if (persistenceService != null) {
            persistenceService.persist(trace);
            return;
        }
        if (mapper == null) return;
        try {
            mapper.insert(RagRetrievalTrace.from(trace));
        } catch (Exception ex) {
            log.warn("[RagTrace] Persistence failed for traceId={}: {}",
                trace.getTraceId(), ex.getMessage());
        }
    }

    /**
     * 返回最近 N 条 trace（倒序，最新的在前）。
     */
    public List<RagTrace> listRecent(int limit) {
        int n = Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_TRACES));
        List<RagTrace> result = new ArrayList<>(n);
        int i = 0;
        for (RagTrace t : traces) {
            if (i >= n) break;
            result.add(t);
            i++;
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        return traces.size();
    }

    public List<RagRetrievalTrace> listPersistedRecent(int limit) {
        if (mapper == null) return List.of();
        try {
            return mapper.selectRecent(clampLimit(limit));
        } catch (Exception ex) {
            log.warn("[RagTrace] Persistent recent query failed: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<RagRetrievalTrace> listPersistedByRunId(String runId, int limit) {
        if (mapper == null || runId == null || runId.isBlank()) return List.of();
        try {
            return mapper.selectByRunId(runId, clampLimit(limit));
        } catch (Exception ex) {
            log.warn("[RagTrace] Persistent run query failed for runId={}: {}", runId, ex.getMessage());
            return List.of();
        }
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200));
    }
}
