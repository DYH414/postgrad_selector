package com.ruoyi.postgrad.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagDocumentService {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentService.class);

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    @Value("${app.ai.rag.indexing.documents-path:rag-documents/}")
    private String documentsPath;

    @Value("${app.ai.rag.indexing.chunk-size:500}")
    private int chunkSize;

    @Value("${app.ai.rag.indexing.chunk-overlap:50}")
    private int chunkOverlap;

    private final AtomicReference<RagStatus> statusRef = new AtomicReference<>(new RagStatus());

    public RagDocumentService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                              InMemoryEmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.embeddingStore = embeddingStore;
    }

    public boolean isReady() {
        return embeddingModelProvider.getIfAvailable() != null && statusRef.get().isIndexed();
    }

    public RagStatus getStatus() {
        RagStatus source = statusRef.get();
        RagStatus copy = new RagStatus();
        copy.setIndexed(source.isIndexed());
        copy.setEnabled(source.isEnabled());
        copy.setDocumentCount(source.getDocumentCount());
        copy.setChunkCount(source.getChunkCount());
        copy.setLastIndexedAt(source.getLastIndexedAt());
        copy.setLastError(source.getLastError());
        copy.setLastRunMode(source.getLastRunMode());
        return copy;
    }

    public RagIndexResult reindex() {
        RagStatus status = new RagStatus();
        status.setEnabled(true);
        status.setLastRunMode("manual");

        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            status.setEnabled(false);
            status.setIndexed(false);
            status.setLastError("Embedding API key not configured");
            statusRef.set(status);
            log.info("[RagInit] Embedding API key not configured, skipping RAG indexing.");
            return new RagIndexResult(0, 0);
        }

        List<Document> documents = loadDocuments();
        if (documents.isEmpty()) {
            status.setIndexed(false);
            status.setLastError("No RAG documents found");
            statusRef.set(status);
            log.warn("[RagInit] No documents found under classpath:{}, skipping indexing.", documentsPath);
            return new RagIndexResult(0, 0);
        }

        long startedAt = System.currentTimeMillis();
        log.info("[RagInit] Starting index: found {} documents in classpath:{}", documents.size(), documentsPath);

        try {
            embeddingStore.removeAll();
        } catch (Exception ex) {
            log.debug("[RagInit] removeAll skipped: {}", ex.getMessage());
        }

        try {
            EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(chunkSize, chunkOverlap))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(documents);

            int chunkCount = countSegments(documents);
            status.setIndexed(true);
            status.setDocumentCount(documents.size());
            status.setChunkCount(chunkCount);
            status.setLastIndexedAt(LocalDateTime.now());
            status.setLastError(null);
            statusRef.set(status);

            log.info("[RagInit] Chunking: {} docs -> ~{} segments (chunkSize={}, overlap={})",
                documents.size(), chunkCount, chunkSize, chunkOverlap);
            log.info("[RagInit] Index complete in {}ms", System.currentTimeMillis() - startedAt);
            return new RagIndexResult(documents.size(), chunkCount);
        } catch (Exception ex) {
            status.setIndexed(false);
            status.setLastError(ex.getMessage());
            statusRef.set(status);
            log.error("[RagInit] Indexing failed: {}", ex.getMessage(), ex);
            return new RagIndexResult(0, 0);
        }
    }

    private List<Document> loadDocuments() {
        try {
            List<Document> documents = ClassPathDocumentLoader.loadDocuments(documentsPath, new TextDocumentParser());
            return documents != null ? documents : Collections.emptyList();
        } catch (Exception ex) {
            log.error("[RagInit] Failed to load RAG documents from {}: {}", documentsPath, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    private int countSegments(List<Document> documents) {
        List<TextSegment> segments = new ArrayList<>();
        documents.forEach(document -> segments.addAll(DocumentSplitters.recursive(chunkSize, chunkOverlap).split(document)));
        return segments.size();
    }
}
