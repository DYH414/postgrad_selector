package com.ruoyi.postgrad.rag.eval.answer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.postgrad.observe.runtime.AiObserveAttributes;
import com.ruoyi.postgrad.rag.dto.RagSearchTestRequest;
import com.ruoyi.postgrad.rag.qdrant.RagCollectionManager;
import com.ruoyi.postgrad.rag.service.RagRetrievalResult;
import com.ruoyi.postgrad.rag.service.RagRetrievalService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagAnswerEvaluationService {

    private static final String ACTIVE_ALIAS = "postgrad_rag_active";
    private static final int RECALL_TOP_N = 20;
    private static final int FINAL_TOP_K = 5;
    private static final int MAX_EVIDENCE_CHARS = 1200;
    private static final DateTimeFormatter REPORT_TIMESTAMP = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final RagRetrievalService retrievalService;
    private final ChatModel chatModel;
    private final RagCollectionManager collectionManager;
    private final RagAnswerEvaluationDatasetLoader datasetLoader;
    private final Path reportDirectory;
    private final String modelName;

    @Autowired
    public RagAnswerEvaluationService(
        RagRetrievalService retrievalService,
        ChatModel chatModel,
        RagCollectionManager collectionManager,
        @Value("${app.ai.deepseek.model-name:unknown}") String modelName) {
        this(retrievalService, chatModel, collectionManager,
            new RagAnswerEvaluationDatasetLoader(), Path.of("target", "rag-eval"), modelName);
    }

    RagAnswerEvaluationService(
        RagRetrievalService retrievalService,
        ChatModel chatModel,
        RagCollectionManager collectionManager,
        RagAnswerEvaluationDatasetLoader datasetLoader,
        Path reportDirectory,
        String modelName) {
        this.retrievalService = retrievalService;
        this.chatModel = chatModel;
        this.collectionManager = collectionManager;
        this.datasetLoader = datasetLoader;
        this.reportDirectory = reportDirectory;
        this.modelName = modelName;
    }

    public RagAnswerEvaluationResult runAnswerV1() {
        List<RagAnswerEvaluationCase> cases = datasetLoader.loadAnswerV1();
        String checksum = datasetLoader.verifyAnswerV1Checksum();
        RagAnswerEvaluationResult result = new RagAnswerEvaluationResult();
        result.setTotalCases(cases.size());
        result.setRunMetadata(runtimeMetadata(checksum));

        List<RagAnswerEvaluationResult.CaseResult> caseResults = new ArrayList<>();
        for (RagAnswerEvaluationCase testCase : cases) {
            caseResults.add(runCase(testCase));
        }
        result.setCases(caseResults);
        aggregate(result);
        writeReports(result);
        return result;
    }

    private RagAnswerEvaluationResult.CaseResult runCase(RagAnswerEvaluationCase testCase) {
        long startedAt = System.nanoTime();
        RagAnswerEvaluationResult.CaseResult result = new RagAnswerEvaluationResult.CaseResult();
        result.setId(testCase.getId());
        result.setQuery(testCase.getQuery());
        result.setCategory(testCase.getCategory());
        try {
            RagRetrievalResult retrieval = retrievalService.retrieve(requestFor(testCase));
            List<RagAnswerEvaluationResult.EvidenceSnapshot> evidence =
                evidenceSnapshots(retrieval.selectedSegments());
            result.setEvidence(evidence);
            String evidenceText = evidenceText(evidence);
            String answer = callModel(answerRequest(testCase, evidenceText), "rag-eval-answer-generator");
            result.setAnswer(answer);
            result.setJudgments(List.of(
                judge(testCase, evidenceText, answer, 1),
                judge(testCase, evidenceText, answer, 2)));
        } catch (Exception e) {
            result.setTechnicalError(safeError(e));
        } finally {
            result.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        }
        return result;
    }

    private RagAnswerEvaluationResult.JudgeResult judge(
        RagAnswerEvaluationCase testCase, String evidence, String answer, int pass) {
        RagAnswerEvaluationResult.JudgeResult result;
        try {
            String raw = callModel(judgeRequest(testCase, evidence, answer, pass),
                "rag-eval-answer-judge");
            result = parseJudge(raw);
        } catch (Exception e) {
            result = new RagAnswerEvaluationResult.JudgeResult();
            result.setTechnicalError(safeError(e));
        }
        return result;
    }

    static RagAnswerEvaluationResult.JudgeResult parseJudge(String raw) {
        RagAnswerEvaluationResult.JudgeResult result = new RagAnswerEvaluationResult.JudgeResult();
        result.setRawOutput(raw);
        try {
            String normalized = raw == null ? "" : raw.trim();
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start < 0 || end <= start) throw new IllegalArgumentException("judge JSON missing");
            JSONObject json = JSON.parseObject(normalized.substring(start, end + 1));
            result.setFaithfulness(score(json, "faithfulness"));
            result.setAnswerRelevance(score(json, "answerRelevance"));
            result.setCitationCorrectness(score(json, "citationCorrectness"));
            result.setAbstentionCorrectness(score(json, "abstentionCorrectness"));
            result.setPassed(json.getBoolean("passed"));
            result.setReason(json.getString("reason"));
            if (result.getPassed() == null) throw new IllegalArgumentException("passed missing");
        } catch (Exception e) {
            result.setTechnicalError("JUDGE_OUTPUT_INVALID: " + safeError(e));
        }
        return result;
    }

    private static int score(JSONObject json, String key) {
        Integer value = json.getInteger(key);
        if (value == null || value < 0 || value > 2) {
            throw new IllegalArgumentException(key + " must be 0..2");
        }
        return value;
    }

    private String callModel(ChatRequest request, String operationKey) {
        return chatModel.chat(request, ChatRequestOptions.builder()
                .addListenerAttribute(AiObserveAttributes.OPERATION_KEY, operationKey)
                .build())
            .aiMessage().text();
    }

    private ChatRequest answerRequest(RagAnswerEvaluationCase testCase, String evidence) {
        String system = """
            你是严格的证据问答助手。只能使用给出的证据回答。
            每个事实句后必须使用 [E1]、[E2] 形式引用证据。
            若证据不足、与问题无关或问题要求证据中不存在的信息，明确回答“现有知识库证据不足”，不要猜测。
            回答控制在 220 个汉字以内，不要输出证据之外的常识。
            """;
        String user = "问题：\n" + testCase.getQuery() + "\n\n证据：\n" + evidence;
        return ChatRequest.builder()
            .messages(SystemMessage.from(system), UserMessage.from(user))
            .build();
    }

    private ChatRequest judgeRequest(
        RagAnswerEvaluationCase testCase, String evidence, String answer, int pass) {
        String system = """
            你是 RAG 回答审计器，不回答原问题。仅根据问题、证据、参考要点和待评回答评分。
            每项 0-2 分：0=失败，1=部分满足，2=完全满足。
            faithfulness：回答中的事实是否都能由证据支持。
            answerRelevance：是否直接回答问题并覆盖参考要点。
            citationCorrectness：引用标签是否存在且支持相邻事实。
            abstentionCorrectness：应拒答时是否拒答；可回答时是否没有无故拒答。
            passed 仅在四项均不少于 1 且 faithfulness=2 时为 true。
            只输出一行 JSON：
            {"faithfulness":2,"answerRelevance":2,"citationCorrectness":2,
             "abstentionCorrectness":2,"passed":true,"reason":"简短理由"}
            """;
        String user = "复核轮次：" + pass
            + "\n问题：\n" + testCase.getQuery()
            + "\n预期拒答：" + testCase.isExpectedAbstention()
            + "\n参考要点：" + JSON.toJSONString(testCase.getRequiredClaims())
            + "\n禁止断言：" + JSON.toJSONString(testCase.getForbiddenClaims())
            + "\n证据：\n" + evidence
            + "\n待评回答：\n" + answer;
        return ChatRequest.builder()
            .messages(SystemMessage.from(system), UserMessage.from(user))
            .build();
    }

    private RagSearchTestRequest requestFor(RagAnswerEvaluationCase testCase) {
        RagSearchTestRequest request =
            new RagSearchTestRequest(testCase.getQuery(), RECALL_TOP_N, 0.5, FINAL_TOP_K);
        request.setSkipRerank(false);
        Map<String, String> metadata = testCase.getExpectedMetadata();
        request.setSchoolId(metadata.get("school_id"));
        request.setSchoolName(metadata.get("school_name"));
        request.setProgramId(metadata.get("program_id"));
        request.setYear(metadata.get("year"));
        request.setDocType(metadata.get("doc_type"));
        request.setSection(metadata.get("section"));
        request.setSourceType(metadata.get("source_type"));
        return request;
    }

    private List<RagAnswerEvaluationResult.EvidenceSnapshot> evidenceSnapshots(
        List<TextSegment> selectedSegments) {
        List<RagAnswerEvaluationResult.EvidenceSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < selectedSegments.size(); index++) {
            TextSegment segment = selectedSegments.get(index);
            RagAnswerEvaluationResult.EvidenceSnapshot snapshot =
                new RagAnswerEvaluationResult.EvidenceSnapshot();
            snapshot.setLabel("E" + (index + 1));
            snapshot.setSourceName(segment.metadata().getString("source_name"));
            snapshot.setContentHash(segment.metadata().getString("content_hash"));
            snapshot.setContent(truncate(segment.text(), MAX_EVIDENCE_CHARS));
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private String evidenceText(List<RagAnswerEvaluationResult.EvidenceSnapshot> evidence) {
        if (evidence.isEmpty()) return "(无可用证据)";
        StringBuilder text = new StringBuilder();
        for (RagAnswerEvaluationResult.EvidenceSnapshot item : evidence) {
            text.append('[').append(item.getLabel()).append("] ")
                .append(item.getSourceName()).append('\n')
                .append(item.getContent()).append("\n\n");
        }
        return text.toString();
    }

    static void aggregate(RagAnswerEvaluationResult result) {
        int generated = 0;
        int generationErrors = 0;
        int judgeErrors = 0;
        int judgedCases = 0;
        int agreementCases = 0;
        int consensusPassedCases = 0;
        double faithfulness = 0;
        double relevance = 0;
        double citations = 0;
        double abstention = 0;
        int validJudgments = 0;

        for (RagAnswerEvaluationResult.CaseResult item : result.getCases()) {
            if (item.getTechnicalError() != null) {
                generationErrors++;
                continue;
            }
            generated++;
            List<RagAnswerEvaluationResult.JudgeResult> valid = item.getJudgments().stream()
                .filter(RagAnswerEvaluationResult.JudgeResult::isValid).toList();
            judgeErrors += item.getJudgments().size() - valid.size();
            for (RagAnswerEvaluationResult.JudgeResult judge : valid) {
                faithfulness += judge.getFaithfulness();
                relevance += judge.getAnswerRelevance();
                citations += judge.getCitationCorrectness();
                abstention += judge.getAbstentionCorrectness();
                validJudgments++;
            }
            if (valid.size() == 2) {
                judgedCases++;
                if (sameJudgment(valid.get(0), valid.get(1))) agreementCases++;
                if (Boolean.TRUE.equals(valid.get(0).getPassed())
                    && Boolean.TRUE.equals(valid.get(1).getPassed())) {
                    consensusPassedCases++;
                }
            }
        }

        result.setGeneratedCases(generated);
        result.setGenerationErrorCases(generationErrors);
        result.setJudgeTechnicalErrors(judgeErrors);
        result.setJudgedCases(judgedCases);
        result.setConsensusPassedCases(consensusPassedCases);
        result.setConsensusPassRate(
            judgedCases == 0 ? 0.0 : (double) consensusPassedCases / judgedCases);
        result.setJudgeAgreementRate(judgedCases == 0 ? 0.0 : (double) agreementCases / judgedCases);
        result.setAverageFaithfulness(meanScore(faithfulness, validJudgments));
        result.setAverageAnswerRelevance(meanScore(relevance, validJudgments));
        result.setAverageCitationCorrectness(meanScore(citations, validJudgments));
        result.setAverageAbstentionCorrectness(meanScore(abstention, validJudgments));
        List<Long> latencies = result.getCases().stream()
            .map(RagAnswerEvaluationResult.CaseResult::getDurationMs)
            .sorted(Comparator.naturalOrder())
            .toList();
        result.setCaseLatencyP50Ms(nearestRank(latencies, 0.50));
        result.setCaseLatencyP95Ms(nearestRank(latencies, 0.95));
    }

    private static boolean sameJudgment(
        RagAnswerEvaluationResult.JudgeResult first,
        RagAnswerEvaluationResult.JudgeResult second) {
        return first.getFaithfulness().equals(second.getFaithfulness())
            && first.getAnswerRelevance().equals(second.getAnswerRelevance())
            && first.getCitationCorrectness().equals(second.getCitationCorrectness())
            && first.getAbstentionCorrectness().equals(second.getAbstentionCorrectness())
            && first.getPassed().equals(second.getPassed());
    }

    private static double meanScore(double total, int count) {
        return count == 0 ? 0.0 : total / count;
    }

    private static long nearestRank(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, index));
    }

    private Map<String, String> runtimeMetadata(String checksum) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("runAtUtc", Instant.now().toString());
        metadata.put("dataset", "answer-v1");
        metadata.put("caseCount", "12");
        metadata.put("checksum", checksum);
        metadata.put("qdrantAlias", ACTIVE_ALIAS);
        metadata.put("qdrantCollection", resolveCollection());
        metadata.put("generatorModel", modelName);
        metadata.put("judgeModel", modelName);
        metadata.put("judgePasses", "2");
        metadata.put("judgeLimitation", "same configured model used for generation and judging");
        metadata.put("recallTopN", Integer.toString(RECALL_TOP_N));
        metadata.put("finalTopK", Integer.toString(FINAL_TOP_K));
        appendGitMetadata(metadata);
        return metadata;
    }

    private String resolveCollection() {
        try {
            String collection = collectionManager.resolveAlias(ACTIVE_ALIAS);
            return collection == null || collection.isBlank() ? "unknown" : collection;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private void appendGitMetadata(Map<String, String> metadata) {
        try {
            Process commit = new ProcessBuilder("git", "rev-parse", "HEAD")
                .redirectErrorStream(true).start();
            if (commit.waitFor(5, TimeUnit.SECONDS) && commit.exitValue() == 0) {
                metadata.put("gitCommit",
                    new String(commit.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
            } else {
                metadata.put("gitCommit", "unknown");
            }
            appendGitDirtyMetadata(metadata);
        } catch (IOException e) {
            metadata.put("gitCommit", "unknown");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metadata.put("gitCommit", "unknown");
        }
    }

    private void appendGitDirtyMetadata(Map<String, String> metadata)
        throws IOException, InterruptedException {
        Path statusOutput = Files.createTempFile("rag-answer-eval-git-status-", ".txt");
        try {
            Process dirty = new ProcessBuilder("git", "status", "--porcelain")
                .redirectErrorStream(true)
                .redirectOutput(statusOutput.toFile())
                .start();
            if (dirty.waitFor(15, TimeUnit.SECONDS) && dirty.exitValue() == 0) {
                metadata.put("gitDirty", Boolean.toString(Files.size(statusOutput) > 0));
            } else {
                dirty.destroyForcibly();
                metadata.put("gitDirty", "unknown");
                metadata.put("gitDirtyReason", "git status timed out or failed");
            }
        } finally {
            Files.deleteIfExists(statusOutput);
        }
    }

    private void writeReports(RagAnswerEvaluationResult result) {
        try {
            Files.createDirectories(reportDirectory);
            String base = REPORT_TIMESTAMP.format(Instant.now()) + "-answer-v1";
            Path json = uniquePath(base, ".json");
            Files.writeString(json, JSON.toJSONString(result), StandardCharsets.UTF_8);
            Path markdown = uniquePath(base, ".md");
            Files.writeString(markdown, markdown(result), StandardCharsets.UTF_8);
            result.setReportPath(json.toString());
            result.getRunMetadata().put("markdownReportPath", markdown.toString());
            Files.writeString(json, JSON.toJSONString(result), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write answer evaluation report", e);
        }
    }

    private Path uniquePath(String base, String extension) throws IOException {
        for (int suffix = 0; ; suffix++) {
            Path path = reportDirectory.resolve(base + (suffix == 0 ? "" : "-" + suffix) + extension);
            if (!Files.exists(path)) return path;
        }
    }

    private String markdown(RagAnswerEvaluationResult result) {
        return """
            # RAG Answer V1 Evaluation

            - Cases: %d
            - Generated: %d
            - Fully judged: %d
            - Judge technical errors: %d
            - Strict consensus pass: %d/%d (%.2f%%)
            - Judge agreement: %.2f%%
            - Case latency P50/P95: %d ms / %d ms
            - Faithfulness mean (0-2): %.3f
            - Answer relevance mean (0-2): %.3f
            - Citation correctness mean (0-2): %.3f
            - Abstention correctness mean (0-2): %.3f

            The generator and judges use the same configured model. These scores are model judgments,
            not human ground truth. Inspect the JSON report for evidence, answers and both raw judgments.
            """.formatted(
            result.getTotalCases(), result.getGeneratedCases(), result.getJudgedCases(),
            result.getJudgeTechnicalErrors(),
            result.getConsensusPassedCases(), result.getJudgedCases(),
            result.getConsensusPassRate() * 100,
            result.getJudgeAgreementRate() * 100,
            result.getCaseLatencyP50Ms(), result.getCaseLatencyP95Ms(),
            result.getAverageFaithfulness(), result.getAverageAnswerRelevance(),
            result.getAverageCitationCorrectness(), result.getAverageAbstentionCorrectness());
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static String safeError(Throwable error) {
        String message = error.getMessage();
        String value = message == null || message.isBlank()
            ? error.getClass().getSimpleName() : message;
        return truncate(value, 512);
    }
}
