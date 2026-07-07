package com.ruoyi.postgrad.rag.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredRagChunkBuilderTest {

    @Test
    void shouldBuildSemanticChunksFromProgramYearFacts() {
        RagProgramYearFact fact = sampleFact();
        StructuredRagChunkBuilder builder = new StructuredRagChunkBuilder();

        List<StructuredRagChunk> chunks = builder.buildProgramYearChunks(fact);

        assertEquals(6, chunks.size());
        assertSections(chunks, "basic_info", "exam_subjects", "admission_score",
            "admission_plan", "admission_result", "source_quality");

        StructuredRagChunk score = find(chunks, "admission_score");
        assertTrue(score.getText().contains("复试线: 340"));
        assertEquals("rag-doc-v2", score.getMetadata().get("schema_version"));
        assertEquals("program_year", score.getMetadata().get("doc_type"));
        assertEquals("1", score.getMetadata().get("school_id"));
        assertEquals("南京大学", score.getMetadata().get("school_name"));
        assertEquals("11", score.getMetadata().get("college_id"));
        assertEquals("计算机科学与技术系", score.getMetadata().get("college_name"));
        assertEquals("101", score.getMetadata().get("program_id"));
        assertEquals("085405", score.getMetadata().get("program_code"));
        assertEquals("软件工程", score.getMetadata().get("program_name"));
        assertEquals("2025", score.getMetadata().get("year"));
        assertEquals("admission_score", score.getMetadata().get("section"));
        assertEquals("N诺", score.getMetadata().get("source_owner"));
        assertEquals("https://example.com/source", score.getMetadata().get("source_url"));
        assertEquals("A", score.getMetadata().get("quality_level"));
        assertEquals("bge-m3-v1", score.getMetadata().get("embedding_version"));
        assertNotNull(score.getMetadata().get("content_hash"));
        assertEquals(64, score.getMetadata().get("content_hash").length());
        assertTrue(score.getChunkId().startsWith("program_year:1:101:2025:admission_score:"));
        assertEquals(score.getChunkId(), score.getMetadata().get("chunk_id"));
    }

    @Test
    void shouldBuildProgramHistoryChunksWithV2Metadata() {
        RagProgramYearFact y2024 = sampleFact();
        y2024.setYear(2024);
        y2024.setAvgAdmittedScore(350);
        RagProgramYearFact y2025 = sampleFact();
        RagProgramHistoryDocument history = new RagProgramHistoryDocument(List.of(y2024, y2025));

        List<StructuredRagChunk> chunks = new StructuredRagChunkBuilder().buildProgramHistoryChunks(history);

        assertSections(chunks, "basic_info", "exam_subjects", "score_trend",
            "admission_trend", "plan_trend", "risk_factors", "source_quality");
        StructuredRagChunk scoreTrend = find(chunks, "score_trend");
        assertEquals("rag-doc-v2", scoreTrend.getMetadata().get("schema_version"));
        assertEquals("program_history", scoreTrend.getMetadata().get("doc_type"));
        assertEquals("program_history_1_101", scoreTrend.getMetadata().get("doc_id"));
        assertEquals("101", scoreTrend.getMetadata().get("program_id"));
        assertEquals("1", scoreTrend.getMetadata().get("school_id"));
        assertEquals("2024", scoreTrend.getMetadata().get("year_start"));
        assertEquals("2025", scoreTrend.getMetadata().get("year_end"));
        assertEquals("2024,2025", scoreTrend.getMetadata().get("years_available"));
        assertEquals("bge-m3-v1", scoreTrend.getMetadata().get("embedding_version"));
        assertTrue(scoreTrend.getText().contains("| 2024 |"));
        assertTrue(scoreTrend.getChunkId().startsWith("program_history:1:101:score_trend:"));
    }

    @Test
    void shouldBuildDataQualityChunksWithV2Metadata() {
        RagProgramYearFact y2023 = sampleFact();
        y2023.setYear(2023);
        y2023.setMissingFields("");
        RagProgramYearFact y2025 = sampleFact();
        y2025.setMissingFields("[\"score\"]");
        RagDataQualityDocument quality = new RagDataQualityDocument(
            new RagProgramHistoryDocument(List.of(y2023, y2025)));

        List<StructuredRagChunk> chunks = new StructuredRagChunkBuilder().buildDataQualityChunks(quality);

        assertSections(chunks, "coverage_summary", "missing_years", "missing_fields",
            "source_conflicts", "recommendation_warning");
        StructuredRagChunk missingYears = find(chunks, "missing_years");
        assertEquals("data_quality", missingYears.getMetadata().get("doc_type"));
        assertEquals("program_quality_1_101", missingYears.getMetadata().get("doc_id"));
        assertEquals("partial", missingYears.getMetadata().get("data_completeness"));
        assertTrue(missingYears.getText().contains("2024"));
    }

    @Test
    void shouldRepresentMissingFactsExplicitly() {
        RagProgramYearFact fact = sampleFact();
        fact.setScoreLine(null);
        fact.setTotalPlan(null);
        fact.setAdmittedCount(null);
        fact.setMissingFields("score_line,total_plan,admitted_count");

        List<StructuredRagChunk> chunks = new StructuredRagChunkBuilder().buildProgramYearChunks(fact);

        assertTrue(find(chunks, "admission_score").getText().contains("暂无复试线数据"));
        assertTrue(find(chunks, "admission_plan").getText().contains("暂无招生计划数据"));
        assertTrue(find(chunks, "admission_result").getText().contains("暂无录取结果数据"));
        assertEquals("score_line,total_plan,admitted_count",
            find(chunks, "source_quality").getMetadata().get("missing_fields"));
    }

    @Test
    void shouldBuildCitationFromSelectedChunk() {
        StructuredRagChunk chunk = find(new StructuredRagChunkBuilder().buildProgramYearChunks(sampleFact()),
            "admission_result");

        RagCitation citation = RagCitation.fromChunk(chunk);

        assertEquals(chunk.getChunkTitle(), citation.getTitle());
        assertEquals("N诺", citation.getSourceName());
        assertEquals("https://example.com/source", citation.getSourceUrl());
        assertEquals("南京大学", citation.getSchoolName());
        assertEquals("软件工程", citation.getProgramName());
        assertEquals("2025", citation.getYear());
        assertEquals("admission_result", citation.getSection());
        assertEquals(chunk.getMetadata().get("content_hash"), citation.getContentHash());
        assertFalse(citation.getContentPreview().isBlank());
    }

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

    private static StructuredRagChunk find(List<StructuredRagChunk> chunks, String section) {
        return chunks.stream()
            .filter(chunk -> section.equals(chunk.getMetadata().get("section")))
            .findFirst()
            .orElseThrow();
    }

    private static void assertSections(List<StructuredRagChunk> chunks, String... sections) {
        for (String section : sections) {
            assertTrue(chunks.stream().anyMatch(chunk -> section.equals(chunk.getMetadata().get("section"))),
                "missing section " + section);
        }
    }
}
