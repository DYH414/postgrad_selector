package com.ruoyi.postgrad.observe.llm.service;

import com.ruoyi.postgrad.observe.llm.domain.AiLlmCallTrace;
import com.ruoyi.postgrad.observe.llm.mapper.AiLlmCallTraceMapper;
import com.ruoyi.postgrad.observe.runtime.AiInvocationSnapshot;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AiLlmTracePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AiLlmTracePersistenceService.class);
    private final AiLlmCallTraceMapper mapper;
    private final TaskExecutor executor;

    public AiLlmTracePersistenceService(AiLlmCallTraceMapper mapper,
                                        @Qualifier("threadPoolTaskExecutor") TaskExecutor executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    public void persist(AiInvocationSnapshot snapshot) {
        AiLlmCallTrace record = AiLlmCallTrace.from(snapshot);
        try {
            executor.execute(() -> insertFailOpen(record));
        } catch (RejectedExecutionException ex) {
            log.warn("[LlmTrace] Persistence queue rejected traceId={}", record.getTraceId());
        }
    }

    private void insertFailOpen(AiLlmCallTrace record) {
        try {
            mapper.insert(record);
        } catch (Exception ex) {
            log.warn("[LlmTrace] Persistence failed traceId={} error={}",
                record.getTraceId(), ex.getClass().getSimpleName());
        }
    }
}
