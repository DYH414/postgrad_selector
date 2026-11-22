package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;

class CandidateUniverseServiceImplTest {
    private final CandidateUniverseServiceImpl service = new CandidateUniverseServiceImpl();
    private final RecommendationMapper mapper = mock(RecommendationMapper.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "recommendationMapper", mapper);
        addCandidate(1L, "101,204,302,408");
        addCandidate(2L, "101,201,301,408");
    }

    @Test
    void usesOnlyTheExamCombinationInTheProfile() {
        assertEquals(List.of(1L), candidateIds("22408"));
        assertEquals(List.of(2L), candidateIds("11408"));
    }

    @Test
    void keepsBothCombinationsForUnrestrictedAndLegacyProfiles() {
        assertEquals(List.of(1L, 2L), candidateIds("不限"));
        assertEquals(List.of(1L, 2L), candidateIds(null));
    }

    private List<Long> candidateIds(String examCombo) {
        ProfileBasisVO profile = new ProfileBasisVO();
        profile.setExamCombo(examCombo);
        return service.buildUniverse(1L, profile, 250, List.of(), "no_strict_requirement")
            .getCandidates().stream().map(SchoolFact::getProgramId).toList();
    }

    private void addCandidate(Long programId, String subjectCodes) {
        RowMap row = new RowMap();
        row.put("programId", programId);
        row.put("subjectCodes", subjectCodes);
        row.put("avgAdmittedScore", 258);
        when(mapper.selectCandidates(subjectCodes, List.of(), null, 250, 30, "full_time"))
            .thenReturn(List.of(row));
    }
}
