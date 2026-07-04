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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAG 检索测试服务 —— 不经过 LLM，直接测试 Embedding → Qdrant → Rerank 管道。
 */
@Service
public class RagSearchTestService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchTestService.class);

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ObjectProvider<SiliconFlowReranker> rerankerProvider;

    public RagSearchTestService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                EmbeddingStore<TextSegment> embeddingStore,
                                ObjectProvider<SiliconFlowReranker> rerankerProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.embeddingStore = embeddingStore;
        this.rerankerProvider = rerankerProvider;
    }

    /**
     * 执行检索测试。
     */
    public RagSearchTestResponse searchTest(RagSearchTestRequest request) {
        long t0 = System.currentTimeMillis();
        RagSearchTestResponse resp = new RagSearchTestResponse();
        resp.setQuery(request.getQuery());
        resp.setRerankEnabled(rerankerProvider.getIfAvailable() != null);

        Integer maxResults = request.getMaxResults() != null ? request.getMaxResults() : 5;
        Double minScore = request.getMinScore() != null ? request.getMinScore() : 0.6;
        Integer rerankTopN = request.getRerankTopN() != null ? request.getRerankTopN() : 3;

        // 1. 验证查询
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            resp.setErrorSummary("Query must not be blank");
            resp.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return resp;
        }

        // 2. 获取 Embedding 模型
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            resp.setErrorSummary("Embedding model is not available");
            resp.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return resp;
        }

        // 3. 向量检索
        List<TextSegment> retrievedSegments;
        try {
            long tEmbed = System.currentTimeMillis();
            Response<Embedding> embResp = embeddingModel.embed(query);
            resp.getLatency().setEmbeddingMs(System.currentTimeMillis() - tEmbed);

            long tRetrieve = System.currentTimeMillis();
            EmbeddingSearchRequest searchReq = EmbeddingSearchRequest.builder()
                .queryEmbedding(embResp.content())
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchReq);
            resp.getLatency().setRetrievalMs(System.currentTimeMillis() - tRetrieve);

            retrievedSegments = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
                retrievedSegments.add(match.embedded());
            }
            resp.setRetrievedCount(retrievedSegments.size());
        } catch (Exception e) {
            log.warn("[RagSearchTest] Retrieval failed: {}", e.getMessage());
            resp.setErrorSummary("Retrieval failed: " + e.getMessage());
            resp.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return resp;
        }

        if (retrievedSegments.isEmpty()) {
            resp.getLatency().setTotalMs(System.currentTimeMillis() - t0);
            return resp;
        }

        // 4. 可选重排序
        boolean rerankFailed = false;
        List<Double> rerankScores = null;
        SiliconFlowReranker reranker = rerankerProvider.getIfAvailable();
        if (reranker != null) {
            try {
                long tRerank = System.currentTimeMillis();
                Response<List<Double>> scoreResp = reranker.scoreAll(retrievedSegments, query);
                rerankScores = scoreResp.content();
                resp.getLatency().setRerankMs(System.currentTimeMillis() - tRerank);
            } catch (Exception e) {
                log.warn("[RagSearchTest] Rerank failed, falling back to vector order: {}", e.getMessage());
                rerankFailed = true;
                resp.setRerankFailed(true);
                resp.setErrorSummary("Rerank failed: " + e.getMessage());
            }
        }

        // 5. 构建 chunk 结果（带排序）
        List<RagChunkResult> chunks = buildChunkResults(retrievedSegments, rerankScores,
            rerankTopN, rerankFailed);
        resp.setSelectedCount((int) chunks.stream().filter(RagChunkResult::isSelectedForPrompt).count());
        resp.setChunks(chunks);
        resp.getLatency().setTotalMs(System.currentTimeMillis() - t0);
        return resp;
    }

    private List<RagChunkResult> buildChunkResults(List<TextSegment> segments,
                                                    List<Double> rerankScores,
                                                    int rerankTopN,
                                                    boolean rerankFailed) {
        List<RagChunkResult> results = new ArrayList<>();
        boolean hasValidRerank = rerankScores != null && !rerankFailed;

        // 计算 rerank 排序索引
        List<Integer> rerankOrder = null;
        if (hasValidRerank && rerankScores.size() == segments.size()) {
            rerankOrder = new ArrayList<>();
            for (int i = 0; i < rerankScores.size(); i++) rerankOrder.add(i);
            rerankOrder.sort((a, b) -> Double.compare(rerankScores.get(b), rerankScores.get(a)));
        }

        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            var metadata = seg.metadata();

            RagChunkResult chunk = new RagChunkResult();
            chunk.setVectorRank(i + 1);
            chunk.setSourceName(metadata != null ? metadata.getString("source_name") : null);
            chunk.setSourceId(metadata != null ? metadata.getString("source_id") : null);
            chunk.setDocType(metadata != null ? metadata.getString("doc_type") : null);
            chunk.setChunkIndex(metadata != null ? metadata.getString("chunk_index") : null);
            chunk.setChunkTitle(metadata != null ? metadata.getString("chunk_title") : null);
            chunk.setContentHash(metadata != null ? metadata.getString("content_hash") : null);
            chunk.setContentPreview(preview(seg.text(), 200));

            if (hasValidRerank && rerankOrder != null) {
                int rerankPos = rerankOrder.indexOf(i);
                chunk.setRerankRank(rerankPos >= 0 ? rerankPos + 1 : null);
                chunk.setRerankScore(rerankScores.get(i));
                chunk.setSelectedForPrompt(rerankPos >= 0 && rerankPos < rerankTopN);
            } else {
                chunk.setRerankRank(null);
                chunk.setRerankScore(null);
                chunk.setSelectedForPrompt(i < rerankTopN);
            }

            results.add(chunk);
        }
        return results;
    }

    private static String safeString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String preview(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private static String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
