package com.ruoyi.postgrad.rag.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagMarkdownDocumentServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateMarkdownDocumentsFromDatabaseFacts() throws Exception {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        when(exportService.exportProgramYearFacts(2025, 10)).thenReturn(List.of(sampleFact()));
        RagMarkdownDocumentService service = new RagMarkdownDocumentService(
            exportService,
            new RagMarkdownDocumentBuilder(),
            tempDir
        );

        RagMarkdownExportResult result = service.generateProgramYearDocuments(2025, 10);

        Path document = tempDir.resolve("2025").resolve("program_year_1_101_2025.md");
        assertEquals(1, result.getDocumentCount());
        assertEquals(1, result.getWrittenFiles().size());
        assertEquals(document, result.getWrittenFiles().get(0));
        assertTrue(Files.exists(document));

        String markdown = Files.readString(document);
        assertTrue(markdown.contains("schema_version: \"rag-doc-v2\""));
        assertTrue(markdown.contains("doc_type: \"program_year\""));
        assertTrue(markdown.contains("school_name: \"南京大学\""));
        assertTrue(markdown.contains("program_name: \"软件工程\""));
        assertTrue(markdown.contains("# 南京大学 软件工程 2025 考研信息"));
        assertTrue(markdown.contains("## 基本信息"));
        assertTrue(markdown.contains("## 考试科目"));
        assertTrue(markdown.contains("## 复试线"));
        assertTrue(markdown.contains("复试线: 340"));
        assertTrue(markdown.contains("## 来源与质量"));
    }

    @Test
    void shouldGenerateV2MarkdownDocumentsByDocumentFamily() throws Exception {
        RagKnowledgeExportService exportService = mock(RagKnowledgeExportService.class);
        RagProgramYearFact y2024 = sampleFact(2024);
        RagProgramYearFact y2025 = sampleFact(2025);
        RagProgramHistoryDocument history = new RagProgramHistoryDocument(List.of(y2024, y2025));
        RagDataQualityDocument quality = new RagDataQualityDocument(history);
        when(exportService.exportProgramHistoryDocuments(2024, 2025, 10)).thenReturn(List.of(history));
        when(exportService.exportProgramYearFacts(2025, 10)).thenReturn(List.of(y2025));
        when(exportService.exportDataQualityDocuments(2024, 2025, 10)).thenReturn(List.of(quality));
        RagMarkdownDocumentService service = new RagMarkdownDocumentService(
            exportService,
            new RagMarkdownDocumentBuilder(),
            tempDir
        );

        RagMarkdownExportResult result = service.generateV2Documents(2024, 2025, 10);

        Path historyPath = tempDir.resolve("program-history").resolve("program_history_1_101.md");
        Path yearPath = tempDir.resolve("program-year").resolve("2025").resolve("program_year_1_101_2025.md");
        Path qualityPath = tempDir.resolve("data-quality").resolve("program_quality_1_101.md");
        assertEquals(3, result.getDocumentCount());
        assertTrue(Files.exists(historyPath));
        assertTrue(Files.exists(yearPath));
        assertTrue(Files.exists(qualityPath));
        assertTrue(Files.readString(historyPath).contains("doc_type: \"program_history\""));
        assertTrue(Files.readString(yearPath).contains("doc_type: \"program_year\""));
        assertTrue(Files.readString(qualityPath).contains("doc_type: \"data_quality\""));
    }

    private static RagProgramYearFact sampleFact() {
        return sampleFact(2025);
    }

    private static RagProgramYearFact sampleFact(Integer year) {
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
        fact.setYear(year);
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
}
