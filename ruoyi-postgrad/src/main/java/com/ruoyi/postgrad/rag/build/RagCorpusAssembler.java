package com.ruoyi.postgrad.rag.build;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Assembles a unified RAG corpus from general documents (classpath rag-documents/).
 *
 * <p>Structured data (admission scores, plans, quality) is served via ChatTools
 * from MySQL directly — no longer duplicated in RAG. RAG focuses on unstructured
 * knowledge: 招生简章, policies, methodology guides, etc.</p>
 *
 * <p>Each chunk carries unified metadata: source_type, source_id, source_name,
 * doc_type, section, chunk_index, content, content_hash, document_version,
 * embedding_version, indexed_at.</p>
 */
@Component
public class RagCorpusAssembler {

    private static final Logger log = LoggerFactory.getLogger(RagCorpusAssembler.class);

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final String SCHEMA_VERSION = "rag-doc-v3";
    private static final String EMBEDDING_VERSION = "bge-m3-v1";
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");
    private static final Pattern SOURCE_URL_PATTERN =
        Pattern.compile("(?m)^>\\s*来源\\s*:\\s*(https?://\\S+)\\s*$");
    private static final String ADMISSION_GUIDE_SUFFIX = "-招生简章.md";

    // Package-private for test injection via reflection
    String documentsPath = "rag-documents/";

    public RagCorpusAssembler() {
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

        // General documents from rag-documents/ (招生简章, policies, methodology)
        List<Document> documents = loadClasspathDocuments();
        List<TextSegment> generalSegments = splitGeneralDocuments(documents, indexedAt);
        allSegments.addAll(generalSegments);

        // Validate source_id uniqueness
        validateSourceIds(allSegments);

        log.info("[RagCorpusAssembler] Assembled corpus: {} general docs -> {} segments (structured data served via Tools)",
            documents.size(), generalSegments.size());

        return new AssembledCorpus(allSegments, documents.size(), 0);
    }

    // ---- General document loading & splitting ----

    private List<Document> loadClasspathDocuments() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = "classpath*:" + documentsPath + "**/*.md";
            Resource[] resources = resolver.getResources(pattern);
            List<Document> docs = new ArrayList<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                String content;
                try (InputStream is = resource.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    content = sb.toString();
                }
                Metadata metadata = new Metadata();
                metadata.put("file_name", filename);
                docs.add(Document.from(content, metadata));
            }
            return docs;
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
            String schoolName = inferSchoolName(sourceName);
            String sourceType = schoolName != null ? "school_guide" : "general";
            String year = extractYear(document.text());
            String sourceUrl = extractSourceUrl(document.text());
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                Metadata metadata = segment.metadata().copy();
                metadata.put("source_type", sourceType);
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
                if (schoolName != null) metadata.put("school_name", schoolName);
                if (year != null) metadata.put("year", year);
                if (sourceUrl != null) metadata.put("source_url", sourceUrl);
                // Unstructured chunks intentionally omit program_id/program_name.
                result.add(TextSegment.from(segment.text(), metadata));
            }
        }
        return result;
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

    // ---- Utilities ----

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
        if (sourceName.contains("招生简章")) {
            return "admission_guide";
        }
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
        if (sourceName.contains("408") || sourceName.contains("考试")) {
            return "exam_intro";
        }
        return "general";
    }

    private String inferSchoolName(String sourceName) {
        if (sourceName != null && sourceName.endsWith(ADMISSION_GUIDE_SUFFIX)) {
            return sourceName.substring(0, sourceName.length() - ADMISSION_GUIDE_SUFFIX.length()).trim();
        }
        return null;
    }

    private String extractYear(String content) {
        if (content == null) return null;
        Matcher matcher = YEAR_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractSourceUrl(String content) {
        if (content == null) return null;
        Matcher matcher = SOURCE_URL_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
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
