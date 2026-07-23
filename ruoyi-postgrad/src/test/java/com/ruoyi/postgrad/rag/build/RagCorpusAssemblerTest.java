package com.ruoyi.postgrad.rag.build;

import static org.junit.jupiter.api.Assertions.*;
import dev.langchain4j.data.segment.TextSegment;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagCorpusAssemblerTest {

    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2025;

    @Test
    void shouldAssembleGeneralDocsWithSourceTypeGeneralAndContentHash() {
        RagCorpusAssembler assembler = new RagCorpusAssembler();
        AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);

        assertTrue(corpus.generalDocumentCount() > 0,
            "should load at least one general document from classpath rag-documents/");
        List<TextSegment> generalChunks = corpus.chunks().stream()
            .filter(seg -> "general".equals(seg.metadata().getString("source_type")))
            .toList();
        assertFalse(generalChunks.isEmpty(), "should have general chunks");
        assertTrue(generalChunks.stream().allMatch(seg -> {
            String hash = seg.metadata().getString("content_hash");
            return hash != null && hash.length() == 64;
        }), "every general chunk should have a 64-char content_hash");
    }

    @Test
    void shouldHaveUniqueSourceIdsAcrossAllChunks() {
        RagCorpusAssembler assembler = new RagCorpusAssembler();
        AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);

        List<String> sourceIds = corpus.chunks().stream()
            .map(seg -> seg.metadata().getString("source_id"))
            .toList();
        assertEquals(sourceIds.size(), new HashSet<>(sourceIds).size(),
            "source_id must be unique across all chunks");
    }

    @Test
    void shouldHandleEmptyCorpusWithoutThrowing() {
        RagCorpusAssembler assembler = new RagCorpusAssembler();
        injectDocumentsPath(assembler, "nonexistent-empty-dir/");

        assertDoesNotThrow(() -> {
            AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);
            assertNotNull(corpus);
            assertTrue(corpus.chunks().isEmpty());
            assertEquals(0, corpus.generalDocumentCount());
        });
    }

    @Test
    void shouldHaveNoDatabaseChunks() {
        RagCorpusAssembler assembler = new RagCorpusAssembler();
        AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);

        assertEquals(0, corpus.databaseDocumentCount(),
            "database documents should be zero — structured data served via Tools");
        List<TextSegment> dbChunks = corpus.chunks().stream()
            .filter(seg -> "database".equals(seg.metadata().getString("source_type")))
            .toList();
        assertTrue(dbChunks.isEmpty(), "should have zero database-sourced chunks");
    }

    @Test
    void shouldAttachSchoolMetadataToAdmissionGuideChunks() {
        RagCorpusAssembler assembler = new RagCorpusAssembler();
        injectDocumentsPath(assembler, "rag-documents-fixture/");
        AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);

        List<TextSegment> chunks = corpus.chunks().stream()
            .filter(seg -> "安徽大学-招生简章.md".equals(seg.metadata().getString("source_name")))
            .toList();

        assertFalse(chunks.isEmpty(), "安徽大学招生简章 should be indexed");
        assertTrue(chunks.stream().allMatch(seg ->
            "school_guide".equals(seg.metadata().getString("source_type"))));
        assertTrue(chunks.stream().allMatch(seg ->
            "admission_guide".equals(seg.metadata().getString("doc_type"))));
        assertTrue(chunks.stream().allMatch(seg ->
            "安徽大学".equals(seg.metadata().getString("school_name"))));
        assertTrue(chunks.stream().allMatch(seg ->
            "2025".equals(seg.metadata().getString("year"))));
        assertTrue(chunks.stream().allMatch(seg -> {
            String sourceUrl = seg.metadata().getString("source_url");
            return sourceUrl != null && sourceUrl.startsWith("https://");
        }));
    }

    // --- helpers ---

    private static void injectDocumentsPath(RagCorpusAssembler assembler, String documentsPath) {
        try {
            Field field = RagCorpusAssembler.class.getDeclaredField("documentsPath");
            field.setAccessible(true);
            field.set(assembler, documentsPath);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to inject documentsPath", ex);
        }
    }
}
