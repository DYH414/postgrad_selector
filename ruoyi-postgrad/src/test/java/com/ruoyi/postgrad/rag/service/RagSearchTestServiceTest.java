package com.ruoyi.postgrad.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.reranker.SiliconFlowReranker;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class RagSearchTestServiceTest {

    @Test
    void shouldApplyMetadataFiltersAndExposeBusinessMetadata() {
        ObjectProvider<EmbeddingModel> embeddingProvider = mock(ObjectProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingProvider.getIfAvailable()).thenReturn(embeddingModel);
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embed("南京大学软件工程复试线")).thenReturn(Response.from(queryEmbedding));

        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        TextSegment segment = TextSegment.from("复试线: 340", new Metadata()
            .put("source_name", "南京大学 2025 软件工程录取数据")
            .put("source_id", "program_year_summary:1:101:2025:admission_score")
            .put("source_url", "https://example.com/source")
            .put("doc_type", "program_year_summary")
            .put("school_name", "南京大学")
            .put("college_name", "计算机科学与技术系")
            .put("program_name", "软件工程")
            .put("year", "2025")
            .put("section", "admission_score")
            .put("chunk_index", "2")
            .put("chunk_title", "南京大学 软件工程 2025 - 复试线")
            .put("content_hash", "abc123"));
        when(store.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(
            new EmbeddingMatch<>(0.88, "id-1", queryEmbedding, segment)
        )));

        ObjectProvider<SiliconFlowReranker> rerankerProvider = mock(ObjectProvider.class);
        when(rerankerProvider.getIfAvailable()).thenReturn(null);

        RagSearchTestRequest request = new RagSearchTestRequest("南京大学软件工程复试线", 5, 0.5, 1);
        request.setSchoolId("1");
        request.setProgramId("101");
        request.setYear("2025");
        request.setSection("admission_score");

        RagSearchTestResponse response = new RagSearchTestService(embeddingProvider, store, rerankerProvider)
            .searchTest(request);

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(store).search(captor.capture());
        assertNotNull(captor.getValue().filter());
        assertEquals(Map.of(
            "school_id", "1",
            "program_id", "101",
            "year", "2025",
            "section", "admission_score"
        ), response.getAppliedFilters());

        RagChunkResult chunk = response.getChunks().get(0);
        assertEquals("南京大学", chunk.getSchoolName());
        assertEquals("计算机科学与技术系", chunk.getCollegeName());
        assertEquals("软件工程", chunk.getProgramName());
        assertEquals("2025", chunk.getYear());
        assertEquals("admission_score", chunk.getSection());
        assertEquals("https://example.com/source", chunk.getSourceUrl());
    }

    @Test
    void shouldInferSchoolFilterBeforeVectorSearch() {
        ObjectProvider<EmbeddingModel> embeddingProvider = mock(ObjectProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingProvider.getIfAvailable()).thenReturn(embeddingModel);
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embed("深圳大学")).thenReturn(Response.from(queryEmbedding));

        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        TextSegment segment = TextSegment.from("学校: 深圳大学", new Metadata()
            .put("source_name", "深圳大学 2025 电子信息")
            .put("school_id", "15")
            .put("school_name", "深圳大学"));
        when(store.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(
            new EmbeddingMatch<>(0.9, "id-15", queryEmbedding, segment)
        )));

        ObjectProvider<SiliconFlowReranker> rerankerProvider = mock(ObjectProvider.class);
        when(rerankerProvider.getIfAvailable()).thenReturn(null);

        RagQueryMetadataResolver resolver = mock(RagQueryMetadataResolver.class);
        ObjectProvider<RagQueryMetadataResolver> resolverProvider = mock(ObjectProvider.class);
        when(resolverProvider.getIfAvailable()).thenReturn(resolver);

        RagSearchTestRequest request = new RagSearchTestRequest("深圳大学", 5, 0.5, 1);
        org.mockito.Mockito.doAnswer(invocation -> {
            RagSearchTestRequest req = invocation.getArgument(0);
            req.setSchoolId("15");
            return null;
        }).when(resolver).enrich(request);

        RagSearchTestResponse response = new RagSearchTestService(
            embeddingProvider, store, rerankerProvider, resolverProvider)
            .searchTest(request);

        verify(resolver).enrich(request);
        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(store).search(captor.capture());
        assertNotNull(captor.getValue().filter());
        assertEquals(Map.of("school_id", "15"), response.getAppliedFilters());
        assertEquals("深圳大学", response.getChunks().get(0).getSchoolName());
    }
}
