package com.ruoyi.postgrad.rag.build;

import com.ruoyi.postgrad.rag.RagDocumentService;
import com.ruoyi.postgrad.rag.knowledge.RagDataQualityDocument;
import com.ruoyi.postgrad.rag.knowledge.RagKnowledgeExportService;
import com.ruoyi.postgrad.rag.knowledge.RagProgramHistoryDocument;
import com.ruoyi.postgrad.rag.knowledge.RagProgramYearFact;
import com.ruoyi.postgrad.rag.knowledge.StructuredRagChunk;
import com.ruoyi.postgrad.rag.knowledge.StructuredRagChunkBuilder;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Assembles a unified RAG corpus from general documents (classpath rag-documents/)
 * and database evidence (via RagKnowledgeExportService).
 *
 * <p>Each chunk carries unified metadata: source_type, source_id, source_name,
 * doc_type, section, chunk_index, content, content_hash, document_version,
 * embedding_version, indexed_at. Database chunks additionally carry school_id,
 * school_name, program_id, program_name, year. General chunks omit these fields.</p>
 */
@Component
public class RagCorpusAssembler {

    private static final Logger log = LoggerFactory.getLogger(RagCorpusAssembler.class);

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final int DEFAULT_DB_LIMIT = 1000;
    private static final String SCHEMA_VERSION = "rag-doc-v2";
    private static final String EMBEDDING_VERSION = "bge-m3-v1";

    private final RagDocumentService ragDocumentService;
    private final RagKnowledgeExportService ragKnowledgeExportService;
    private final StructuredRagChunkBuilder structuredChunkBuilder;
    private final String ragDataDir;

    // Package-private for test injection via reflection
    String documentsPath = "rag-documents/";

    public RagCorpusAssembler(
            RagDocumentService ragDocumentService,
            RagKnowledgeExportService ragKnowledgeExportService,
            StructuredRagChunkBuilder structuredChunkBuilder,
            @Value("${app.ai.rag.data-dir:./rag-data}") String ragDataDir) {
        this.ragDocumentService = ragDocumentService;
        this.ragKnowledgeExportService = ragKnowledgeExportService;
        this.structuredChunkBuilder = structuredChunkBuilder;
        this.ragDataDir = ragDataDir;
    }

    /**
     * Assembles a unified corpus for the given year range.
     *
     * @param startYear inclusive start year
     * @param endYear   inclusive end year
     * @return AssembledCorpus containing all chunks and source document counts
     */
    public AssembledCorpus assemble(int startYear, int endYear) {
        LocalDateTime indexedAt = LocalDateTime.now();
        List<TextSegment> allSegments = new ArrayList<>();

        // 1. General documents
        List<Document> documents = loadClasspathDocuments();
        List<TextSegment> generalSegments = splitGeneralDocuments(documents, indexedAt);
        allSegments.addAll(generalSegments);

        // 2. Database documents
        List<StructuredRagChunk> dbChunks = new ArrayList<>();
        int databaseDocCount = 0;

        List<RagProgramHistoryDocument> histories =
            ragKnowledgeExportService.exportProgramHistoryDocuments(startYear, endYear, DEFAULT_DB_LIMIT);
        for (RagProgramHistoryDocument history : histories) {
            dbChunks.addAll(structuredChunkBuilder.buildProgramHistoryChunks(history));
            databaseDocCount++;
        }

        List<RagProgramYearFact> facts =
            ragKnowledgeExportService.exportProgramYearFacts(endYear, DEFAULT_DB_LIMIT);
        for (RagProgramYearFact fact : facts) {
            dbChunks.addAll(structuredChunkBuilder.buildProgramYearChunks(fact));
            databaseDocCount++;
        }

        List<RagDataQualityDocument> qualities =
            ragKnowledgeExportService.exportDataQualityDocuments(startYear, endYear, DEFAULT_DB_LIMIT);
        for (RagDataQualityDocument quality : qualities) {
            dbChunks.addAll(structuredChunkBuilder.buildDataQualityChunks(quality));
            databaseDocCount++;
        }

        List<TextSegment> dbSegments = toDbSegments(dbChunks, indexedAt);
        allSegments.addAll(dbSegments);

        // 3. Write external Markdown mirrors
        writeExternalMarkdown(dbChunks);

        // 4. Validate source_id uniqueness
        validateSourceIds(allSegments);

        log.info("[RagCorpusAssembler] Assembled corpus: {} general docs -> {} segments, {} database docs -> {} segments",
            documents.size(), generalSegments.size(), databaseDocCount, dbSegments.size());

        return new AssembledCorpus(allSegments, documents.size(), databaseDocCount);
    }

