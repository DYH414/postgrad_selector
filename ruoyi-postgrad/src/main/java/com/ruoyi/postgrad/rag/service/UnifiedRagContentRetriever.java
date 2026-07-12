package com.ruoyi.postgrad.rag.service;

import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;

/**
 * Adapts the unified retrieval pipeline for LangChain4j generic chat augmentation.
 */
public class UnifiedRagContentRetriever implements ContentRetriever {

    private final RagRetrievalService retrievalService;
    private final int maxResults;
    private final double minScore;
    private final int rerankTopN;

    public UnifiedRagContentRetriever(RagRetrievalService retrievalService,
                                      int maxResults,
                                      double minScore,
                                      int rerankTopN) {
        this.retrievalService = retrievalService;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.rerankTopN = rerankTopN;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        RagSearchTestRequest request = new RagSearchTestRequest(
            query.text(), maxResults, minScore, rerankTopN);
        return retrievalService.retrieve(request).selectedSegments().stream()
            .map(Content::from)
            .toList();
    }
}
