package com.ruoyi.postgrad.rag.service;

import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.reranker.SiliconFlowReranker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The single RAG retrieval pipeline for metadata enrichment, vector recall, reranking and selection.
 */
@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ObjectProvider<SiliconFlowReranker> rerankerProvider;
    private final ObjectProvider<RagQueryMetadataResolver> metadataResolverProvider;

    public RagRetrievalService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                               EmbeddingStore<TextSegment> embeddingStore,
                               ObjectProvider<SiliconFlowReranker> rerankerProvider,
                               ObjectProvider<RagQueryMetadataResolver> metadataResolverProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.embeddingStore = embeddingStore;
        this.rerankerProvider = rerankerProvider;
        this.metadataResolverProvider = metadataResolverProvider;
    }

    public RagRetrievalResult retrieve(RagSearchTestRequest request) {
        long t0 = System.currentTimeMillis();
        RagSearchTestResponse response = new RagSearchTestResponse();
        response.setQuery(request != null ? request.getQuery() : null);
        response.setRerankEnabled(rerankerProvider.getIfAvailable() != null);

        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            response.setErrorSummary("Query must not be blank");
            response.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return result(response, List.of());
        }

        enrichMetadataFilters(request);
        response.setAppliedFilters(request.getMetadataFilters());

        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            response.setErrorSummary("Embedding model is not available");
            response.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return result(response, List.of());
        }

        List<EmbeddingMatch<TextSegment>> retrievedMatches;
        try {
            long tEmbed = System.currentTimeMillis();
            Response<Embedding> embeddingResponse = embeddingModel.embed(request.getQuery());
            response.getLatency().setEmbeddingMs(System.currentTimeMillis() - tEmbed);

            long tRetrieve = System.currentTimeMillis();
            Filter filter = RagMetadataFilterBuilder.fromRequest(request);
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingResponse.content())
                .maxResults(defaultValue(request.getMaxResults(), 5))
                .minScore(defaultValue(request.getMinScore(), 0.6));
            if (filter != null) {
                builder.filter(filter);
            }
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(builder.build());
            response.getLatency().setRetrievalMs(System.currentTimeMillis() - tRetrieve);

            retrievedMatches = searchResult.matches();
            response.setRetrievedCount(retrievedMatches.size());
        } catch (Exception e) {
            log.warn("[RagRetrieval] Retrieval failed: {}", e.getMessage());
            response.setErrorSummary("Retrieval failed: " + e.getMessage());
            response.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return result(response, List.of());
        }

        if (retrievedMatches.isEmpty()) {
            response.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return result(response, List.of());
        }

        boolean rerankFailed = false;
        List<Double> rerankScores = null;
        List<TextSegment> retrievedSegments = retrievedMatches.stream()
            .map(EmbeddingMatch::embedded)
            .toList();
        SiliconFlowReranker reranker = request.isSkipRerank() ? null : rerankerProvider.getIfAvailable();
        if (reranker != null) {
            try {
                long tRerank = System.currentTimeMillis();
                rerankScores = reranker.scoreAll(retrievedSegments, request.getQuery()).content();
                response.getLatency().setRerankMs(System.currentTimeMillis() - tRerank);
            } catch (Exception e) {
                log.warn("[RagRetrieval] Rerank failed, falling back to vector order: {}", e.getMessage());
                rerankFailed = true;
                response.setRerankFailed(true);
                response.setErrorSummary("Rerank failed: " + e.getMessage());
            }
        }

        List<ChunkSelection> selections = buildChunkSelections(
            retrievedMatches, rerankScores, defaultValue(request.getRerankTopN(), 3), rerankFailed);
        List<ChunkSelection> orderedSelections = orderForResponse(selections);
        response.setChunks(orderedSelections.stream().map(ChunkSelection::chunk).toList());
        response.setSelectedCount((int) orderedSelections.stream().filter(ChunkSelection::selected).count());
        response.getLatency().setTotalMs(System.currentTimeMillis() - t0);
        return result(response, selectedSegments(orderedSelections));
    }

    private List<ChunkSelection> buildChunkSelections(List<EmbeddingMatch<TextSegment>> matches,
                                                       List<Double> rerankScores,
                                                       int rerankTopN,
                                                       boolean rerankFailed) {
        boolean hasValidRerank = rerankScores != null && !rerankFailed && rerankScores.size() == matches.size();
        List<Integer> rerankOrder = hasValidRerank ? rankedIndices(rerankScores) : List.of();
        List<ChunkSelection> selections = new ArrayList<>();

        for (int index = 0; index < matches.size(); index++) {
            EmbeddingMatch<TextSegment> match = matches.get(index);
            TextSegment segment = match.embedded();
            RagChunkResult chunk = toChunkResult(segment, index + 1, match.score());
            boolean selected;
            if (hasValidRerank) {
                int rerankPosition = rerankOrder.indexOf(index);
                chunk.setRerankRank(rerankPosition + 1);
                chunk.setRerankScore(rerankScores.get(index));
                selected = rerankPosition < rerankTopN;
            } else {
                chunk.setRerankRank(null);
                chunk.setRerankScore(null);
                selected = index < rerankTopN;
            }
            chunk.setSelectedForPrompt(selected);
            selections.add(new ChunkSelection(segment, chunk, selected));
        }
        return selections;
    }

    private static List<Integer> rankedIndices(List<Double> scores) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < scores.size(); index++) {
            indices.add(index);
        }
        indices.sort(Comparator.comparing(scores::get).reversed());
        return indices;
    }

    private static RagChunkResult toChunkResult(TextSegment segment, int vectorRank, double vectorScore) {
        var metadata = segment.metadata();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setVectorRank(vectorRank);
        chunk.setVectorScore(vectorScore);
        chunk.setSourceName(metadata != null ? metadata.getString("source_name") : null);
        chunk.setSourceId(metadata != null ? metadata.getString("source_id") : null);
        chunk.setSourceUrl(metadata != null ? metadata.getString("source_url") : null);
        chunk.setDocType(metadata != null ? metadata.getString("doc_type") : null);
        chunk.setSchoolId(metadata != null ? metadata.getString("school_id") : null);
        chunk.setProgramId(metadata != null ? metadata.getString("program_id") : null);
        chunk.setSchoolName(metadata != null ? metadata.getString("school_name") : null);
        chunk.setCollegeName(metadata != null ? metadata.getString("college_name") : null);
        chunk.setProgramName(metadata != null ? metadata.getString("program_name") : null);
        chunk.setYear(metadata != null ? metadata.getString("year") : null);
        chunk.setSection(metadata != null ? metadata.getString("section") : null);
        chunk.setChunkIndex(metadata != null ? metadata.getString("chunk_index") : null);
        chunk.setChunkTitle(metadata != null ? metadata.getString("chunk_title") : null);
        chunk.setContentHash(metadata != null ? metadata.getString("content_hash") : null);
        chunk.setContentPreview(preview(segment.text(), 200));
        return chunk;
    }

    private List<TextSegment> selectedSegments(List<ChunkSelection> selections) {
        return selections.stream()
            .filter(ChunkSelection::selected)
            .map(ChunkSelection::segment)
            .toList();
    }

    private static List<ChunkSelection> orderForResponse(List<ChunkSelection> selections) {
        return selections.stream()
            .sorted(Comparator.comparing(selection -> selection.chunk().getRerankRank(),
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private void enrichMetadataFilters(RagSearchTestRequest request) {
        if (metadataResolverProvider == null) {
            return;
        }
        RagQueryMetadataResolver resolver = metadataResolverProvider.getIfAvailable();
        if (resolver != null) {
            resolver.enrich(request);
        }
    }

    private static <T> T defaultValue(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static String preview(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private static RagRetrievalResult result(RagSearchTestResponse response, List<TextSegment> selectedSegments) {
        return new RagRetrievalResult(response, selectedSegments);
    }

    private record ChunkSelection(TextSegment segment, RagChunkResult chunk, boolean selected) {
    }
}
