package com.ruoyi.postgrad.rag.status;

import com.ruoyi.postgrad.rag.RagStatus;
import com.ruoyi.postgrad.rag.domain.RagIndexRun;
import com.ruoyi.postgrad.rag.mapper.RagIndexRunMapper;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Reads merged RAG index status from both MySQL (index run) and Qdrant (live alias).
 *
 * <p>Queries the persisted {@code rag_index_run} table for the latest build state
 * and the Qdrant alias API for the live collection.</p>
 */
@Service
public class RagIndexStatusService {

    /** Alias name for the active RAG collection in Qdrant. */
    static final String ACTIVE_ALIAS = "postgrad_rag_active";

    private static final List<String> INTERRUPTIBLE_STATUSES =
        Arrays.asList("PENDING", "RUNNING", "VALIDATING", "SWITCHING");

    private final RagIndexRunMapper indexRunMapper;
    private final RagCollectionManager collectionManager;

    public RagIndexStatusService(RagIndexRunMapper indexRunMapper,
                                  RagCollectionManager collectionManager) {
        this.indexRunMapper = indexRunMapper;
        this.collectionManager = collectionManager;
    }

    /**
     * Returns the merged RAG index status.
     *
     * <ol>
     *   <li>Resolves the {@code postgrad_rag_active} alias from Qdrant</li>
     *   <li>Reads the latest SUCCEEDED run from MySQL (if any)</li>
     *   <li>Gets the point count from the active collection</li>
     *   <li>Falls back to latest run state when no active build exists</li>
     * </ol>
     */
    public RagStatus getStatus() {
        RagStatus status = new RagStatus();
        status.setBackend("qdrant");
        status.setEnabled(true);
        status.setReachable(true);

        // 1. Resolve live alias from Qdrant
        String activeCollection = collectionManager.resolveAlias(ACTIVE_ALIAS);
        status.setActiveCollection(activeCollection);
        status.setCollectionName(activeCollection);

        // 2. Read latest SUCCEEDED batch from MySQL
        RagIndexRun activeRun = indexRunMapper.selectActive();

        if (activeRun != null && activeCollection != null) {
            status.setIndexed(true);
            int docCount = (activeRun.getGeneralDocumentCount() != null
                    ? activeRun.getGeneralDocumentCount() : 0)
                + (activeRun.getDatabaseDocumentCount() != null
                    ? activeRun.getDatabaseDocumentCount() : 0);
            status.setDocumentCount(docCount);
            status.setLastIndexedAt(activeRun.getFinishedAt());
            status.setLastRunMode("auto");
            // 3. Get point count from active collection
            long pointCount = collectionManager.getPointCount(activeCollection);
            status.setChunkCount((int) Math.max(pointCount, 0));
        } else {
            status.setIndexed(false);
        }

        // 4. The latest attempt is independent from the active successful version.
        RagIndexRun latestRun = indexRunMapper.selectLatest();
        if (latestRun != null) {
            status.setLatestRunId(latestRun.getRunId());
            status.setLatestRunStatus(latestRun.getStatus());
            status.setBuildProgressPercent(latestRun.getProgressPercent());
            status.setBuildProgressStage(latestRun.getProgressStage());
            status.setLastError(latestRun.getErrorSummary());

            // Without an active version, expose candidate progress instead.
            if (!status.isIndexed() && latestRun.getCandidateCollection() != null) {
                long pointCount = collectionManager.getPointCount(
                    latestRun.getCandidateCollection());
                status.setChunkCount((int) Math.max(pointCount, 0));
            }
        }

        return status;
    }

    /**
     * Marks any PENDING, RUNNING, VALIDATING, or SWITCHING runs as INTERRUPTED.
     *
     * <p>Should be called on application startup to clean up stale runs left
     * by a previous process termination.</p>
     */
    public void markPendingInterrupted() {
        indexRunMapper.markInterrupted(INTERRUPTIBLE_STATUSES);
    }
}
