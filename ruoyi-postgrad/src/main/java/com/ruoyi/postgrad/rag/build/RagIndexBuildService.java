package com.ruoyi.postgrad.rag.build;

import com.ruoyi.postgrad.rag.domain.RagIndexRun;
import com.ruoyi.postgrad.rag.mapper.RagIndexRunMapper;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Single build orchestrator for RAG index.
 *
 * <p>Start a new build via {@link #startBuild(int, int)} — acquires a Redis lock
 * ({@code rag:build:lock}) to prevent concurrent builds and returns a runId.
 * If a build is already running, returns the existing runId.</p>
 *
 * <p>The actual pipeline executes inside {@link #executeBuild(String, int, int)}:
 * <ol>
 *   <li>Create a versioned Qdrant candidate collection</li>
 *   <li>Create payload indexes on filterable fields</li>
 *   <li>Assemble the unified corpus ({@link RagCorpusAssembler})</li>
 *   <li>Batch-embed all chunks and upsert into the candidate collection</li>
 *   <li>Validate point count matches chunk count</li>
 *   <li>Switch the active alias to the candidate collection</li>
 *   <li>Clean up the previous version collection</li>
 * </ol>
 * </p>
 *
 * <p>Any failure marks the run as FAILED, preserves the old alias, and releases
 * the build lock.</p>
 */
@Service
public class RagIndexBuildService {

    private static final Logger log = LoggerFactory.getLogger(RagIndexBuildService.class);

    /** Alias name for the active RAG collection. */
    public static final String ACTIVE_ALIAS = "postgrad_rag_active";

    /** Redis lock key for build mutual exclusion. */
    public static final String LOCK_KEY = "rag:build:lock";

    private static final Duration LOCK_TTL = Duration.ofSeconds(1800L);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);

    private final RagCorpusAssembler corpusAssembler;
    private final RagCollectionManager collectionManager;
    private final RagIndexRunMapper indexRunMapper;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final RagEmbeddingStoreFactory embeddingStoreFactory;
    private final StringRedisTemplate redisTemplate;
    private final RagIndexJobExecutor jobExecutor;
    private final int batchSize;
    private final int embeddingDimension;
    private final String embeddingModelName;

    public RagIndexBuildService(
            RagCorpusAssembler corpusAssembler,
            RagCollectionManager collectionManager,
            RagIndexRunMapper indexRunMapper,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            RagEmbeddingStoreFactory embeddingStoreFactory,
            StringRedisTemplate redisTemplate,
            RagIndexJobExecutor jobExecutor,
            @Value("${app.ai.rag.indexing.batch-size:100}") int batchSize,
            @Value("${app.ai.rag.qdrant.dimension:1024}") int embeddingDimension,
            @Value("${app.ai.rag.embedding.model-name:BAAI/bge-m3}") String embeddingModelName) {
        this.corpusAssembler = corpusAssembler;
        this.collectionManager = collectionManager;
        this.indexRunMapper = indexRunMapper;
        this.embeddingModelProvider = embeddingModelProvider;
        this.embeddingStoreFactory = embeddingStoreFactory;
        this.redisTemplate = redisTemplate;
        this.jobExecutor = jobExecutor;
        this.batchSize = batchSize;
        this.embeddingDimension = embeddingDimension;
        this.embeddingModelName = embeddingModelName;
    }

    /**
     * Start a new index build. Returns immediately with a runId.
     *
     * <p>If another build is already running (Redis lock exists), returns the
     * existing runId rather than starting a second build.</p>
     *
     * @param startYear inclusive start year for database evidence
     * @param endYear   inclusive end year for database evidence
     * @return runId (new UUID, or existing if a build is already in progress)
     */
    public String startBuild(int startYear, int endYear) {
        String runId = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(LOCK_KEY, runId, LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            RagIndexRun run = new RagIndexRun();
            run.setRunId(runId);
            run.setStatus("PENDING");
            run.setStartYear(startYear);
            run.setEndYear(endYear);
            run.setEmbeddingModel(embeddingModelName);
            run.setEmbeddingDimension(embeddingDimension);
            run.setGeneralDocumentCount(0);
            run.setDatabaseDocumentCount(0);
            run.setChunkCount(0);
            run.setIndexedChunkCount(0);
            run.setProgressPercent(0);
            try {
                indexRunMapper.insert(run);
                jobExecutor.submit(runId, () -> executeBuild(runId, startYear, endYear));
            } catch (RuntimeException ex) {
                releaseLock(runId);
                throw ex;
            }
            log.info("[RagIndexBuild] Lock acquired, build runId={} started (years={}-{})",
                runId, startYear, endYear);
            return runId;
        }

        // Lock not acquired — another build is in progress; return its runId
        String existingRunId = redisTemplate.opsForValue().get(LOCK_KEY);
        if (existingRunId != null) {
            log.info("[RagIndexBuild] Build already in progress (runId={}), returning existing", existingRunId);
            return existingRunId;
        }

        throw new IllegalStateException("RAG build lock state changed; retry the request");
    }

    /**
     * Execute the full build pipeline for the given runId.
     *
     * <p>This method is designed to be called by a JobExecutor on a background
     * thread after {@link #startBuild(int, int)} returns.</p>
     *
     * @param runId     the run identifier returned by {@link #startBuild}
     * @param startYear inclusive start year for database evidence
     * @param endYear   inclusive end year for database evidence
     */
    public void executeBuild(String runId, int startYear, int endYear) {
        String currentStage = "init";
        String candidateCollection = null;
        boolean aliasSwitched = false;

        try {
            // 1. Verify lock ownership
            String lockValue = redisTemplate.opsForValue().get(LOCK_KEY);
            if (!runId.equals(lockValue)) {
                log.warn("[RagIndexBuild] Lock not held by runId={}, skipping executeBuild", runId);
                return;
            }

            // 2. Update status to RUNNING
            indexRunMapper.updateStatus(runId, "RUNNING", null, null, null, null, null);

            // 3. Assemble corpus before creating any Qdrant artifacts
            currentStage = "ASSEMBLY";
            indexRunMapper.updateStatus(runId, "RUNNING", null, "assembling", 0, null, null);
            AssembledCorpus assembledCorpus = corpusAssembler.assemble(startYear, endYear);
            List<TextSegment> chunks = assembledCorpus.chunks();
            int totalChunks = chunks.size();
            if (totalChunks == 0) {
                throw new IllegalStateException("RAG corpus is empty");
            }
            indexRunMapper.updateCorpusStats(runId,
                assembledCorpus.generalDocumentCount(),
                assembledCorpus.databaseDocumentCount(),
                totalChunks);
            log.info("[RagIndexBuild] Assembled corpus: {} chunks ({} general, {} database)",
                totalChunks, assembledCorpus.generalDocumentCount(),
                assembledCorpus.databaseDocumentCount());

            // 4. Create the isolated candidate collection and its payload indexes
            candidateCollection = collectionManager.createVersionCollection(embeddingDimension);
            indexRunMapper.updateCandidateCollection(runId, candidateCollection);
            EmbeddingStore<TextSegment> candidateStore = embeddingStoreFactory.create(candidateCollection);
            collectionManager.createPayloadIndexes(candidateCollection);
            log.info("[RagIndexBuild] Created candidate collection {}", candidateCollection);

            // 5. Batch embed + upsert (progress: embedding)
            currentStage = "EMBEDDING";
            EmbeddingModel model = embeddingModelProvider.getIfAvailable();
            if (model == null) {
                throw new IllegalStateException("EmbeddingModel is not available");
            }

            indexRunMapper.updateStatus(runId, "RUNNING", 0, "embedding", 0, null, null);
            int indexedCount = 0;
            while (indexedCount < totalChunks) {
                int end = Math.min(indexedCount + batchSize, totalChunks);
                List<TextSegment> batch = chunks.subList(indexedCount, end);

                Response<List<Embedding>> embedResponse = model.embedAll(batch);
                List<Embedding> embeddings = embedResponse.content();

                List<String> ids = batch.stream()
                    .map(seg -> UUID.nameUUIDFromBytes(
                        (seg.metadata().getString("source_id")
                         + ":" + seg.metadata().getString("chunk_index"))
                            .getBytes(StandardCharsets.UTF_8)
                    ).toString())
                    .toList();

                candidateStore.addAll(ids, embeddings, batch);

                indexedCount = end;
                int percent = Math.min(indexedCount * 100 / totalChunks, 99);
                indexRunMapper.updateStatus(runId, "RUNNING", indexedCount, "embedding",
                    percent, null, null);

                log.debug("[RagIndexBuild] Batch upsert: {}/{} chunks ({})",
                    indexedCount, totalChunks, candidateCollection);
            }

            // 7. Validate (progress: validating)
            currentStage = "VALIDATION";
            indexRunMapper.updateStatus(runId, "RUNNING", indexedCount, "validating", 90, null, null);
            long pointCount = collectionManager.getPointCount(candidateCollection);
            if (pointCount != totalChunks) {
                throw new IllegalStateException(
                    "VALIDATION_POINT_COUNT_MISMATCH: Expected " + totalChunks
                    + " points but found " + pointCount
                    + " in collection " + candidateCollection);
            }
            log.info("[RagIndexBuild] Validation passed: {} points match {} chunks", pointCount, totalChunks);

            // 8. Switch alias (progress: switching)
            currentStage = "ALIAS_SWITCH";
            indexRunMapper.updateStatus(runId, "RUNNING", indexedCount, "switching", 95, null, null);
            String oldCollection = collectionManager.resolveAlias(ACTIVE_ALIAS);
            collectionManager.switchAlias(ACTIVE_ALIAS, candidateCollection);
            aliasSwitched = true;
            log.info("[RagIndexBuild] Switched alias '{}' -> '{}' (was: {})",
                ACTIVE_ALIAS, candidateCollection, oldCollection);

            // 9. Update SUCCEEDED
            indexRunMapper.switchToSucceeded(runId, oldCollection);

            log.info("[RagIndexBuild] Build {} completed successfully: {} chunks indexed into {}",
                runId, totalChunks, candidateCollection);

        } catch (Exception e) {
            String errorCode = deriveErrorCode(currentStage, e);
            String errorSummary = e.getMessage() != null ? e.getMessage() : errorCode;
            if (!aliasSwitched) {
                indexRunMapper.updateStatus(runId, "FAILED", null, null, null, errorCode, errorSummary);
                cleanupFailedCandidate(candidateCollection);
            } else {
                log.error("[RagIndexBuild] Alias already switched for runId={}, control-plane reconciliation required",
                    runId, e);
            }
            log.error("[RagIndexBuild] Build {} failed at stage '{}' with code {}: {}",
                runId, currentStage, errorCode, errorSummary, e);
        } finally {
            // 11. Release lock
            releaseLock(runId);
        }
    }

    private void releaseLock(String runId) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), runId);
        } catch (Exception e) {
            log.warn("[RagIndexBuild] Failed to release lock for runId={}: {}", runId, e.getMessage());
        }
    }

    private void cleanupFailedCandidate(String candidateCollection) {
        if (candidateCollection == null || candidateCollection.isBlank()) {
            return;
        }
        try {
            collectionManager.deleteCollection(candidateCollection);
        } catch (Exception cleanupError) {
            log.warn("[RagIndexBuild] Failed to clean candidate collection {}: {}",
                candidateCollection, cleanupError.getMessage());
        }
    }

    /**
     * Derives a machine-readable error code from the current stage and the
     * thrown exception. For the VALIDATION stage, if the exception message
     * contains a prefixed error code ({@code CODE: message}), that prefix is
     * used directly. Otherwise the stage name plus {@code _ERROR} is returned.
     */
    private static String deriveErrorCode(String currentStage, Exception e) {
        if ("VALIDATION".equals(currentStage)) {
            String msg = e.getMessage();
            if (msg != null) {
                int colonIdx = msg.indexOf(":");
                if (colonIdx > 0 && colonIdx < 80) {
                    String prefix = msg.substring(0, colonIdx);
                    if (prefix.contains("VALIDATION_") || prefix.contains("POINT_COUNT")) {
                        return prefix.trim();
                    }
                }
            }
        }
        return currentStage + "_ERROR";
    }
}
