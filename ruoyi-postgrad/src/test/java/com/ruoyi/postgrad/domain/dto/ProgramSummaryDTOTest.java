package com.ruoyi.postgrad.domain.dto;

import com.ruoyi.postgrad.domain.RowMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
