package com.ruoyi.postgrad.rag;

import static org.mockito.Mockito.*;

import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import com.ruoyi.postgrad.rag.status.RagIndexStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class RagInitializerTest {

    // T5.1: alias exists → log status, no write operations
    @Test
    void shouldLogStatusWhenAliasExists() {
        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        RagCollectionManager collectionManager = mock(RagCollectionManager.class);
        when(collectionManager.resolveAlias("postgrad_rag_active"))
            .thenReturn("postgrad_rag_1234567890_abc12345");
        when(collectionManager.getPointCount("postgrad_rag_1234567890_abc12345"))
            .thenReturn(100L);

        RagInitializer initializer = new RagInitializer(statusService, collectionManager);
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(statusService).markPendingInterrupted();
        verify(collectionManager).resolveAlias("postgrad_rag_active");
        verify(collectionManager).getPointCount("postgrad_rag_1234567890_abc12345");
        // No write operations
        verify(collectionManager, never()).createVersionCollection(anyInt());
        verify(collectionManager, never()).deleteCollection(anyString());
        verify(collectionManager, never()).switchAlias(anyString(), anyString());
    }

    // T5.2: alias missing → NOT_INITIALIZED log, no error
    @Test
    void shouldLogNotInitializedWhenAliasMissing() {
        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        RagCollectionManager collectionManager = mock(RagCollectionManager.class);
        when(collectionManager.resolveAlias("postgrad_rag_active")).thenReturn(null);

        RagInitializer initializer = new RagInitializer(statusService, collectionManager);
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(statusService).markPendingInterrupted();
        verify(collectionManager).resolveAlias("postgrad_rag_active");
        verify(collectionManager, never()).getPointCount(anyString());
        verify(collectionManager, never()).createVersionCollection(anyInt());
        verify(collectionManager, never()).deleteCollection(anyString());
    }

    // T5.3: markPendingInterrupted always called
    @Test
    void shouldAlwaysCallMarkPendingInterrupted() {
        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        RagCollectionManager collectionManager = mock(RagCollectionManager.class);
        when(collectionManager.resolveAlias("postgrad_rag_active")).thenReturn("some_collection");
        when(collectionManager.getPointCount(anyString())).thenReturn(50L);

        RagInitializer initializer = new RagInitializer(statusService, collectionManager);
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(statusService).markPendingInterrupted();
        verify(collectionManager).resolveAlias("postgrad_rag_active");
    }

    // T5.4: Qdrant failure → graceful, no crash
    @Test
    void shouldHandleQdrantFailureGracefully() {
        RagIndexStatusService statusService = mock(RagIndexStatusService.class);
        RagCollectionManager collectionManager = mock(RagCollectionManager.class);
        when(collectionManager.resolveAlias("postgrad_rag_active"))
            .thenThrow(new RuntimeException("Qdrant not reachable"));

        RagInitializer initializer = new RagInitializer(statusService, collectionManager);
        // Should not throw
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(statusService).markPendingInterrupted();
        verify(collectionManager).resolveAlias("postgrad_rag_active");
        verify(collectionManager, never()).getPointCount(anyString());
        verify(collectionManager, never()).createVersionCollection(anyInt());
    }
}
