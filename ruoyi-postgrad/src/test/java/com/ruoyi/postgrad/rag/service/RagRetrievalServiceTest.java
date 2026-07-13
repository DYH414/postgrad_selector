package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.reranker.SiliconFlowReranker;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RagRetrievalServiceTest {

    @Test
    void retrieveShouldResolveSchoolFilterBeforeVectorSearch() {
        ObjectProvider<EmbeddingModel> embeddingProvider = embeddingProvider("深圳大学300分左右有什么专业");
        String fullContent = "完整上下文内容，不能只保留预览。".repeat(20);
        EmbeddingStore<TextSegment> embeddingStore = embeddingStore(List.of(segment(fullContent)));
        ObjectProvider<SiliconFlowReranker> rerankerProvider = provider(null);
        RagQueryMetadataResolver metadataResolver = mock(RagQueryMetadataResolver.class);
        ObjectProvider<RagQueryMetadataResolver> metadataResolverProvider = provider(metadataResolver);
        doAnswer(invocation -> {
            invocation.<RagSearchTestRequest>getArgument(0).setSchoolId("15");
            return null;
        }).when(metadataResolver).enrich(any(RagSearchTestRequest.class));

        RagRetrievalService service = new RagRetrievalService(
            embeddingProvider, embeddingStore, rerankerProvider, metadataResolverProvider);

        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学300分左右有什么专业", 5, 0.0, 3);
        RagRetrievalResult result = service.retrieve(request);

        assertEquals("15", result.response().getAppliedFilters().get("school_id"));
        assertEquals(fullContent, result.selectedSegments().get(0).text());
        assertTrue(result.response().getChunks().get(0).getContentPreview().length() < fullContent.length());
        verify(embeddingStore).search(argThat(search -> search.filter() != null));
    }

    @Test
    void retrieveShouldKeepVectorOrderWhenRerankFails() {
        ObjectProvider<EmbeddingModel> embeddingProvider = embeddingProvider("深圳大学");
        TextSegment first = segment("第一条完整内容");
        TextSegment second = segment("第二条完整内容");
        EmbeddingStore<TextSegment> embeddingStore = embeddingStore(List.of(first, second));
        SiliconFlowReranker reranker = mock(SiliconFlowReranker.class);
        when(reranker.scoreAll(anyList(), eq("深圳大学")))
            .thenThrow(new IllegalStateException("upstream timeout"));

        RagRetrievalService service = new RagRetrievalService(
            embeddingProvider, embeddingStore, provider(reranker), provider(null));

        RagSearchTestResponse response = service.retrieve(
            new RagSearchTestRequest("深圳大学", 2, 0.0, 2)).response();

        assertTrue(response.isRerankFailed());
        assertEquals(2, response.getSelectedCount());
        assertEquals(1, response.getChunks().get(0).getVectorRank());
        assertNotNull(response.getChunks().get(0).getContentPreview());
    }

    @Test
    void retrieveShouldSkipRerankerAndPreserveVectorOrderWhenRequested() {
        ObjectProvider<EmbeddingModel> embeddingProvider = embeddingProvider("深圳大学");
        TextSegment first = segment("第一条完整内容", "source-a");
        TextSegment second = segment("第二条完整内容", "source-b");
        EmbeddingStore<TextSegment> embeddingStore = embeddingStore(List.of(first, second));
        SiliconFlowReranker reranker = mock(SiliconFlowReranker.class);

        RagRetrievalService service = new RagRetrievalService(
            embeddingProvider, embeddingStore, provider(reranker), provider(null));
        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学", 2, 0.0, 2);
        request.setSkipRerank(true);

        RagRetrievalResult result = service.retrieve(request);

        assertEquals(List.of("source-a", "source-b"), result.selectedSegments().stream()
            .map(segment -> segment.metadata().getString("source_id"))
            .toList());
        verifyNoInteractions(reranker);
    }

    @Test
    void retrieveShouldKeepSelectedChunksAndFullSegmentsInRerankOrder() {
        ObjectProvider<EmbeddingModel> embeddingProvider = embeddingProvider("深圳大学风险");
        TextSegment vectorFirst = segment("向量第一条", "source-a");
        TextSegment vectorSecond = segment("向量第二条", "source-b");
        EmbeddingStore<TextSegment> embeddingStore = embeddingStore(List.of(vectorFirst, vectorSecond));
        SiliconFlowReranker reranker = mock(SiliconFlowReranker.class);
        when(reranker.scoreAll(anyList(), eq("深圳大学风险")))
            .thenReturn(Response.from(List.of(0.2D, 0.9D)));

        RagRetrievalService service = new RagRetrievalService(
            embeddingProvider, embeddingStore, provider(reranker), provider(null));

        RagRetrievalResult result = service.retrieve(
            new RagSearchTestRequest("深圳大学风险", 2, 0.0, 2));

        assertEquals(List.of("source-b", "source-a"), result.response().getChunks().stream()
            .filter(chunk -> chunk.isSelectedForPrompt())
            .map(chunk -> chunk.getSourceId())
            .toList());
        assertEquals(List.of("source-b", "source-a"), result.selectedSegments().stream()
            .map(segment -> segment.metadata().getString("source_id"))
            .toList());
        assertEquals(2, result.response().getChunks().get(0).getVectorRank());
        assertEquals(1, result.response().getChunks().get(1).getVectorRank());
    }

    private static ObjectProvider<EmbeddingModel> embeddingProvider(String query) {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(query)).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        return provider(embeddingModel);
    }

    private static EmbeddingStore<TextSegment> embeddingStore(List<TextSegment> segments) {
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        List<EmbeddingMatch<TextSegment>> matches = segments.stream()
            .map(segment -> new EmbeddingMatch<>(0.9, "id-" + segment.text(), queryEmbedding, segment))
            .toList();
        when(store.search(any())).thenReturn(new EmbeddingSearchResult<>(matches));
        return store;
    }

    private static TextSegment segment(String text) {
        return segment(text, "source-" + text);
    }

    private static TextSegment segment(String text, String sourceId) {
        return TextSegment.from(text, new Metadata()
            .put("source_name", "深圳大学招生数据")
            .put("source_id", sourceId));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
