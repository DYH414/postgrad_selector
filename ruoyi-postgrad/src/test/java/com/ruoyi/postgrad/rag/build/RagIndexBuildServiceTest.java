package com.ruoyi.postgrad.rag.build;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ruoyi.postgrad.rag.domain.RagIndexRun;
import com.ruoyi.postgrad.rag.mapper.RagIndexRunMapper;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class RagIndexBuildServiceTest {

    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2025;
    private static final int BATCH_SIZE = 100;
    private static final int DIMENSION = 1024;
    private static final String CANDIDATE_COLLECTION = "postgrad_rag_1234567890_abc12345";
    private static final String OLD_COLLECTION = "postgrad_rag_1234567000_oldcol";
    private static final String LOCK_KEY = "rag:build:lock";

    @Mock
    private RagCorpusAssembler corpusAssembler;
    @Mock
    private RagCollectionManager collectionManager;
    @Mock
    private RagIndexRunMapper indexRunMapper;
    @Mock
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;
    @Mock
    private RagEmbeddingStoreFactory embeddingStoreFactory;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private RagIndexJobExecutor jobExecutor;

    @Captor
    private ArgumentCaptor<Integer> countCaptor;

    private RagIndexBuildService service;

    @BeforeEach
    void setUp() {
        service = new RagIndexBuildService(
            corpusAssembler,
            collectionManager,
            indexRunMapper,
            embeddingModelProvider,
            embeddingStoreFactory,
            redisTemplate,
            jobExecutor,
            BATCH_SIZE,
            DIMENSION,
            "BAAI/bge-m3"
        );

        // Common stubs (lenient — not every test uses all of them)
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(indexRunMapper.insert(any())).thenReturn(1);
        lenient().when(embeddingStoreFactory.create(anyString())).thenReturn(embeddingStore);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(1L);
    }

    // ===== T3.1: Full pipeline success =====

    @Test
    void shouldExecuteFullPipelineSuccessfully() {
        // Arrange — corpus assembly
        List<TextSegment> chunks = createChunks(5);
        AssembledCorpus corpus = new AssembledCorpus(chunks, 2, 3);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR)).thenReturn(corpus);

        // Arrange — embedding model
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        List<Embedding> embeddings = createEmbeddings(5);
        Response<List<Embedding>> embedResponse = mock(Response.class);
        when(embedResponse.content()).thenReturn(embeddings);
        when(embeddingModel.embedAll(anyList())).thenReturn(embedResponse);

        // Arrange — collection management
        when(collectionManager.createVersionCollection(DIMENSION))
            .thenReturn(CANDIDATE_COLLECTION);
        when(collectionManager.resolveAlias(RagIndexBuildService.ACTIVE_ALIAS))
            .thenReturn(null);

        // Arrange — validation
        when(collectionManager.getPointCount(CANDIDATE_COLLECTION)).thenReturn(5L);

        // Arrange — lock: acquire + verify
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        assertNotNull(runId);

        // Arrange — make lock verification return the actual runId
        doReturn(runId).when(valueOps).get(LOCK_KEY);

        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — lock acquired
        InOrder inOrder = inOrder(
            valueOps, indexRunMapper, collectionManager, corpusAssembler,
            embeddingModel, embeddingStore, redisTemplate
        );

        // Lock acquired
        inOrder.verify(valueOps).setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L)));
        inOrder.verify(indexRunMapper).insert(any());

        // Update RUNNING
        inOrder.verify(indexRunMapper).updateStatus(eq(runId), eq("RUNNING"), isNull(), isNull(),
            isNull(), isNull(), isNull());

        // Assemble
        inOrder.verify(indexRunMapper).updateStatus(eq(runId), eq("RUNNING"), isNull(), eq("assembling"),
            eq(0), isNull(), isNull());
        inOrder.verify(corpusAssembler).assemble(START_YEAR, END_YEAR);
        inOrder.verify(indexRunMapper).updateCorpusStats(runId, 2, 3, 5);

        // Create candidate collection only after a non-empty corpus exists
        inOrder.verify(collectionManager).createVersionCollection(DIMENSION);
        inOrder.verify(indexRunMapper).updateCandidateCollection(runId, CANDIDATE_COLLECTION);
        verify(embeddingStoreFactory).create(CANDIDATE_COLLECTION);
        inOrder.verify(collectionManager).createPayloadIndexes(CANDIDATE_COLLECTION);

        // Embedding init
        inOrder.verify(indexRunMapper).updateStatus(eq(runId), eq("RUNNING"), eq(0), eq("embedding"),
            eq(0), isNull(), isNull());
        inOrder.verify(embeddingModel).embedAll(chunks);
        inOrder.verify(embeddingStore).addAll(anyList(), eq(embeddings), eq(chunks));

        // Progress update
        inOrder.verify(indexRunMapper).updateStatus(eq(runId), eq("RUNNING"), eq(5), eq("embedding"),
            anyInt(), isNull(), isNull());

        // Validate
        inOrder.verify(indexRunMapper).updateStatus(eq(runId), eq("RUNNING"), eq(5), eq("validating"),
            eq(90), isNull(), isNull());
        inOrder.verify(collectionManager).getPointCount(CANDIDATE_COLLECTION);

        // Switch
        inOrder.verify(indexRunMapper).updateStatus(eq(runId), eq("RUNNING"), eq(5), eq("switching"),
            eq(95), isNull(), isNull());
        inOrder.verify(collectionManager).resolveAlias(RagIndexBuildService.ACTIVE_ALIAS);
        inOrder.verify(collectionManager).switchAlias(RagIndexBuildService.ACTIVE_ALIAS, CANDIDATE_COLLECTION);

        // Succeeded
        inOrder.verify(indexRunMapper).switchToSucceeded(runId, null);

        // Release lock
        inOrder.verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), eq(runId));
    }

    // ===== T3.2: Lock rejection returns existing runId =====

    @Test
    void shouldInitializeRequiredFieldsBeforePersistingPendingRun() {
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);

        service.startBuild(START_YEAR, END_YEAR);

        ArgumentCaptor<RagIndexRun> runCaptor = ArgumentCaptor.forClass(RagIndexRun.class);
        verify(indexRunMapper).insert(runCaptor.capture());
        RagIndexRun run = runCaptor.getValue();

        assertAll(
            () -> assertEquals("PENDING", run.getStatus()),
            () -> assertEquals("BAAI/bge-m3", run.getEmbeddingModel()),
            () -> assertEquals(DIMENSION, run.getEmbeddingDimension()),
            () -> assertEquals(0, run.getGeneralDocumentCount()),
            () -> assertEquals(0, run.getDatabaseDocumentCount()),
            () -> assertEquals(0, run.getChunkCount()),
            () -> assertEquals(0, run.getIndexedChunkCount()),
            () -> assertEquals(0, run.getProgressPercent())
        );
        verify(jobExecutor).submit(eq(run.getRunId()), any(Runnable.class));
    }

    @Test
    void shouldReleaseBuildLockWhenPendingRunCannotBePersisted() {
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(indexRunMapper.insert(any(RagIndexRun.class)))
            .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
            () -> service.startBuild(START_YEAR, END_YEAR));

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class), anyList(), anyString());
        verifyNoInteractions(jobExecutor);
    }

    @Test
    void shouldReturnExistingRunIdWhenLockAlreadyHeld() {
        // Arrange
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(false);
        when(valueOps.get(LOCK_KEY)).thenReturn("existing-run-id");

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);

        // Assert
        assertEquals("existing-run-id", runId);
        verify(indexRunMapper, never()).insert(any());
        verifyNoInteractions(jobExecutor);
    }

    @Test
    void shouldFailInsteadOfReturningUnpersistedRunIdWhenLockStateRaces() {
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(false);
        when(valueOps.get(LOCK_KEY)).thenReturn(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> service.startBuild(START_YEAR, END_YEAR));

        assertTrue(error.getMessage().contains("lock state changed"));
        verify(indexRunMapper, never()).insert(any());
        verifyNoInteractions(jobExecutor);
    }

    // ===== T3.3: Assembly failure → FAILED, no alias switch =====

    @Test
    void shouldRejectEmptyCorpusBeforeCreatingCandidateCollection() {
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR))
            .thenReturn(new AssembledCorpus(List.of(), 0, 0));

        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        verify(collectionManager, never()).createVersionCollection(anyInt());
        verify(collectionManager, never()).switchAlias(anyString(), anyString());
        verify(indexRunMapper).updateStatus(
            eq(runId), eq("FAILED"), isNull(), isNull(), isNull(),
            eq("ASSEMBLY_ERROR"), contains("empty"));
    }

    @Test
    void shouldMarkFailedWhenAssemblyFails() {
        // Arrange — lock
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR))
            .thenThrow(new RuntimeException("Assembly failed"));

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — FAILED status, no alias switch
        verify(indexRunMapper).updateStatus(
            eq(runId), eq("FAILED"), isNull(), isNull(), isNull(),
            eq("ASSEMBLY_ERROR"), contains("Assembly failed")
        );
        verify(collectionManager, never()).switchAlias(anyString(), anyString());
    }

    // ===== T3.4: Embedding failure → FAILED, no alias switch, old index preserved =====

    @Test
    void shouldMarkFailedWhenEmbeddingFails() {
        // Arrange — lock
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(collectionManager.createVersionCollection(DIMENSION))
            .thenReturn(CANDIDATE_COLLECTION);

        List<TextSegment> chunks = createChunks(3);
        AssembledCorpus corpus = new AssembledCorpus(chunks, 1, 2);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR)).thenReturn(corpus);
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        when(embeddingModel.embedAll(anyList()))
            .thenThrow(new RuntimeException("Embedding API error"));

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — FAILED, no alias switch
        verify(indexRunMapper).updateStatus(
            eq(runId), eq("FAILED"), isNull(), isNull(), isNull(),
            eq("EMBEDDING_ERROR"), contains("Embedding API error")
        );
        verify(collectionManager, never()).switchAlias(anyString(), anyString());
    }

    // ===== T3.5: Point count mismatch → FAILED, errorCode=VALIDATION_POINT_COUNT_MISMATCH =====

    @Test
    void shouldFailOnPointCountMismatch() {
        // Arrange — lock
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(collectionManager.createVersionCollection(DIMENSION))
            .thenReturn(CANDIDATE_COLLECTION);

        List<TextSegment> chunks = createChunks(5);
        AssembledCorpus corpus = new AssembledCorpus(chunks, 2, 3);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR)).thenReturn(corpus);
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        List<Embedding> embeddings = createEmbeddings(5);
        Response<List<Embedding>> embedResponse = mock(Response.class);
        when(embedResponse.content()).thenReturn(embeddings);
        when(embeddingModel.embedAll(anyList())).thenReturn(embedResponse);

        // Mismatch: 3 points in Qdrant vs 5 chunks
        when(collectionManager.getPointCount(CANDIDATE_COLLECTION)).thenReturn(3L);

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — FAILED with specific error code, no alias switch
        verify(indexRunMapper).updateStatus(
            eq(runId), eq("FAILED"), any(), isNull(), isNull(),
            eq("VALIDATION_POINT_COUNT_MISMATCH"), contains("Expected 5")
        );
        verify(collectionManager, never()).switchAlias(anyString(), anyString());
    }

    // ===== T3.6: Alias switch failure → FAILED, old alias unchanged =====

    @Test
    void shouldFailWhenAliasSwitchFails() {
        // Arrange — lock
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(collectionManager.createVersionCollection(DIMENSION))
            .thenReturn(CANDIDATE_COLLECTION);

        List<TextSegment> chunks = createChunks(3);
        AssembledCorpus corpus = new AssembledCorpus(chunks, 1, 2);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR)).thenReturn(corpus);
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        List<Embedding> embeddings = createEmbeddings(3);
        Response<List<Embedding>> embedResponse = mock(Response.class);
        when(embedResponse.content()).thenReturn(embeddings);
        when(embeddingModel.embedAll(anyList())).thenReturn(embedResponse);

        when(collectionManager.getPointCount(CANDIDATE_COLLECTION)).thenReturn(3L);
        when(collectionManager.resolveAlias(RagIndexBuildService.ACTIVE_ALIAS))
            .thenReturn(OLD_COLLECTION);
        doThrow(new RuntimeException("Qdrant API error"))
            .when(collectionManager).switchAlias(RagIndexBuildService.ACTIVE_ALIAS, CANDIDATE_COLLECTION);

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — FAILED, old alias NOT switched
        verify(indexRunMapper).updateStatus(
            eq(runId), eq("FAILED"), isNull(), isNull(), isNull(),
            eq("ALIAS_SWITCH_ERROR"), contains("Qdrant API error")
        );
        // Old collection should NOT be deleted (it's still the active one)
        verify(collectionManager, never()).deleteCollection(OLD_COLLECTION);
    }

    // ===== T3.7: Batch progress updates indexed_chunk_count =====

    @Test
    void shouldUpdateBatchProgressForIndexedChunkCount() {
        // Arrange — lock
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(collectionManager.createVersionCollection(DIMENSION))
            .thenReturn(CANDIDATE_COLLECTION);

        // 250 chunks with batchSize=100 → 3 batches
        List<TextSegment> chunks = createChunks(250);
        AssembledCorpus corpus = new AssembledCorpus(chunks, 5, 10);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR)).thenReturn(corpus);

        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        Response<List<Embedding>> batch1response = mock(Response.class);
        when(batch1response.content()).thenReturn(createEmbeddings(100));
        Response<List<Embedding>> batch2response = mock(Response.class);
        when(batch2response.content()).thenReturn(createEmbeddings(100));
        Response<List<Embedding>> batch3response = mock(Response.class);
        when(batch3response.content()).thenReturn(createEmbeddings(50));
        when(embeddingModel.embedAll(anyList()))
            .thenReturn(batch1response)
            .thenReturn(batch2response)
            .thenReturn(batch3response);

        when(collectionManager.getPointCount(CANDIDATE_COLLECTION)).thenReturn(250L);
        when(collectionManager.resolveAlias(RagIndexBuildService.ACTIVE_ALIAS))
            .thenReturn(null);

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — 3 progress updates with increasing indexedChunkCount
        verify(indexRunMapper, atLeast(3)).updateStatus(
            eq(runId), eq("RUNNING"), countCaptor.capture(), eq("embedding"),
            anyInt(), isNull(), isNull()
        );

        List<Integer> counts = countCaptor.getAllValues();
        assertTrue(counts.contains(100), "Should have progress update for 100");
        assertTrue(counts.contains(200), "Should have progress update for 200");
        assertTrue(counts.contains(250), "Should have progress update for 250");
    }

    // ===== T3.8: progress_stage changes =====

    @Test
    void shouldTrackProgressStageChanges() {
        // Arrange — lock
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(collectionManager.createVersionCollection(DIMENSION))
            .thenReturn(CANDIDATE_COLLECTION);

        List<TextSegment> chunks = createChunks(3);
        AssembledCorpus corpus = new AssembledCorpus(chunks, 1, 2);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR)).thenReturn(corpus);

        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        List<Embedding> embeddings = createEmbeddings(3);
        Response<List<Embedding>> embedResponse = mock(Response.class);
        when(embedResponse.content()).thenReturn(embeddings);
        when(embeddingModel.embedAll(anyList())).thenReturn(embedResponse);

        when(collectionManager.getPointCount(CANDIDATE_COLLECTION)).thenReturn(3L);
        when(collectionManager.resolveAlias(RagIndexBuildService.ACTIVE_ALIAS))
            .thenReturn(null);

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — progress stages appear in order
        ArgumentCaptor<String> stageCaptor = ArgumentCaptor.forClass(String.class);
        verify(indexRunMapper, atLeast(4)).updateStatus(
            eq(runId), anyString(), any(), stageCaptor.capture(),
            anyInt(), isNull(), isNull()
        );

        List<String> stages = stageCaptor.getAllValues();
        int assemblingIdx = indexOf(stages, "assembling");
        int embeddingIdx = indexOf(stages, "embedding");
        int validatingIdx = indexOf(stages, "validating");
        int switchingIdx = indexOf(stages, "switching");

        assertTrue(assemblingIdx >= 0, "Should have 'assembling' stage");
        assertTrue(embeddingIdx >= 0, "Should have 'embedding' stage");
        assertTrue(validatingIdx >= 0, "Should have 'validating' stage");
        assertTrue(switchingIdx >= 0, "Should have 'switching' stage");

        assertTrue(assemblingIdx < embeddingIdx,
            "'assembling' should come before 'embedding'");
        assertTrue(embeddingIdx < validatingIdx,
            "'embedding' should come before 'validating'");
        assertTrue(validatingIdx < switchingIdx,
            "'validating' should come before 'switching'");
    }

    // ===== T3.9: Previous successful version retained for rollback =====

    @Test
    void shouldRetainPreviousCollectionAfterSuccessfulBuild() {
        // Arrange — lock
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofSeconds(1800L))))
            .thenReturn(true);
        when(collectionManager.createVersionCollection(DIMENSION))
            .thenReturn(CANDIDATE_COLLECTION);

        List<TextSegment> chunks = createChunks(3);
        AssembledCorpus corpus = new AssembledCorpus(chunks, 1, 2);
        when(corpusAssembler.assemble(START_YEAR, END_YEAR)).thenReturn(corpus);

        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        List<Embedding> embeddings = createEmbeddings(3);
        Response<List<Embedding>> embedResponse = mock(Response.class);
        when(embedResponse.content()).thenReturn(embeddings);
        when(embeddingModel.embedAll(anyList())).thenReturn(embedResponse);

        when(collectionManager.getPointCount(CANDIDATE_COLLECTION)).thenReturn(3L);
        // There IS a previous collection
        when(collectionManager.resolveAlias(RagIndexBuildService.ACTIVE_ALIAS))
            .thenReturn(OLD_COLLECTION);

        // Act
        String runId = service.startBuild(START_YEAR, END_YEAR);
        doReturn(runId).when(valueOps).get(LOCK_KEY);
        service.executeBuild(runId, START_YEAR, END_YEAR);

        // Assert — previous collection remains available for rollback
        verify(collectionManager, never()).deleteCollection(OLD_COLLECTION);
        verify(indexRunMapper).switchToSucceeded(runId, OLD_COLLECTION);
    }

    // ===== Helper methods =====

    private static List<TextSegment> createChunks(int count) {
        List<TextSegment> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextSegment segment = TextSegment.from("Chunk " + i + " content");
            segment.metadata().put("source_id", "src:" + i);
            segment.metadata().put("chunk_index", String.valueOf(i));
            list.add(segment);
        }
        return list;
    }

    private static List<Embedding> createEmbeddings(int count) {
        List<Embedding> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float[] vector = new float[DIMENSION];
            vector[0] = i;
            list.add(new Embedding(vector));
        }
        return list;
    }

    private static int indexOf(List<String> list, String value) {
        for (int i = 0; i < list.size(); i++) {
            if (value.equals(list.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
