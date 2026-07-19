package com.ruoyi.postgrad.rag.trace;

import com.ruoyi.postgrad.rag.mapper.RagRetrievalTraceMapper;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** Persists compact RAG traces outside the online retrieval thread. */
@Service
public class RagTracePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(RagTracePersistenceService.class);

    private final RagRetrievalTraceMapper mapper;
    private final TaskExecutor executor;

    public RagTracePersistenceService(RagRetrievalTraceMapper mapper,
                                      @Qualifier("threadPoolTaskExecutor") TaskExecutor executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    public void persist(RagTrace trace) {
        RagRetrievalTrace record = RagRetrievalTrace.from(trace);
        try {
            executor.execute(() -> insertFailOpen(record));
        } catch (RejectedExecutionException ex) {
            log.warn("[RagTrace] Persistence queue rejected traceId={}: {}",
                record.getTraceId(), ex.getMessage());
        }
    }

    private void insertFailOpen(RagRetrievalTrace record) {
        try {
            mapper.insert(record);
        } catch (Exception ex) {
            log.warn("[RagTrace] Persistence failed for traceId={}: {}",
                record.getTraceId(), ex.getMessage());
        }
    }
}
