package com.ruoyi.postgrad.domain.dto;

import com.ruoyi.postgrad.domain.RowMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramSummaryDTOTest {

    @Test
    void fromRowMapPreservesSchoolTierBadges() {
        RowMap row = new RowMap();
        row.put("programId", 2495L);
        row.put("schoolId", 177L);
        row.put("schoolName", "四川大学");
        row.put("schoolTier", "985");
        row.put("is985", 1);
        row.put("is211", 1);
        row.put("isDoubleFirst", 1);
        row.put("subjectCodes", "101,201,301,408");
        row.put("avgAdmittedScore", 329);

        ProgramSummaryDTO dto = ProgramSummaryDTO.fromRowMap(row, 315);

        assertEquals("985", dto.getSchoolTier());
        assertTrue(dto.getIs985());
        assertTrue(dto.getIs211());
        assertTrue(dto.getIsDoubleFirst());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "101,201,301,408 | 11408 | 政治 + 英语一 + 数学一 + 408",
        "101,204,302,408 | 22408 | 政治 + 英语二 + 数学二 + 408"
    })
    void fromRowMapLabelsCanonicalExamCombos(String subjectCodes, String combo, String label) {
        RowMap row = new RowMap();
        row.put("subjectCodes", subjectCodes);

        ProgramSummaryDTO dto = ProgramSummaryDTO.fromRowMap(row, 0);

        assertEquals(combo, dto.getExamCombo());
        assertEquals(label, dto.getExamSubjectsLabel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"101,201,301,842", "101,204,301,408", "11408"})
    void fromRowMapDisplaysOtherSubjectCodes(String subjectCodes) {
        RowMap row = new RowMap();
        row.put("subjectCodes", subjectCodes);

        ProgramSummaryDTO dto = ProgramSummaryDTO.fromRowMap(row, 0);

        assertEquals(subjectCodes, dto.getExamCombo());
        assertEquals(subjectCodes, dto.getExamSubjectsLabel());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void fromRowMapOmitsLabelsForMissingSubjectCodes(String subjectCodes) {
        RowMap row = new RowMap();
        row.put("subjectCodes", subjectCodes);

        ProgramSummaryDTO dto = ProgramSummaryDTO.fromRowMap(row, 0);

        assertNull(dto.getExamCombo());
        assertNull(dto.getExamSubjectsLabel());
    }
}
