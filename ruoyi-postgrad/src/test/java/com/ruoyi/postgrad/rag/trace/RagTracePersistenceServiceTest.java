package com.ruoyi.postgrad.rag.trace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ruoyi.postgrad.rag.mapper.RagRetrievalTraceMapper;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

class RagTracePersistenceServiceTest {

    @Test
    void persistsThroughExecutorBoundary() {
        RagRetrievalTraceMapper mapper = mock(RagRetrievalTraceMapper.class);
        RagTracePersistenceService service =
            new RagTracePersistenceService(mapper, new SyncTaskExecutor());

        service.persist(new RagTrace());

        verify(mapper).insert(any(RagRetrievalTrace.class));
    }

    @Test
    void mapperAndQueueFailuresRemainFailOpen() {
        RagRetrievalTraceMapper mapper = mock(RagRetrievalTraceMapper.class);
        doThrow(new IllegalStateException("database unavailable")).when(mapper).insert(any());
        RagTracePersistenceService mapperFailure =
            new RagTracePersistenceService(mapper, new SyncTaskExecutor());
        TaskExecutor rejectingExecutor = task -> {
            throw new RejectedExecutionException("queue full");
        };
        RagTracePersistenceService queueFailure =
            new RagTracePersistenceService(mapper, rejectingExecutor);

        assertDoesNotThrow(() -> mapperFailure.persist(new RagTrace()));
        assertDoesNotThrow(() -> queueFailure.persist(new RagTrace()));
    }
}
