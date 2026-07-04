package com.ruoyi.postgrad.rag.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 有界内存 RAG Trace 记录器 —— 保留最近 N 条 trace，倒序排列。
 * <p>不持久化，重启即清空。查询文本不落盘，保护用户隐私。</p>
 */
@Component
public class RagTraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(RagTraceRecorder.class);
    private static final int MAX_TRACES = 200;

    private final ConcurrentLinkedDeque<RagTrace> traces = new ConcurrentLinkedDeque<>();

    /**
     * 记录一条 trace。超过上限时淘汰最旧记录。
     */
    public void record(RagTrace trace) {
        if (trace == null) return;
        traces.addFirst(trace);
        while (traces.size() > MAX_TRACES) {
            traces.pollLast();
        }
        log.debug("[RagTrace] Recorded traceId={} operation={} outcome={} total={}/{}",
            trace.getTraceId(), trace.getOperationKey(), trace.getOutcome(), traces.size(), MAX_TRACES);
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
}
