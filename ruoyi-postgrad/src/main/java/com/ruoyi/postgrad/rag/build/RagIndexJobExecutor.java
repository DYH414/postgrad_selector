package com.ruoyi.postgrad.rag.build;

import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Single-thread executor for async RAG index build tasks.
 *
 * <p>Wraps a {@link ThreadPoolTaskExecutor} with core=1, max=1, queue=1 so
 * only one build runs at a time. Submits via {@link #submit(String, int, int)}
 * which delegates to {@link RagIndexBuildService#executeBuild(String, int, int)}.</p>
 */
@Component
public class RagIndexJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(RagIndexJobExecutor.class);

    private final ThreadPoolTaskExecutor executor;
    private final RagIndexBuildService buildService;

    public RagIndexJobExecutor(ThreadPoolTaskExecutor executor, RagIndexBuildService buildService) {
        this.executor = executor;
        this.buildService = buildService;
    }

    /**
     * Submit a build task to run asynchronously on the single-worker thread pool.
     *
     * @param runId     the run identifier returned by {@link RagIndexBuildService#startBuild}
     * @param startYear inclusive start year for database evidence
     * @param endYear   inclusive end year for database evidence
     */
    public void submit(String runId, int startYear, int endYear) {
        log.info("[RagIndexJob] Submitting build runId={} (years={}-{})", runId, startYear, endYear);
        executor.execute(() -> buildService.executeBuild(runId, startYear, endYear));
    }

    /**
     * Returns {@code true} if a build is currently running.
     */
    public boolean isRunning() {
        return executor.getActiveCount() > 0;
    }
}
