package com.ruoyi.postgrad.rag.eval;

import com.ruoyi.postgrad.rag.dto.RagChunkResult;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.dto.RagSearchTestResponse;
import com.ruoyi.postgrad.rag.service.RagSearchTestService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RagEvaluationService {

    private final RagSearchTestService searchTestService;

    public RagEvaluationService(RagSearchTestService searchTestService) {
        this.searchTestService = searchTestService;
    }

    public RagEvaluationResult runDefault() {
        return run(defaultCases());
    }

    public RagEvaluationResult run(List<RagEvaluationCase> cases) {
        RagEvaluationResult result = new RagEvaluationResult();
        List<RagEvaluationResult.RagEvaluationCaseResult> caseResults = new ArrayList<>();
        int passed = 0;
        for (RagEvaluationCase testCase : cases) {
            RagSearchTestRequest request = new RagSearchTestRequest(testCase.getQuery(), 5, 0.5, 3);
            applyExpectedFilters(request, testCase.getExpectedMetadata());
            RagSearchTestResponse response = searchTestService.searchTest(request);
            RagEvaluationResult.RagEvaluationCaseResult caseResult = evaluateCase(testCase, response);
            if (caseResult.isPassed()) {
                passed++;
            }
            caseResults.add(caseResult);
        }
        result.setTotalCases(cases.size());
        result.setPassedCases(passed);
        result.setFailedCases(cases.size() - passed);
        result.setPassRate(cases.isEmpty() ? 0.0 : (double) passed / cases.size());
        result.setCases(caseResults);
        return result;
    }

    private RagEvaluationResult.RagEvaluationCaseResult evaluateCase(RagEvaluationCase testCase,
                                                                     RagSearchTestResponse response) {
        RagEvaluationResult.RagEvaluationCaseResult result = new RagEvaluationResult.RagEvaluationCaseResult();
        result.setId(testCase.getId());
        result.setQuery(testCase.getQuery());
        if (!testCase.isSourceDataAvailable() && response.getChunks().isEmpty()) {
            result.setFailureReason("SOURCE_DATA_MISSING");
            return result;
        }
        if (response.getChunks().isEmpty()) {
            result.setFailureReason("NO_RETRIEVAL");
            return result;
        }
        for (RagChunkResult chunk : response.getChunks()) {
            if (matches(testCase.getExpectedMetadata(), chunk)) {
                result.setPassed(true);
                result.setFailureReason("PASSED");
                result.getEvidence().add(evidenceLabel(chunk));
            }
        }
        if (!result.isPassed()) {
            result.setFailureReason(resolveFailureReason(testCase, response));
        }
        return result;
    }

    private void applyExpectedFilters(RagSearchTestRequest request, Map<String, String> expected) {
        request.setSchoolId(expected.get("school_id"));
        request.setProgramId(expected.get("program_id"));
        request.setYear(expected.get("year"));
        request.setDocType(expected.get("doc_type"));
        request.setSection(expected.get("section"));
    }

    private String resolveFailureReason(RagEvaluationCase testCase, RagSearchTestResponse response) {
        boolean hasExpectedScope = response.getChunks().stream()
            .anyMatch(chunk -> matchesScope(testCase.getExpectedMetadata(), chunk));
        return hasExpectedScope ? "MISSING_SECTION" : "WRONG_SCOPE";
    }

    private boolean matchesScope(Map<String, String> expected, RagChunkResult chunk) {
        return equalsExpected(expected, "school_id", chunk.getSchoolId())
            && equalsExpected(expected, "program_id", chunk.getProgramId())
            && equalsExpected(expected, "year", chunk.getYear())
            && equalsExpected(expected, "doc_type", chunk.getDocType());
    }

    private boolean matches(Map<String, String> expected, RagChunkResult chunk) {
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String actual = switch (entry.getKey()) {
                case "school_name" -> chunk.getSchoolName();
                case "program_name" -> chunk.getProgramName();
                case "school_id" -> chunk.getSchoolId();
                case "program_id" -> chunk.getProgramId();
                case "year" -> chunk.getYear();
                case "section" -> chunk.getSection();
                case "doc_type" -> chunk.getDocType();
                default -> null;
            };
            if (!entry.getValue().equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private boolean equalsExpected(Map<String, String> expected, String key, String actual) {
        String value = expected.get(key);
        return value == null || value.equals(actual);
    }

    private String evidenceLabel(RagChunkResult chunk) {
        return String.join(" / ",
            nullToEmpty(chunk.getSchoolName()),
            nullToEmpty(chunk.getProgramName()),
            nullToEmpty(chunk.getYear()),
            nullToEmpty(chunk.getSection()),
            nullToEmpty(chunk.getChunkTitle()));
    }

    private List<RagEvaluationCase> defaultCases() {
        return List.of(
            new RagEvaluationCase("score-line-program-year",
                "杭州电子科技大学 电子信息-网安方向 2025 年复试线是多少",
                Map.of("school_id", "56", "program_id", "2854", "year", "2025",
                    "doc_type", "program_year", "section", "admission_score")),
            new RagEvaluationCase("admission-plan-program-year",
                "杭州电子科技大学 电子信息-网安方向 2025 年招生计划和统考名额是多少",
                Map.of("school_id", "56", "program_id", "2854", "year", "2025",
                    "doc_type", "program_year", "section", "admission_plan")),
            new RagEvaluationCase("admission-result-program-year",
                "杭州电子科技大学 电子信息-网安方向 2025 年录取最低分平均分最高分是多少",
                Map.of("school_id", "56", "program_id", "2854", "year", "2025",
                    "doc_type", "program_year", "section", "admission_result"))
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
