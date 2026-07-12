package com.ruoyi.postgrad.rag.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.service.RagRetrievalResult;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagEvaluationServiceTest {

    @Test
    void shouldPassCaseWhenRetrievedChunkMatchesExpectedMetadata() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSchoolName("南京大学");
        chunk.setSchoolId("1");
        chunk.setProgramName("软件工程");
        chunk.setProgramId("10");
        chunk.setYear("2025");
        chunk.setDocType("program_year");
        chunk.setSection("admission_score");
        chunk.setSelectedForPrompt(true);
        response.setChunks(List.of(chunk));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "case-1",
            "南京大学软件工程 2025 复试线",
            Map.of("school_id", "1", "program_id", "10", "year", "2025", "doc_type", "program_year", "section", "admission_score")
        );

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals(1, result.getTotalCases());
        assertEquals(1, result.getPassedCases());
        assertEquals(0, result.getFailedCases());
        assertEquals(1.0, result.getPassRate());
        assertFalse(result.getCases().get(0).getEvidence().isEmpty());
        assertEquals("PASSED", result.getCases().get(0).getFailureReason());
        verify(retrievalService).retrieve(argThat(req ->
            "1".equals(req.getSchoolId())
                && "10".equals(req.getProgramId())
                && "2025".equals(req.getYear())
                && "program_year".equals(req.getDocType())
                && "admission_score".equals(req.getSection())
        ));
    }

    @Test
    void shouldFailWithWrongScopeWhenSelectedChunkBelongsToAnotherProgram() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSchoolId("56");
        chunk.setProgramId("9999");
        chunk.setYear("2025");
        chunk.setDocType("program_year");
        chunk.setSection("admission_score");
        chunk.setSelectedForPrompt(true);
        response.setChunks(List.of(chunk));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "wrong-scope",
            "杭州电子科技大学 电子信息-网安方向 2025 复试线",
            Map.of("school_id", "56", "program_id", "2854", "year", "2025", "doc_type", "program_year", "section", "admission_score")
        );

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals(0, result.getPassedCases());
        assertEquals("WRONG_SCOPE", result.getCases().get(0).getFailureReason());
    }

    @Test
    void shouldFailWithMissingSectionWhenCorrectProgramHasDifferentSection() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSchoolId("56");
        chunk.setProgramId("2854");
        chunk.setYear("2025");
        chunk.setDocType("program_year");
        chunk.setSection("basic_info");
        chunk.setSelectedForPrompt(true);
        response.setChunks(List.of(chunk));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "missing-section",
            "杭州电子科技大学 电子信息-网安方向 2025 复试线",
            Map.of("school_id", "56", "program_id", "2854", "year", "2025", "doc_type", "program_year", "section", "admission_score")
        );

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals("MISSING_SECTION", result.getCases().get(0).getFailureReason());
    }

    @Test
    void shouldFailWithSourceDataMissingWhenCaseMarksExpectedSectionUnavailable() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        response.setChunks(List.of());
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationCase testCase = new RagEvaluationCase(
            "source-missing",
            "杭州电子科技大学 电子信息-网安方向 2025 录取结果",
            Map.of("school_id", "56", "program_id", "2854", "year", "2025", "doc_type", "program_year", "section", "admission_result")
        );
        testCase.setSourceDataAvailable(false);

        RagEvaluationResult result = new RagEvaluationService(retrievalService).run(List.of(testCase));

        assertEquals("SOURCE_DATA_MISSING", result.getCases().get(0).getFailureReason());
    }

    @Test
    void defaultCasesShouldUseConcreteKnownProgramMetadata() {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagSearchTestResponse response = new RagSearchTestResponse();
        RagChunkResult chunk = new RagChunkResult();
        chunk.setSchoolId("56");
        chunk.setProgramId("2854");
        chunk.setYear("2025");
        chunk.setDocType("program_year");
        chunk.setSection("admission_score");
        chunk.setSelectedForPrompt(true);
        response.setChunks(List.of(chunk));
        when(retrievalService.retrieve(any())).thenReturn(new RagRetrievalResult(response, List.of()));

        RagEvaluationResult result = new RagEvaluationService(retrievalService).runDefault();

        assertTrue(result.getCases().stream().anyMatch(c -> c.getQuery().contains("杭州电子科技大学")));
        verify(retrievalService, atLeastOnce()).retrieve(argThat(req ->
            "56".equals(req.getSchoolId())
                && "2854".equals(req.getProgramId())
                && "2025".equals(req.getYear())
        ));
    }
}
