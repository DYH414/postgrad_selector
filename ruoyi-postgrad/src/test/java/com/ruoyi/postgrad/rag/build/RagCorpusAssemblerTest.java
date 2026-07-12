package com.ruoyi.postgrad.rag.build;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.knowledge.RagDataQualityDocument;
import com.ruoyi.postgrad.rag.knowledge.RagKnowledgeExportService;
import com.ruoyi.postgrad.rag.knowledge.RagProgramHistoryDocument;
import com.ruoyi.postgrad.rag.knowledge.RagProgramYearFact;
import com.ruoyi.postgrad.rag.knowledge.StructuredRagChunkBuilder;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagCorpusAssemblerTest {

    private static final int START_YEAR = 2024;
    private static final int END_YEAR = 2025;

    @Test
    void shouldAssembleGeneralDocsWithSourceTypeGeneralAndContentHash() {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        when(exportService.exportProgramHistoryDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        when(exportService.exportLatestProgramYearFacts(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        when(exportService.exportDataQualityDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        Path tempDir = createTempDir();

        RagCorpusAssembler assembler = new RagCorpusAssembler(
            exportService, new StructuredRagChunkBuilder(), tempDir.toString());

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
        cleanTempDir(tempDir);
    }

    @Test
    void shouldAssembleDatabaseDocsWithSchoolIdProgramIdAndYear() {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        RagProgramYearFact fact = sampleFact();
        RagProgramHistoryDocument history = new RagProgramHistoryDocument(List.of(sampleFact()));
        RagDataQualityDocument quality = new RagDataQualityDocument(history);
        when(exportService.exportProgramHistoryDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of(history));
        when(exportService.exportLatestProgramYearFacts(anyInt(), anyInt(), anyInt())).thenReturn(List.of(fact));
        when(exportService.exportDataQualityDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of(quality));
        Path tempDir = createTempDir();

        RagCorpusAssembler assembler = new RagCorpusAssembler(
            exportService, new StructuredRagChunkBuilder(), tempDir.toString());

        AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);

        List<TextSegment> dbChunks = corpus.chunks().stream()
            .filter(seg -> "database".equals(seg.metadata().getString("source_type")))
            .toList();
        assertFalse(dbChunks.isEmpty(), "should have database chunks");
        assertTrue(dbChunks.stream().allMatch(seg -> {
            String schoolId = seg.metadata().getString("school_id");
            String programId = seg.metadata().getString("program_id");
            String year = seg.metadata().getString("year");
            return schoolId != null && !schoolId.isBlank()
                && programId != null && !programId.isBlank();
        }), "every database chunk should have school_id, program_id");
        // At least some database chunks should have year (program_year and data_quality may have empty year)
        assertTrue(dbChunks.stream().anyMatch(seg -> {
            String year = seg.metadata().getString("year");
            return year != null && !year.isBlank();
        }), "at least some database chunks should have year");
        cleanTempDir(tempDir);
    }

    @Test
    void shouldUseLatestAvailableYearWithinRequestedRangeForYearFacts() {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        RagProgramYearFact fact = sampleFact();
        fact.setYear(2025);
        when(exportService.exportProgramHistoryDocuments(2022, 2026, Integer.MAX_VALUE)).thenReturn(List.of());
        when(exportService.exportLatestProgramYearFacts(2022, 2026, Integer.MAX_VALUE)).thenReturn(List.of(fact));
        when(exportService.exportDataQualityDocuments(2022, 2026, Integer.MAX_VALUE)).thenReturn(List.of());
        Path tempDir = createTempDir();

        RagCorpusAssembler assembler = new RagCorpusAssembler(
            exportService, new StructuredRagChunkBuilder(), tempDir.toString());

        assembler.assemble(2022, 2026);

        verify(exportService).exportLatestProgramYearFacts(2022, 2026, Integer.MAX_VALUE);
        verify(exportService, never()).exportProgramYearFacts(2026, Integer.MAX_VALUE);
        cleanTempDir(tempDir);
    }

    @Test
    void shouldHaveUniqueSourceIdsAcrossAllChunks() {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        RagProgramYearFact fact = sampleFact();
        RagProgramHistoryDocument history = new RagProgramHistoryDocument(List.of(sampleFact()));
        RagDataQualityDocument quality = new RagDataQualityDocument(history);
        when(exportService.exportProgramHistoryDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of(history));
        when(exportService.exportLatestProgramYearFacts(anyInt(), anyInt(), anyInt())).thenReturn(List.of(fact));
        when(exportService.exportDataQualityDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of(quality));
        Path tempDir = createTempDir();

        RagCorpusAssembler assembler = new RagCorpusAssembler(
            exportService, new StructuredRagChunkBuilder(), tempDir.toString());

        AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);

        List<String> sourceIds = corpus.chunks().stream()
            .map(seg -> seg.metadata().getString("source_id"))
            .toList();
        assertEquals(sourceIds.size(), new HashSet<>(sourceIds).size(),
            "source_id must be unique across all chunks");
        cleanTempDir(tempDir);
    }

    @Test
    void shouldHandleEmptyCorpusWithoutThrowing() {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        when(exportService.exportProgramHistoryDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        when(exportService.exportLatestProgramYearFacts(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        when(exportService.exportDataQualityDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        Path tempDir = createTempDir();

        RagCorpusAssembler assembler = new RagCorpusAssembler(
            exportService, new StructuredRagChunkBuilder(), tempDir.toString());
        // Override documentsPath to point to a non-existent directory
        injectDocumentsPath(assembler, "nonexistent-empty-dir/");

        assertDoesNotThrow(() -> {
            AssembledCorpus corpus = assembler.assemble(START_YEAR, END_YEAR);
            assertNotNull(corpus);
            assertTrue(corpus.chunks().isEmpty());
            assertEquals(0, corpus.generalDocumentCount());
            assertEquals(0, corpus.databaseDocumentCount());
        });
        cleanTempDir(tempDir);
    }

    @Test
    void shouldWriteExternalMarkdownToGeneratedDirectory(@TempDir Path tempDir) {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        RagProgramYearFact fact = sampleFact();
        RagProgramHistoryDocument history = new RagProgramHistoryDocument(List.of(sampleFact()));
        RagDataQualityDocument quality = new RagDataQualityDocument(history);
        when(exportService.exportProgramHistoryDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of(history));
        when(exportService.exportLatestProgramYearFacts(anyInt(), anyInt(), anyInt())).thenReturn(List.of(fact));
        when(exportService.exportDataQualityDocuments(anyInt(), anyInt(), anyInt())).thenReturn(List.of(quality));

        RagCorpusAssembler assembler = new RagCorpusAssembler(
            exportService, new StructuredRagChunkBuilder(), tempDir.toString());

        assembler.assemble(START_YEAR, END_YEAR);

        Path generatedDir = tempDir.resolve("generated");
        assertTrue(Files.isDirectory(generatedDir), "generated/ directory should exist");
        try (Stream<Path> files = Files.list(generatedDir)) {
            long fileCount = files.count();
            assertTrue(fileCount > 0, "should have at least one markdown file in generated/");
        } catch (IOException ex) {
            fail("Failed to list generated directory: " + ex.getMessage());
        }
    }

    // --- helpers ---

    private static RagProgramYearFact sampleFact() {
        RagProgramYearFact fact = new RagProgramYearFact();
        fact.setSchoolId(1L);
        fact.setSchoolName("南京大学");
        fact.setProvince("江苏");
        fact.setCity("南京");
        fact.setSchoolTier("985");
        fact.setCollegeId(11L);
        fact.setCollegeName("计算机科学与技术系");
        fact.setProgramId(101L);
        fact.setProgramCode("085405");
        fact.setProgramName("软件工程");
        fact.setResearchDirection("软件工程与智能系统");
        fact.setDegreeType("专硕");
        fact.setStudyMode("全日制");
        fact.setExamType("统考");
        fact.setIs408(true);
        fact.setExamSubjects("101 思想政治理论 / 201 英语一 / 301 数学一 / 408 计算机学科专业基础");
        fact.setYear(2025);
        fact.setScoreLine(340);
        fact.setSinglePolitics(50);
        fact.setSingleEnglish(50);
        fact.setSingleMath(75);
        fact.setSingleProfessional(75);
        fact.setTotalPlan(42);
        fact.setUnifiedExamQuota(30);
        fact.setRetestCount(55);
        fact.setAdmittedCount(28);
        fact.setMinAdmittedScore(345);
        fact.setAvgAdmittedScore(362);
        fact.setMaxAdmittedScore(395);
        fact.setQualityLevel("A");
        fact.setHasOfficialSource(true);
        fact.setHasConflict(false);
        fact.setMissingFields("");
        fact.setSourceOwner("N诺");
        fact.setSourceName("南京大学 2025 软件工程录取数据");
        fact.setSourceUrl("https://example.com/source");
        return fact;
    }

    private static void injectDocumentsPath(RagCorpusAssembler assembler, String documentsPath) {
        try {
            Field field = RagCorpusAssembler.class.getDeclaredField("documentsPath");
            field.setAccessible(true);
            field.set(assembler, documentsPath);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to inject documentsPath", ex);
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("rag-assembler-test-");
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create temp directory", ex);
        }
    }

    private static void cleanTempDir(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
                }
            } catch (IOException ignored) {
            }
        }
    }
}
