package com.ruoyi.postgrad.rag.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RagKnowledgeExportMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagKnowledgeExportServiceTest {

    @Test
    void shouldConvertDatabaseRowsToStructuredChunks() {
        RagKnowledgeExportMapper mapper = mock(RagKnowledgeExportMapper.class);
        RowMap row = row(1L, 101L, 2025, 340);
        when(mapper.selectProgramYearFacts(2025, 10)).thenReturn(List.of(row));

        RagKnowledgeExportService service = new RagKnowledgeExportService(mapper, new StructuredRagChunkBuilder());

        List<StructuredRagChunk> chunks = service.exportProgramYearChunks(2025, 10);

        assertEquals(6, chunks.size());
        assertTrue(chunks.stream().allMatch(chunk -> "南京大学".equals(chunk.getMetadata().get("school_name"))));
        assertTrue(chunks.stream().anyMatch(chunk -> "admission_score".equals(chunk.getMetadata().get("section"))));
    }

    @Test
    void exportProgramHistoryDocumentsGroupsRowsByProgramId() {
        RagKnowledgeExportMapper mapper = mock(RagKnowledgeExportMapper.class);
        when(mapper.selectProgramHistoryFacts(2023, 2025, 100)).thenReturn(List.of(
            row(1L, 148L, 2025, 320),
            row(1L, 148L, 2023, 305),
            row(2L, 200L, 2025, 315)
        ));

        RagKnowledgeExportService service = new RagKnowledgeExportService(mapper, new StructuredRagChunkBuilder());

        List<RagProgramHistoryDocument> docs = service.exportProgramHistoryDocuments(2023, 2025, 100);
        List<RagDataQualityDocument> qualityDocs = service.exportDataQualityDocuments(2023, 2025, 100);

        assertEquals(2, docs.size());
        assertEquals(2023, docs.get(0).getYearStart());
        assertEquals(2025, docs.get(0).getYearEnd());
        assertEquals(2, docs.get(0).getYearFacts().size());
        assertEquals(2, qualityDocs.size());
    }

    @Test
    void exportProgramHistoryDocumentsAppliesLimitAfterGroupingPrograms() {
        RagKnowledgeExportMapper mapper = mock(RagKnowledgeExportMapper.class);
        when(mapper.selectProgramHistoryFacts(2022, 2026, 2)).thenReturn(List.of(
            row(1L, 148L, 2022, 305),
            row(1L, 148L, 2025, 320),
            row(2L, 200L, 2025, 315),
            row(3L, 300L, 2025, 325)
        ));

        RagKnowledgeExportService service = new RagKnowledgeExportService(mapper, new StructuredRagChunkBuilder());

        List<RagProgramHistoryDocument> docs = service.exportProgramHistoryDocuments(2022, 2026, 2);

        assertEquals(2, docs.size(), "limit should cap canonical program documents, not raw year rows");
        assertEquals(148L, docs.get(0).getPrimaryFact().getProgramId());
        assertEquals(200L, docs.get(1).getPrimaryFact().getProgramId());
    }

    @Test
    void exportLatestProgramYearFactsUsesLatestAvailableFactWithinRange() {
        RagKnowledgeExportMapper mapper = mock(RagKnowledgeExportMapper.class);
        when(mapper.selectProgramHistoryFacts(2022, 2026, 1000)).thenReturn(List.of(
            row(1L, 148L, 2022, 305),
            row(1L, 148L, 2025, 320),
            row(2L, 200L, 2024, 315)
        ));
        RagKnowledgeExportService service = new RagKnowledgeExportService(mapper, new StructuredRagChunkBuilder());

        List<RagProgramYearFact> latestFacts = service.exportLatestProgramYearFacts(2022, 2026, 1000);

        assertEquals(2, latestFacts.size());
        assertEquals(2025, latestFacts.get(0).getYear());
        assertEquals(2024, latestFacts.get(1).getYear());
    }

    private static RowMap row(Long schoolId, Long programId, Integer year, Integer avgScore) {
        RowMap row = new RowMap();
        row.put("schoolId", schoolId);
        row.put("schoolName", schoolId == 1L ? "南京大学" : "广州大学");
        row.put("province", schoolId == 1L ? "江苏" : "广东");
        row.put("city", schoolId == 1L ? "南京" : "广州");
        row.put("schoolTier", schoolId == 1L ? "985" : "PUBLIC_REGULAR");
        row.put("collegeId", schoolId == 1L ? 11L : 128L);
        row.put("collegeName", schoolId == 1L ? "计算机科学与技术系" : "网络空间安全学院");
        row.put("programId", programId);
        row.put("programCode", programId == 101L ? "085405" : "083900");
        row.put("programName", programId == 101L ? "软件工程" : "网络空间安全");
        row.put("degreeType", "academic");
        row.put("studyMode", "full_time");
        row.put("examType", "GENERAL_500");
        row.put("is408", 1);
        row.put("examSubjects", "101 思想政治理论 / 201 英语（一） / 301 数学（一） / 408 计算机学科专业基础");
        row.put("year", year);
        row.put("scoreLine", avgScore - 20);
        row.put("totalPlan", 42);
        row.put("admittedCount", 28);
        row.put("avgAdmittedScore", avgScore);
        row.put("qualityLevel", "A");
        row.put("sourceOwner", "N诺");
        row.put("sourceName", "录取数据");
        row.put("sourceUrl", "https://example.com/source");
        return row;
    }
}
