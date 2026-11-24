package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicantCandidateFilterTest {
    @Test
    void removesPersistedCandidatesNoLongerInApplicantCatalog() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        when(mapper.selectApplicantProgramIds(any())).thenReturn(List.of(1L));
        TierCandidates tier = new TierCandidates();
        tier.setCandidates(List.of(card(1L), card(2L)));

        ApplicantCandidateFilter.tiers(List.of(tier), mapper);

        assertEquals(List.of(1L), tier.getCandidates().stream().map(c -> c.getFact().getProgramId()).toList());
    }

    @Test
    void keepsEligiblePendingRefillAndDropsExcludedRefill() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        when(mapper.selectApplicantProgramIds(List.of(1L))).thenReturn(List.of(1L));
        when(mapper.selectApplicantProgramIds(List.of(2L))).thenReturn(List.of());
        var draft = new com.ruoyi.postgrad.recommend.domain.DraftVO();
        draft.setPendingRefill(java.util.Map.of("programId", 1));
        ApplicantCandidateFilter.draft(draft, mapper);
        assertEquals(1, draft.getPendingRefill().get("programId"));
        draft.setPendingRefill(java.util.Map.of("programId", 2));
        ApplicantCandidateFilter.draft(draft, mapper);
        org.junit.jupiter.api.Assertions.assertNull(draft.getPendingRefill());
    }

    private CandidateCardVO card(long id) {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(id);
        CandidateCardVO card = new CandidateCardVO();
        card.setFact(fact);
        return card;
    }
}
