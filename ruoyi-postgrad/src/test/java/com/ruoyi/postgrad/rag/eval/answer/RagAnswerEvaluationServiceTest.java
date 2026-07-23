package com.ruoyi.postgrad.rag.eval.answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagAnswerEvaluationServiceTest {

    @Test
    void answerDatasetIsChecksumFrozenAndHasTwelveUniqueCases() {
        RagAnswerEvaluationDatasetLoader loader = new RagAnswerEvaluationDatasetLoader();

        List<RagAnswerEvaluationCase> cases = loader.loadAnswerV1();

        assertEquals(12, cases.size());
        assertEquals(12, new HashSet<>(cases.stream().map(RagAnswerEvaluationCase::getId).toList()).size());
        assertEquals(2, cases.stream().filter(RagAnswerEvaluationCase::isExpectedAbstention).count());
        assertTrue(cases.stream().filter(testCase -> !testCase.isExpectedAbstention())
            .allMatch(testCase -> !testCase.getRequiredClaims().isEmpty()));
        assertFalse(loader.verifyAnswerV1Checksum().isBlank());
    }

    @Test
    void strictJudgeParserAcceptsValidJsonWithSurroundingText() {
        String raw = "结果：{\"faithfulness\":2,\"answerRelevance\":2,"
            + "\"citationCorrectness\":1,\"abstentionCorrectness\":2,"
            + "\"passed\":true,\"reason\":\"证据支持\"}";

        RagAnswerEvaluationResult.JudgeResult result =
            RagAnswerEvaluationService.parseJudge(raw);

        assertTrue(result.isValid());
        assertEquals(2, result.getFaithfulness());
        assertEquals(1, result.getCitationCorrectness());
        assertTrue(result.getPassed());
    }

    @Test
    void strictJudgeParserMarksMalformedOrOutOfRangeOutputAsTechnicalError() {
        RagAnswerEvaluationResult.JudgeResult malformed =
            RagAnswerEvaluationService.parseJudge("not json");
        RagAnswerEvaluationResult.JudgeResult outOfRange =
            RagAnswerEvaluationService.parseJudge(
                "{\"faithfulness\":3,\"answerRelevance\":2,\"citationCorrectness\":2,"
                    + "\"abstentionCorrectness\":2,\"passed\":true,\"reason\":\"bad\"}");

        assertFalse(malformed.isValid());
        assertTrue(malformed.getTechnicalError().startsWith("JUDGE_OUTPUT_INVALID"));
        assertFalse(outOfRange.isValid());
    }

    @Test
    void aggregateUsesStrictTwoJudgeConsensusAndReportsCaseLatency() {
        RagAnswerEvaluationResult result = new RagAnswerEvaluationResult();
        result.setCases(List.of(
            caseResult(100, true, true),
            caseResult(200, true, false),
            caseResult(900, true, true)));

        RagAnswerEvaluationService.aggregate(result);

        assertEquals(3, result.getJudgedCases());
        assertEquals(2, result.getConsensusPassedCases());
        assertEquals(2.0 / 3.0, result.getConsensusPassRate(), 0.0001);
        assertEquals(200, result.getCaseLatencyP50Ms());
        assertEquals(900, result.getCaseLatencyP95Ms());
    }

    private static RagAnswerEvaluationResult.CaseResult caseResult(
        long durationMs, boolean firstPassed, boolean secondPassed) {
        RagAnswerEvaluationResult.CaseResult result =
            new RagAnswerEvaluationResult.CaseResult();
        result.setDurationMs(durationMs);
        result.setJudgments(List.of(judgment(firstPassed), judgment(secondPassed)));
        return result;
    }

    private static RagAnswerEvaluationResult.JudgeResult judgment(boolean passed) {
        RagAnswerEvaluationResult.JudgeResult result =
            new RagAnswerEvaluationResult.JudgeResult();
        result.setFaithfulness(2);
        result.setAnswerRelevance(2);
        result.setCitationCorrectness(2);
        result.setAbstentionCorrectness(2);
        result.setPassed(passed);
        return result;
    }
}
