package com.ruoyi.postgrad.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.ruoyi.postgrad.rag.qdrant.QdrantCollectionService;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final QdrantCollectionService qdrantCollectionService;

    @Value("${app.ai.rag.indexing.documents-path:rag-documents/}")
    private String documentsPath;

    @Value("${app.ai.rag.indexing.chunk-size:500}")
    private int chunkSize;

    @Value("${app.ai.rag.indexing.chunk-overlap:50}")
    private int chunkOverlap;

    private final AtomicReference<RagStatus> statusRef = new AtomicReference<>(new RagStatus());

    public RagDocumentService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                              EmbeddingStore<TextSegment> embeddingStore,
                              QdrantCollectionService qdrantCollectionService) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.embeddingStore = embeddingStore;
        this.qdrantCollectionService = qdrantCollectionService;
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
        copy.setBackend(source.getBackend());
        copy.setCollectionName(source.getCollectionName());
        copy.setReachable(source.isReachable());
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

        try {
            qdrantCollectionService.ensureCollection();
            qdrantCollectionService.clearCollection();
            status.setBackend("qdrant");
            status.setCollectionName(qdrantCollectionService.getCollectionName());
            status.setReachable(qdrantCollectionService.isReachable());
        } catch (Exception ex) {
            status.setBackend("qdrant");
            status.setCollectionName(qdrantCollectionService.getCollectionName());
            status.setReachable(false);
            status.setIndexed(false);
            status.setLastError("Qdrant unavailable: " + ex.getMessage());
            statusRef.set(status);
            log.error("[RagInit] Qdrant unavailable, skipping RAG indexing: {}", ex.getMessage(), ex);
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
            LocalDateTime indexedAt = LocalDateTime.now();
            List<TextSegment> segments = splitWithMetadata(documents, indexedAt);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            List<String> ids = segmentIds(segments);
            embeddingStore.addAll(ids, embeddings, segments);

            int chunkCount = segments.size();
            status.setIndexed(true);
            status.setDocumentCount(documents.size());
            status.setChunkCount(chunkCount);
            status.setLastIndexedAt(indexedAt);
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

    private List<TextSegment> splitWithMetadata(List<Document> documents, LocalDateTime indexedAt) {
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> result = new ArrayList<>();
        String updatedAt = indexedAt.toString();
        for (Document document : documents) {
            List<TextSegment> segments = splitter.split(document);
            String sourceName = sourceName(document);
            String docType = inferDocType(sourceName);
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                Metadata metadata = segment.metadata().copy();
                metadata.put("school_id", "");
                metadata.put("school_name", "");
                metadata.put("major_id", "");
                metadata.put("major_name", "");
                metadata.put("doc_type", docType);
                metadata.put("year", "");
                metadata.put("source_id", sourceName);
                metadata.put("source_name", sourceName);
                metadata.put("source_url", "");
                metadata.put("chunk_index", String.valueOf(i));
                metadata.put("chunk_title", sourceName);
                metadata.put("content", segment.text());
                metadata.put("content_hash", sha256(segment.text()));
                metadata.put("embedding_version", "bge-m3-v1");
                metadata.put("updated_at", updatedAt);
                metadata.put("tags", "general");
                result.add(TextSegment.from(segment.text(), metadata));
            }
        }
        return result;
    }

    private List<String> segmentIds(List<TextSegment> segments) {
        List<String> ids = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            Metadata metadata = segment.metadata();
            ids.add(UUID.nameUUIDFromBytes((metadata.getString("source_id")
                + ":" + metadata.getString("chunk_index")
                + ":" + metadata.getString("content_hash"))
                .getBytes(StandardCharsets.UTF_8)).toString());
        }
        return ids;
    }

    private String sourceName(Document document) {
        String fileName = document.metadata().getString("file_name");
        if (fileName != null && !fileName.isBlank()) {
            return decodeIfEncoded(fileName);
        }
        String source = document.metadata().getString("source");
        if (source != null && !source.isBlank()) {
            return decodeIfEncoded(source);
        }
        return "unknown";
    }

    private static String decodeIfEncoded(String name) {
        if (name.contains("%")) {
            try {
                return URLDecoder.decode(name, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        return name;
    }

    private String inferDocType(String sourceName) {
        if (sourceName.contains("调剂")) {
            return "adjustment_policy";
        }
        if (sourceName.contains("国家线") || sourceName.contains("复试")) {
            return "score_policy";
        }
        if (sourceName.contains("择校")) {
            return "selection_method";
        }
        if (sourceName.contains("术语")) {
            return "terminology";
        }
        return "general";
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

}
