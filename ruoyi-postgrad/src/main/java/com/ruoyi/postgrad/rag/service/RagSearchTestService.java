package com.ruoyi.postgrad.rag.service;

import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.reranker.SiliconFlowReranker;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade for the existing RAG administration API.
 */
@Service
public class RagSearchTestService {

    private final RagRetrievalService ragRetrievalService;

    @Autowired
    public RagSearchTestService(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    public RagSearchTestService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                EmbeddingStore<TextSegment> embeddingStore,
                                ObjectProvider<SiliconFlowReranker> rerankerProvider) {
        this(embeddingModelProvider, embeddingStore, rerankerProvider, null);
    }

    public RagSearchTestService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                EmbeddingStore<TextSegment> embeddingStore,
                                ObjectProvider<SiliconFlowReranker> rerankerProvider,
                                ObjectProvider<RagQueryMetadataResolver> metadataResolverProvider) {
        this(new RagRetrievalService(
            embeddingModelProvider, embeddingStore, rerankerProvider, metadataResolverProvider));
    }

    public RagSearchTestResponse searchTest(RagSearchTestRequest request) {
        return ragRetrievalService.retrieve(request).response();
    }
}
