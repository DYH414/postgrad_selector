package com.ruoyi.postgrad.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.qdrant.QdrantCollectionService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    void shouldEnsureAndClearQdrantBeforeIndexing() throws Exception {
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        QdrantCollectionService qdrant = mock(QdrantCollectionService.class);
        when(provider.getIfAvailable()).thenReturn(embeddingModel);
        when(qdrant.getCollectionName()).thenReturn("postgrad_rag_main");
        when(qdrant.isReachable()).thenReturn(true);
        when(embeddingModel.embedAll(any())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream()
                .map(item -> dev.langchain4j.data.embedding.Embedding.from(new float[]{0.1f, 0.2f, 0.3f}))
                .toList());
        });

        RagDocumentService service = newService(provider, store, qdrant);
        inject(service, "documentsPath", "rag-documents/");
        inject(service, "chunkSize", 500);
        inject(service, "chunkOverlap", 50);

        service.reindex();

        verify(qdrant).ensureCollection();
        verify(qdrant).clearCollection();
        assertEquals("qdrant", service.getStatus().getBackend());
        assertEquals("postgrad_rag_main", service.getStatus().getCollectionName());
        assertTrue(service.getStatus().isReachable());
    }

    @Test
    void shouldAttachStableMetadataToSplitSegments() throws Exception {
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        QdrantCollectionService qdrant = mock(QdrantCollectionService.class);
        when(provider.getIfAvailable()).thenReturn(embeddingModel);
        when(qdrant.getCollectionName()).thenReturn("postgrad_rag_main");
        when(qdrant.isReachable()).thenReturn(true);
        when(embeddingModel.embedAll(any())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream()
                .map(item -> dev.langchain4j.data.embedding.Embedding.from(new float[]{0.1f, 0.2f, 0.3f}))
                .toList());
        });

        RagDocumentService service = newService(provider, store, qdrant);
        inject(service, "documentsPath", "rag-documents/");
        inject(service, "chunkSize", 500);
        inject(service, "chunkOverlap", 50);

        service.reindex();

        ArgumentCaptor<List<TextSegment>> segmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingModel).embedAll(segmentsCaptor.capture());
        List<TextSegment> segments = segmentsCaptor.getValue();
        assertFalse(segments.isEmpty());

        Metadata metadata = segments.get(0).metadata();
        assertEquals("", metadata.getString("school_id"));
        assertEquals("", metadata.getString("school_name"));
        assertEquals("", metadata.getString("major_id"));
        assertEquals("", metadata.getString("major_name"));
        assertEquals("", metadata.getString("year"));
        assertEquals("", metadata.getString("source_url"));
        assertEquals("0", metadata.getString("chunk_index"));
        assertEquals(segments.get(0).text(), metadata.getString("content"));
        assertEquals("bge-m3-v1", metadata.getString("embedding_version"));
        assertEquals("general", metadata.getString("tags"));
        assertNotNull(metadata.getString("source_id"));
        assertFalse(metadata.getString("source_id").isBlank());
        assertNotNull(metadata.getString("source_name"));
        assertFalse(metadata.getString("source_name").isBlank());
        assertNotNull(metadata.getString("chunk_title"));
        assertFalse(metadata.getString("chunk_title").isBlank());
        assertNotNull(metadata.getString("content_hash"));
        assertEquals(64, metadata.getString("content_hash").length());
        assertInstanceOf(String.class, metadata.toMap().get("updated_at"));
        LocalDateTime.parse(metadata.getString("updated_at"));
        Set<String> allowedDocTypes = new HashSet<>(List.of(
            "adjustment_policy",
            "score_policy",
            "selection_method",
            "terminology",
            "general"
        ));
        assertTrue(allowedDocTypes.contains(metadata.getString("doc_type")));
    }

    @Test
    void shouldExposeUsefulStatusWhenQdrantBootstrapFails() throws Exception {
        ObjectProvider<EmbeddingModel> provider = mock(ObjectProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        QdrantCollectionService qdrant = mock(QdrantCollectionService.class);
        when(provider.getIfAvailable()).thenReturn(embeddingModel);
        when(qdrant.getCollectionName()).thenReturn("postgrad_rag_main");
        doThrow(new IllegalStateException("boom")).when(qdrant).ensureCollection();

        RagDocumentService service = newService(provider, store, qdrant);
        inject(service, "documentsPath", "rag-documents/");

        RagIndexResult result = service.reindex();

        assertEquals(0, result.getDocumentCount());
        assertEquals(0, result.getChunkCount());
        assertEquals("qdrant", service.getStatus().getBackend());
        assertEquals("postgrad_rag_main", service.getStatus().getCollectionName());
        assertFalse(service.getStatus().isReachable());
        assertFalse(service.getStatus().isIndexed());
        assertEquals("Qdrant unavailable: boom", service.getStatus().getLastError());
        assertNull(service.getStatus().getLastIndexedAt());
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
