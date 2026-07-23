# RAG Frozen V3 And LLM Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and run a checksum-frozen RAG retrieval and LLM answer benchmark with correct metric denominators and reproducible reports.

**Architecture:** Extend the current evaluation module instead of creating a second framework. Retrieval and safety datasets remain deterministic JSONL fixtures; LLM answer evaluation is a separate service that consumes selected retrieval evidence and persists raw judge artifacts in the run report.

**Tech Stack:** Java 17, Spring Boot, LangChain4j 1.15, Qdrant, JUnit 5, Fastjson2, Maven.

## Global Constraints

- Do not modify or regenerate `frozen-v1` or `safety-v1`.
- Do not overwrite unrelated dirty-worktree changes.
- Positive retrieval, safety abstention and LLM answer metrics use separate denominators.
- nDCG must remain in `[0, 1]`, including repeated chunks from one source.
- LLM-as-judge scores must be labeled as model judgments, not human ground truth.
- Reports must contain dataset checksums, Git state, resolved Qdrant collection and model names.

---

### Task 1: Correct Ranking Metric Semantics

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationService.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationResult.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationServiceTest.java`

**Interfaces:**
- Consumes: `RagEvaluationCase` relevance sources and conditions.
- Produces: bounded nDCG, unique-unit Recall/Precision and explicit filter evaluation counts.

- [x] **Step 1: Add failing duplicate-source and denominator tests**

Add tests where three chunks from one relevant source occupy ranks 1-3 while only one source is expected. Assert `nDCG@5 == 1.0`, `Recall@5 == 1.0`, and safety cases do not affect MRR.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -pl ruoyi-postgrad -am -DskipTests=false -Dtest=RagEvaluationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the duplicate-source nDCG assertion fails against the current implementation.

- [x] **Step 3: Implement canonical relevance-unit deduplication**

Track the first matching expected source or condition at each rank. Count each relevance unit once for DCG, Recall and Precision while preserving first relevant rank for MRR.

- [x] **Step 4: Expose filter evaluated and empty-result counts**

Add integer fields to `RagEvaluationResult` so a reported Filter Accuracy always shows its denominator and cannot present empty retrievals as successful filtering.

- [x] **Step 5: Run focused and regression tests**

Run the focused command above and existing RAG evaluation workbench tests. Expected: all pass and every nDCG assertion is within `[0, 1]`.

### Task 2: Add Frozen V3 And Safety V3

**Files:**
- Create: `ruoyi-postgrad/src/main/resources/rag-eval/frozen-v3.jsonl`
- Create: `ruoyi-postgrad/src/main/resources/rag-eval/frozen-v3.manifest.json`
- Create: `ruoyi-postgrad/src/main/resources/rag-eval/safety-v3.jsonl`
- Create: `ruoyi-postgrad/src/main/resources/rag-eval/safety-v3.manifest.json`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDatasetLoader.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDatasetLoaderTest.java`

**Interfaces:**
- Produces: `loadFrozenV3()`, `loadSafetyV3()`, `verifyFrozenV3Checksum()` and `verifySafetyV3Checksum()`.

- [x] **Step 1: Add failing loader contract tests**

Assert exactly 36 positive cases, 12 safety cases, globally unique IDs and queries, exact `content_hash` relevance for every positive case, explicit no-answer semantics for every safety case, and matching manifests.

- [x] **Step 2: Verify the tests fail because V3 resources are absent**

Run:

```powershell
mvn -pl ruoyi-postgrad -am -DskipTests=false -Dtest=RagEvaluationDatasetLoaderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [x] **Step 3: Build ground truth from indexed Markdown evidence**

Select cases from indexed general-knowledge and school-guide evidence. Encode exact reviewed `content_hash` relevance. Keep MySQL-owned score and enrollment facts out of this RAG benchmark.

- [x] **Step 4: Generate SHA-256 manifests**

Hash the exact UTF-8 JSONL bytes and store `version`, `caseCount` and lowercase SHA-256 in each manifest.

- [x] **Step 5: Implement loader methods and run tests**

Expected: loader and checksum tests pass without touching V1 resources.

### Task 3: Add Frozen V3 Paired Retrieval Run

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationService.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationRunReport.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/controller/RagAdminController.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationServiceTest.java`

