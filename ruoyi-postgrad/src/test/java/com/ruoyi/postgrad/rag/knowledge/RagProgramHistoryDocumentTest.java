package com.ruoyi.postgrad.rag.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RagProgramHistoryDocumentTest {

    @Test
    void sortsYearFactsAndExposesYearRange() {
        RagProgramYearFact y2025 = fact(1L, 148L, 2025, "B", null);
        RagProgramYearFact y2023 = fact(1L, 148L, 2023, "A", null);

        RagProgramHistoryDocument doc = new RagProgramHistoryDocument(List.of(y2025, y2023));

        assertEquals(2023, doc.getYearStart());
        assertEquals(2025, doc.getYearEnd());
        assertEquals("2023,2025", doc.getYearsAvailableCsv());
        assertEquals(2023, doc.getYearFacts().get(0).getYear());
        assertEquals(2025, doc.getYearFacts().get(1).getYear());
        assertEquals(148L, doc.getPrimaryFact().getProgramId());
    }

    @Test
    void dataQualityReportsMissingYearsBetweenRange() {
        RagProgramHistoryDocument history = new RagProgramHistoryDocument(List.of(
            fact(1L, 148L, 2023, "A", null),
            fact(1L, 148L, 2025, "B", "[\"score\"]")
        ));

        RagDataQualityDocument quality = new RagDataQualityDocument(history);

        assertEquals(List.of(2024), quality.getMissingYears());
        assertEquals("partial", quality.getDataCompleteness());
        assertTrue(quality.getMergedMissingFields().contains("score"));
    }

    private static RagProgramYearFact fact(Long schoolId, Long programId, Integer year,
                                           String qualityLevel, String missingFields) {
        RagProgramYearFact fact = new RagProgramYearFact();
        fact.setSchoolId(schoolId);
        fact.setSchoolName("广州大学");
        fact.setProvince("广东");
        fact.setCity("广州");
        fact.setSchoolTier("PUBLIC_REGULAR");
        fact.setCollegeId(128L);
        fact.setCollegeName("网络空间安全学院");
        fact.setProgramId(programId);
        fact.setProgramCode("083900");
        fact.setProgramName("网络空间安全");
        fact.setDegreeType("academic");
        fact.setStudyMode("full_time");
        fact.setExamType("GENERAL_500");
        fact.setIs408(true);
        fact.setExamSubjects("101 思想政治理论 / 201 英语（一） / 301 数学（一） / 408 计算机学科专业基础");
        fact.setYear(year);
        fact.setQualityLevel(qualityLevel);
        fact.setMissingFields(missingFields);
        fact.setSourceOwner("N诺考研择校");
        fact.setSourceName("N诺考研择校-广州大学");
        fact.setSourceUrl("https://noobdream.com/schoolinfo/135/");
        return fact;
    }
}
