package com.ruoyi.postgrad.rag.build;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

@FunctionalInterface
public interface RagEmbeddingStoreFactory {

    EmbeddingStore<TextSegment> create(String collectionName);
}
