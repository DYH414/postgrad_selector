package com.ruoyi.postgrad.rag.status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ruoyi.postgrad.rag.RagStatus;
import com.ruoyi.postgrad.rag.domain.RagIndexRun;
import com.ruoyi.postgrad.rag.mapper.RagIndexRunMapper;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class RagIndexStatusServiceTest {

    @Mock
    private RagIndexRunMapper indexRunMapper;

    @Mock
    private RagCollectionManager collectionManager;

    // ===== T4.1: Active version exists =====

    @Test
    void shouldReturnFullStatusWhenActiveVersionExists() {
        // Arrange
        RagIndexRun activeRun = new RagIndexRun();
        activeRun.setRunId("run-active-1");
        activeRun.setStatus("SUCCEEDED");
        activeRun.setGeneralDocumentCount(10);
        activeRun.setDatabaseDocumentCount(5);
        activeRun.setIndexedChunkCount(200);
        activeRun.setFinishedAt(LocalDateTime.of(2026, 7, 10, 12, 0, 0));

        when(indexRunMapper.selectActive()).thenReturn(activeRun);
        when(collectionManager.resolveAlias(RagIndexStatusService.ACTIVE_ALIAS))
            .thenReturn("postgrad_rag_v2");
        when(collectionManager.getPointCount("postgrad_rag_v2")).thenReturn(200L);

        RagIndexStatusService service = new RagIndexStatusService(indexRunMapper, collectionManager);

        // Act
        RagStatus status = service.getStatus();

        // Assert
        assertTrue(status.isIndexed());
        assertEquals(15, status.getDocumentCount());
        assertEquals(200, status.getChunkCount());
        assertEquals("postgrad_rag_v2", status.getActiveCollection());
        assertEquals("postgrad_rag_v2", status.getCollectionName());
        assertEquals("run-active-1", status.getLatestRunId());
        assertEquals("SUCCEEDED", status.getLatestRunStatus());
        assertEquals("qdrant", status.getBackend());
        assertTrue(status.isReachable());
        assertTrue(status.isEnabled());
        assertNotNull(status.getLastIndexedAt());
    }

    // ===== T4.2: No active alias =====

    @Test
    void shouldReturnNotInitializedWhenNoActiveAlias() {
        // Arrange
        when(indexRunMapper.selectActive()).thenReturn(null);
        when(indexRunMapper.selectLatest()).thenReturn(null);
        when(collectionManager.resolveAlias(RagIndexStatusService.ACTIVE_ALIAS))
            .thenReturn(null);

        RagIndexStatusService service = new RagIndexStatusService(indexRunMapper, collectionManager);

        // Act
        RagStatus status = service.getStatus();

        // Assert
        assertFalse(status.isIndexed());
        assertNull(status.getLatestRunId());
        assertNull(status.getLatestRunStatus());
        assertNull(status.getActiveCollection());
        assertNull(status.getCollectionName());
        assertNull(status.getBuildProgressPercent());
        assertNull(status.getBuildProgressStage());
    }

    // ===== T4.3: Build in progress =====

    @Test
    void shouldShowRunningStatusWhenBuildInProgress() {
        // Arrange
        RagIndexRun buildRun = new RagIndexRun();
        buildRun.setRunId("run-build-1");
        buildRun.setStatus("RUNNING");
        buildRun.setProgressPercent(60);
        buildRun.setProgressStage("embedding");
        buildRun.setCandidateCollection("postgrad_rag_candidate_xxx");
        buildRun.setIndexedChunkCount(50);

        when(indexRunMapper.selectActive()).thenReturn(null);
        when(indexRunMapper.selectLatest()).thenReturn(buildRun);
        when(collectionManager.resolveAlias(RagIndexStatusService.ACTIVE_ALIAS))
            .thenReturn(null);
        when(collectionManager.getPointCount("postgrad_rag_candidate_xxx")).thenReturn(50L);

        RagIndexStatusService service = new RagIndexStatusService(indexRunMapper, collectionManager);

        // Act
        RagStatus status = service.getStatus();

        // Assert
        assertFalse(status.isIndexed());
        assertEquals("run-build-1", status.getLatestRunId());
        assertEquals("RUNNING", status.getLatestRunStatus());
        assertEquals(60, status.getBuildProgressPercent());
        assertEquals("embedding", status.getBuildProgressStage());
        assertEquals(50, status.getChunkCount());
    }

    // ===== T4.4: markPendingInterrupted =====

    @Test
    void shouldMarkPendingAndRunningAsInterruptedOnStartup() {
        // Arrange
        RagIndexStatusService service = new RagIndexStatusService(indexRunMapper, collectionManager);

        // Act
        service.markPendingInterrupted();

        // Assert
        verify(indexRunMapper).markInterrupted(
            List.of("PENDING", "RUNNING", "VALIDATING", "SWITCHING"));
    }
}