**Interfaces:**
- Produces: `runFrozenV3Ablation()` and `POST /postgrad/rag/eval/frozen-v3`.

- [x] **Step 1: Add a failing report contract test**

Assert both modes use the same 36 ranking and 12 safety cases, expose paired improvement/tie/regression counts, and record both checksums.

- [x] **Step 2: Implement paired comparison and runtime metadata**

Record Qdrant alias, concrete collection, Git commit, dirty flag, retrieval parameters and model identifiers.

- [x] **Step 3: Add the protected administrator endpoint**

Use the existing `postgrad:rag:eval` permission and return the generated report.

- [x] **Step 4: Run evaluation and controller regression tests**

Expected: deterministic report tests pass without requiring a live Qdrant instance.

### Task 4: Add Answer V1 And LLM Evaluation

**Files:**
- Create: `ruoyi-postgrad/src/main/resources/rag-eval/answer-v1.jsonl`
- Create: `ruoyi-postgrad/src/main/resources/rag-eval/answer-v1.manifest.json`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/answer/RagAnswerEvaluationCase.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/answer/RagAnswerEvaluationResult.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/answer/RagAnswerEvaluationService.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/answer/RagAnswerEvaluationServiceTest.java`

**Interfaces:**
- Consumes: `RagRetrievalService`, `ChatModel`, 12 answer cases.
- Produces: one generated answer and two independent rubric judgments per successful case.

- [x] **Step 1: Add failing deterministic parser and aggregation tests**

Cover valid judge JSON, malformed output, disagreement, expected abstention, citation mismatch and technical errors.

- [x] **Step 2: Implement strict answer and judge prompts**

The answer prompt permits only supplied evidence. The judge prompt returns JSON fields for faithfulness, relevance, citation correctness, abstention correctness and reason.

- [x] **Step 3: Implement two-pass judging and transparent aggregation**

Keep both raw judgments, calculate agreement, and exclude judge technical errors from quality denominators.

- [x] **Step 4: Validate the answer dataset checksum**

Assert 12 cases, stable IDs, expected facts or abstention semantics, and expected source prefixes.

- [x] **Step 5: Run answer-evaluation unit tests**

Expected: all tests pass with mocked model responses and no network access.

### Task 5: Run Real Qdrant And LLM Acceptance

**Files:**
- Create: `target/rag-eval/<timestamp>-frozen-v3.json`
- Create: `target/rag-eval/<timestamp>-frozen-v3.md`
- Create: `target/rag-eval/<timestamp>-answer-v1.json`
- Create: `target/rag-eval/<timestamp>-answer-v1.md`

**Interfaces:**
- Consumes: running MySQL, Redis, Qdrant, backend and IDEA environment variables.
- Produces: immutable local acceptance artifacts and verified resume metrics.

- [x] **Step 1: Run unit and module regression tests**

Run all RAG evaluation, retrieval and workbench tests. Expected: PASS.

- [x] **Step 2: Start dependencies and backend with IDEA environment**

Verify Qdrant alias resolution and record the concrete collection before testing.

- [x] **Step 3: Execute Frozen V3 in both modes**

Verify 36 positive cases and 12 safety cases complete in both modes with no unreported technical failures.

- [x] **Step 4: Execute Answer V1**

Run 12 generations and two judge calls per case. Retain raw output and mark any provider error separately.

- [x] **Step 5: Export JSON and Markdown reports**

Verify all required metadata, formulas, denominators, per-case evidence and limitations are present.

- [x] **Step 6: Produce the final evidence summary**

Report only metrics found in the generated artifacts. Identify which values are suitable for a resume, interview discussion, or neither.
