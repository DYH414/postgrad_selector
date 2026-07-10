package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.dto.RagEvidenceRequest;
import com.ruoyi.postgrad.rag.dto.RagEvidenceResponse;
import com.ruoyi.postgrad.rag.knowledge.RagCitation;
import com.ruoyi.postgrad.rag.service.RagEvidenceService;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportServiceImplRagEvidenceTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildCitationsUsesRagEvidenceBeforeSchoolFactFallback() throws Exception {
        RagEvidenceService evidenceService = mock(RagEvidenceService.class);
        RagEvidenceResponse evidence = new RagEvidenceResponse();
        RagCitation citation = new RagCitation();
        citation.setTitle("广州大学 网络空间安全 - 历年分数趋势");
        citation.setSourceName("program_history_22_101.md");
        citation.setProgramId("101");
        citation.setDocType("program_history");
        citation.setSection("score_trend");
        citation.setContentPreview("近三年录取均分呈上升趋势。");
        evidence.setCitations(List.of(citation));
        when(evidenceService.retrieveProgramEvidence(any(RagEvidenceRequest.class))).thenReturn(evidence);

        ReportServiceImpl service = new ReportServiceImpl();
        var field = ReportServiceImpl.class.getDeclaredField("ragEvidenceService");
        field.setAccessible(true);
        field.set(service, evidenceService);

        Method method = ReportServiceImpl.class.getDeclaredMethod("buildCitations", List.class);
        method.setAccessible(true);
        List<RagCitation> citations = (List<RagCitation>) method.invoke(service, List.of(tierWithProgram()));

        assertEquals(1, citations.size());
        assertEquals("program_history", citations.get(0).getDocType());
        assertEquals("101", citations.get(0).getProgramId());
        assertEquals("score_trend", citations.get(0).getSection());
    }

    private static TierCandidates tierWithProgram() {
        SchoolFact fact = new SchoolFact();
        fact.setSchoolId(22L);
        fact.setProgramId(101L);
        fact.setSchoolName("广州大学");
        fact.setProgramName("网络空间安全");
        fact.setAvgAdmittedScore(315);

        TierCandidates tier = new TierCandidates();
        tier.setLevel("steady");
        tier.setLabel("稳妥档");
        tier.setCandidates(List.of(CandidateCardVO.fromFact(fact)));
        return tier;
    }
}
