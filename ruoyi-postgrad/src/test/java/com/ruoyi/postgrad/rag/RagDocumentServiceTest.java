package com.ruoyi.postgrad.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.qdrant.QdrantCollectionService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RagDocumentServiceTest {

    @Test
    void shouldSkipIndexingWhenEmbeddingModelMissing() {
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        QdrantCollectionService qdrant = mock(QdrantCollectionService.class);
        RagDocumentService service = newService(provider, store, qdrant);

        RagIndexResult result = service.reindex();

        assertEquals(0, result.getDocumentCount());
        assertEquals(0, result.getChunkCount());
        assertFalse(service.getStatus().isIndexed());
    }

    @Test
    void shouldIndexClasspathDocuments() throws Exception {
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(provider.getIfAvailable()).thenReturn(embeddingModel);
        when(embeddingModel.embedAll(any())).thenAnswer(invocation -> {
            List<?> segments = invocation.getArgument(0);
            List embeddings = segments.stream()
                .map(item -> dev.langchain4j.data.embedding.Embedding.from(new float[]{0.1f, 0.2f, 0.3f}))
                .toList();
            return Response.from(embeddings);
        });

        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        QdrantCollectionService qdrant = mock(QdrantCollectionService.class);
        RagDocumentService service = newService(provider, store, qdrant);
        inject(service, "documentsPath", "rag-documents/");
        inject(service, "chunkSize", 500);
        inject(service, "chunkOverlap", 50);

        RagIndexResult result = service.reindex();

        assertEquals(5, result.getDocumentCount());
        assertTrue(result.getChunkCount() > 0);
        assertTrue(service.getStatus().isIndexed());
        assertNotNull(service.getStatus().getLastIndexedAt());
    }

    @Test
    void statusShouldReturnCopy() {
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        QdrantCollectionService qdrant = mock(QdrantCollectionService.class);
        RagDocumentService service = newService(provider, store, qdrant);

        RagStatus status = service.getStatus();
        status.setIndexed(true);

        assertFalse(service.getStatus().isIndexed());
    }

    private static RagDocumentService newService(ObjectProvider<EmbeddingModel> provider,
                                                 EmbeddingStore<TextSegment> store,
                                                 QdrantCollectionService qdrant) {
        return new RagDocumentService(provider, store, qdrant);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = RagDocumentService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
