package com.ruoyi.postgrad.rag.build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Single-thread executor for async RAG index build tasks.
 *
 * <p>Wraps the dedicated RAG executor and runs tasks supplied by the build
 * coordinator. It deliberately has no dependency back to the coordinator.</p>
 */
@Component
public class RagIndexJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(RagIndexJobExecutor.class);

    private final ThreadPoolTaskExecutor executor;

    public RagIndexJobExecutor(@Qualifier("ragBuildExecutor") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    /**
     * Submit a build task to run asynchronously on the single-worker thread pool.
     *
     * @param runId the run identifier used for diagnostics
     * @param task  the build task to execute
     */
    public void submit(String runId, Runnable task) {
        log.info("[RagIndexJob] Submitting build runId={}", runId);
        executor.execute(task);
    }

    /**
     * Returns {@code true} if a build is currently running.
     */
    public boolean isRunning() {
        return executor.getActiveCount() > 0;
    }
}
