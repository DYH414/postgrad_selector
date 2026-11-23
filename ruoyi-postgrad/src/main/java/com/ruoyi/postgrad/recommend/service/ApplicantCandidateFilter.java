package com.ruoyi.postgrad.recommend.service;

import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ApplicantCandidateFilter {
    private ApplicantCandidateFilter() {}

    public static List<CandidateCardVO> cards(List<CandidateCardVO> cards, RecommendationMapper mapper) {
        if (cards == null || cards.isEmpty()) return new ArrayList<>();
        List<Long> ids = cards.stream().filter(c -> c != null && c.getFact() != null)
            .map(c -> c.getFact().getProgramId()).filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) return new ArrayList<>();
        Set<Long> allowed = Set.copyOf(mapper.selectApplicantProgramIds(ids));
        return cards.stream().filter(c -> c != null && c.getFact() != null
            && allowed.contains(c.getFact().getProgramId())).collect(Collectors.toList());
    }

    public static void draft(com.ruoyi.postgrad.recommend.domain.DraftVO draft, RecommendationMapper mapper) {
        if (draft == null) return;
        tiers(draft.getTiers(), mapper);
        if (draft.getRemovedCandidates() != null) draft.setRemovedCandidates(cards(draft.getRemovedCandidates(), mapper));
        if (draft.getBlockedCandidates() != null && !draft.getBlockedCandidates().isEmpty()) {
            List<Long> ids = draft.getBlockedCandidates().stream().map(b -> b.getProgramId())
                .filter(java.util.Objects::nonNull).distinct().toList();
            Set<Long> allowed = ids.isEmpty() ? Set.of() : Set.copyOf(mapper.selectApplicantProgramIds(ids));
            draft.setBlockedCandidates(draft.getBlockedCandidates().stream()
                .filter(b -> allowed.contains(b.getProgramId())).collect(Collectors.toList()));
        }
        if (draft.getPendingRefill() != null) {
            Object value = draft.getPendingRefill().get("programId");
            Long id;
            try { id = value == null ? null : Long.valueOf(value.toString()); }
            catch (NumberFormatException e) { id = null; }
            if (id == null || mapper.selectApplicantProgramIds(List.of(id)).isEmpty()) draft.setPendingRefill(null);
        }
    }

    public static void tiers(List<TierCandidates> tiers, RecommendationMapper mapper) {
        if (tiers == null) return;
        List<CandidateCardVO> all = new ArrayList<>();
        tiers.forEach(t -> { if (t.getCandidates() != null) all.addAll(t.getCandidates()); });
        Set<CandidateCardVO> allowed = Set.copyOf(cards(all, mapper));
        tiers.forEach(t -> { if (t.getCandidates() != null) {
            t.setCandidates(t.getCandidates().stream().filter(allowed::contains).collect(Collectors.toList()));
        }});
    }

    public static CandidateWorkspaceVO workspace(CandidateWorkspaceVO workspace, RecommendationMapper mapper) {
        if (workspace == null || workspace.getTiers() == null) return workspace;
        List<CandidateCardVO> all = new ArrayList<>();
        workspace.getTiers().forEach(t -> { if (t.getCandidates() != null) all.addAll(t.getCandidates()); });
        Set<CandidateCardVO> allowed = Set.copyOf(cards(all, mapper));
        workspace.getTiers().forEach(t -> { if (t.getCandidates() != null) {
            t.setCandidates(t.getCandidates().stream().filter(allowed::contains).collect(Collectors.toList()));
        }});
        return workspace;
    }
}
