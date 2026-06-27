package com.ruoyi.framework.config;

import com.ruoyi.postgrad.rag.reranker.SiliconFlowReranker;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RagConfig {

    @Bean
    public InMemoryEmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public ContentRetriever contentRetriever(InMemoryEmbeddingStore<TextSegment> embeddingStore,
                                             EmbeddingModel embeddingModel,
                                             @Value("${app.ai.rag.retrieval.max-results:5}") int maxResults,
                                             @Value("${app.ai.rag.retrieval.min-score:0.6}") double minScore) {
        return EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(maxResults)
            .minScore(minScore)
            .build();
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public SiliconFlowReranker siliconFlowReranker(
        @Value("${app.ai.rag.rerank.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
        @Value("${app.ai.rag.rerank.api-key:}") String apiKey,
        @Value("${app.ai.rag.rerank.model-name:BAAI/bge-reranker-v2-m3}") String modelName,
        @Value("${app.ai.rag.rerank.max-chunks-per-doc:1024}") int maxChunksPerDoc,
        @Value("${app.ai.rag.rerank.overlap-tokens:20}") int overlapTokens,
        @Value("${app.ai.rag.rerank.timeout-seconds:120}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        return new SiliconFlowReranker(new RestTemplate(factory), baseUrl, apiKey, modelName, maxChunksPerDoc, overlapTokens);
    }

    @Bean
    @ConditionalOnBean(ContentRetriever.class)
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever,
                                                 ObjectProvider<SiliconFlowReranker> rerankerProvider,
                                                 @Value("${app.ai.rag.retrieval.rerank-top-n:3}") int rerankTopN,
                                                 @Value("${app.ai.rag.retrieval.rerank-min-score:0.0}") double rerankMinScore) {
        DefaultRetrievalAugmentor.DefaultRetrievalAugmentorBuilder builder = DefaultRetrievalAugmentor.builder()
            .contentRetriever(contentRetriever);

        SiliconFlowReranker reranker = rerankerProvider.getIfAvailable();
        if (reranker != null) {
            builder.contentAggregator(ReRankingContentAggregator.builder()
                .scoringModel(reranker)
                .minScore(rerankMinScore)
                .maxResults(rerankTopN)
                .build());
        }
        return builder.build();
    }
}