    // ---- General document loading & splitting ----

    private List<Document> loadClasspathDocuments() {
        try {
            List<Document> docs = ClassPathDocumentLoader.loadDocuments(documentsPath, new TextDocumentParser());
            return docs != null ? docs : Collections.emptyList();
        } catch (Exception ex) {
            log.warn("[RagCorpusAssembler] No documents found at classpath:{} : {}", documentsPath, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<TextSegment> splitGeneralDocuments(List<Document> documents, LocalDateTime indexedAt) {
        DocumentSplitter splitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);
        List<TextSegment> result = new ArrayList<>();
        String indexedAtStr = indexedAt.toString();
        for (Document document : documents) {
            List<TextSegment> segments = splitter.split(document);
            String sourceName = sourceName(document);
            String docType = inferDocType(sourceName);
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                Metadata metadata = segment.metadata().copy();
                metadata.put("source_type", "general");
                metadata.put("source_id", "general:" + sourceName + ":" + i);
                metadata.put("source_name", sourceName);
                metadata.put("doc_type", docType);
                metadata.put("section", "");
                metadata.put("chunk_index", String.valueOf(i));
                metadata.put("content", segment.text());
                metadata.put("content_hash", sha256(segment.text()));
                metadata.put("document_version", SCHEMA_VERSION);
                metadata.put("embedding_version", EMBEDDING_VERSION);
                metadata.put("indexed_at", indexedAtStr);
                // General chunks omit school_id, school_name, program_id, program_name, year
                result.add(TextSegment.from(segment.text(), metadata));
            }
        }
        return result;
    }

    // ---- Database document conversion ----

    private List<TextSegment> toDbSegments(List<StructuredRagChunk> chunks, LocalDateTime indexedAt) {
        List<TextSegment> segments = new ArrayList<>();
        String indexedAtStr = indexedAt.toString();
        for (StructuredRagChunk chunk : chunks) {
            Metadata metadata = new Metadata();
            // Copy all existing metadata from the structured chunk
            for (Map.Entry<String, String> entry : chunk.getMetadata().entrySet()) {
                metadata.put(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }
            // Override unified fields
            metadata.put("source_type", "database");
            metadata.put("source_id", chunk.getChunkId());
            metadata.put("content", chunk.getText());
            metadata.put("content_hash", sha256(chunk.getText()));
            metadata.put("indexed_at", indexedAtStr);
            metadata.put("embedding_version", EMBEDDING_VERSION);
            // Existing metadata from chunk builder already provides:
            // school_id, school_name, program_id, program_name, year, section,
            // doc_type, source_name, document_version (as schema_version), etc.
            segments.add(TextSegment.from(chunk.getText(), metadata));
        }
        return segments;
    }

    // ---- External Markdown output ----

    private void writeExternalMarkdown(List<StructuredRagChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        try {
            Path generatedDir = Path.of(ragDataDir, "generated");
            Files.createDirectories(generatedDir);
            for (StructuredRagChunk chunk : chunks) {
                String fileName = chunk.getChunkId()
                    .replace(":", "_")
                    .replace("/", "_")
                    .replace("\\", "_") + ".md";
                Path filePath = generatedDir.resolve(fileName);
                Files.writeString(filePath, chunk.getText(), StandardCharsets.UTF_8);
            }
            log.info("[RagCorpusAssembler] Wrote {} external markdown files to {}", chunks.size(), generatedDir);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Failed to write external Markdown to " + ragDataDir + "/generated/: " + ex.getMessage(), ex);
        }
    }

    // ---- Validation ----

    private void validateSourceIds(List<TextSegment> segments) {
        Set<String> ids = new HashSet<>();
        for (TextSegment segment : segments) {
            String sourceId = segment.metadata().getString("source_id");
            if (!ids.add(sourceId)) {
                throw new IllegalStateException(
                    "Duplicate source_id detected: '" + sourceId + "'. "
                        + "Corpus assembly requires unique source_id across all chunks.");
            }
        }
    }

    // ---- Utilities (mirrored from RagDocumentService) ----

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
                // fall through
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

    private static String sha256(String text) {
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
