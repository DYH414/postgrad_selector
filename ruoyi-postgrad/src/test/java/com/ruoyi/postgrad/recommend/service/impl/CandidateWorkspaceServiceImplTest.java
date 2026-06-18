package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ruoyi.postgrad.recommend.domain.CandidateUniverseVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;

class CandidateWorkspaceServiceImplTest {

    @Test
    void buildWorkspaceSortsFilteredCandidatesWithoutUnsupportedOperationException() {
        CandidateUniverseVO universe = new CandidateUniverseVO();
        universe.setUserId(1L);
        universe.setUniverseId("u1");
        universe.setCandidates(List.of(
            fact(1L, 1L, -2, true),
            fact(2L, 2L, 9, true),
            fact(3L, 3L, 20, true)
        ));

        CandidateWorkspaceVO workspace = new CandidateWorkspaceServiceImpl()
            .buildWorkspace(universe, "no_strict_requirement", "developed_priority");

        assertEquals(3, workspace.totalCandidates());
        assertEquals(1, workspace.tierByLevel("reach").getCandidates().size());
        assertEquals(1, workspace.tierByLevel("steady").getCandidates().size());
        assertEquals(1, workspace.tierByLevel("safe").getCandidates().size());
    }

    private SchoolFact fact(Long programId, Long schoolId, int gap, boolean canBeSafe) {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(programId);
        fact.setSchoolId(schoolId);
        fact.setSchoolName("测试学校" + schoolId);
        fact.setSchoolTier("211");
        fact.setCity("杭州");
        fact.setDataCompleteness("A");
        fact.setQuotaRisk("normal");
        fact.setScoreGap(gap);
        fact.setCanBeSafe(canBeSafe);
        return fact;
    }
}
