# RAG 冻结评测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为现有 RAG 检索增加可冻结的 18 条排序测试集、独立 4 条 No-answer 安全集、Vector Only 与 Vector + BGE Rerank 消融结果和可复现报告。

**Architecture:** 保留 `RagRetrievalService` 作为唯一在线/离线检索入口。评测层从资源文件加载 case，在相同 `recallTopN=20`、`finalTopK=5` 和 Metadata Filter 下分别调用 `skipRerank=true/false`；指标和运行元数据由独立报告 DTO 汇总并写入 JSON。

**Tech Stack:** Spring Boot, JUnit 5, Jackson, LangChain4j, Qdrant, BGE reranker.

## Global Constraints

- 63 条 dev 集只能用于调参，当前 18 条 test 用例冻结为 `frozen-v1`；4 条 No-answer 用例单独冻结为 `safety-v1`。
- 评测不得经过 LLM、Agent、SSE 或用户会话。
- 两种消融模式共享同一 Alias/Collection、Embedding 模型、Metadata Filter、`recallTopN=20`、`finalTopK=5` 和最小向量分数。
- No-answer 不进入 MRR、Hit@K、Recall@K 和 nDCG 的分母；空结果不能计为 Filter Accuracy 正确。
- 报告必须记录 Git commit、Qdrant Alias/Collection、Embedding/Rerank 模型和数据集 SHA-256。

---

## File Structure

- Create: `ruoyi-postgrad/src/test/resources/rag-eval/frozen-v1.jsonl` - 当前 18 条冻结用例。
- Create: `ruoyi-postgrad/src/test/resources/rag-eval/frozen-v1.manifest.json`、`ruoyi-postgrad/src/test/resources/rag-eval/safety-v1.jsonl`、`ruoyi-postgrad/src/test/resources/rag-eval/safety-v1.manifest.json` - 两套数据集的版本、case 数、SHA-256。
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDatasetLoader.java` - JSONL 加载、manifest hash 校验。
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationMode.java` - `VECTOR_ONLY`、`VECTOR_RERANK`。
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationRunReport.java` - 双模式报告、环境元数据和分位延迟。
- Modify: `RagEvaluationCase.java`、`RagEvaluationResult.java`、`RagEvaluationService.java`、`RagSearchTestRequest.java`。
- Test: `RagEvaluationDatasetLoaderTest.java`、`RagEvaluationServiceTest.java`、`RagAdminControllerTraceTest.java`。

### Task 1: 冻结集资源与加载器

**Files:**
- Create: `ruoyi-postgrad/src/test/resources/rag-eval/frozen-v1.jsonl`
- Create: `ruoyi-postgrad/src/test/resources/rag-eval/frozen-v1.manifest.json`
- Create: `ruoyi-postgrad/src/test/resources/rag-eval/safety-v1.jsonl`
- Create: `ruoyi-postgrad/src/test/resources/rag-eval/safety-v1.manifest.json`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDatasetLoader.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDatasetLoaderTest.java`

**Interfaces:** `List<RagEvaluationCase> loadFrozenV1()` 加载 18 条；`List<RagEvaluationCase> loadSafetyV1()` 加载 4 条；两个 `verify...Checksum()` 方法返回 SHA-256，checksum 不一致时抛 `IllegalStateException`。

- [ ] **Step 1: 写失败测试**

```java
@Test
void frozenV1LoadsExactlyEighteenCasesAndMatchesManifest() {
    assertEquals(18, loader.loadFrozenV1().size());
    assertDoesNotThrow(loader::verifyFrozenV1Checksum);
    assertEquals(4, loader.loadSafetyV1().size());
    assertDoesNotThrow(loader::verifySafetyV1Checksum);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationDatasetLoaderTest test`

Expected: FAIL，资源和加载器不存在。

- [ ] **Step 3: 最小实现**

迁移当前 `RagEvaluationDataset.testSet()` 的 18 条到 `frozen-v1`，不改 query、相关性条件或 Metadata；新增 `safety-v1` 四条：不存在学校、库外学校、库外年份、无法唯一定位意图。JSONL 每行一条，manifest 包含 `version`、`caseCount`、`sha256`。加载器读取 Classpath JSONL，用 SHA-256 对源字节校验 manifest；加载数不是 18 或 4 均抛异常。

```java
public List<RagEvaluationCase> loadFrozenV1() { return readJsonLines("rag-eval/frozen-v1.jsonl"); }
public String verifyFrozenV1Checksum() {
    String actual = DigestUtils.sha256Hex(readBytes("rag-eval/frozen-v1.jsonl"));
    if (!actual.equals(manifest().sha256())) throw new IllegalStateException("frozen-v1 checksum mismatch");
    return actual;
}
```

- [ ] **Step 4: 运行测试**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationDatasetLoaderTest test`

Expected: PASS，加载数为 18 且 hash 匹配。

- [ ] **Step 5: 提交**

```bash
git add ruoyi-postgrad/src/test/resources/rag-eval/frozen-v1.jsonl ruoyi-postgrad/src/test/resources/rag-eval/frozen-v1.manifest.json ruoyi-postgrad/src/test/resources/rag-eval/safety-v1.jsonl ruoyi-postgrad/src/test/resources/rag-eval/safety-v1.manifest.json ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDatasetLoader.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDatasetLoaderTest.java
git commit -m "test(rag): freeze v1 evaluation set"
```

### Task 2: 解耦召回池与最终 TopK

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/dto/RagSearchTestRequest.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationService.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationServiceTest.java`

**Interfaces:** 评测请求固定 `maxResults=20`，`rerankTopN=5`，`skipRerank` 由模式控制。

- [ ] **Step 1: 写失败测试**

```java
verify(retrievalService).retrieve(argThat(request ->
    request.getMaxResults() == 20 && request.getRerankTopN() == 5 && request.isSkipRerank()));
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationServiceTest#vectorOnlyUsesRecallTwentyAndFinalTopFive test`

Expected: FAIL，现有服务把 `EVAL_K=5` 同时用作候选池和最终 TopK。

- [ ] **Step 3: 最小实现**

```java
private static final int RECALL_TOP_N = 20;
private static final int FINAL_TOP_K = 5;

private RagSearchTestRequest requestFor(RagEvaluationCase tc, boolean skipRerank) {
    RagSearchTestRequest request = new RagSearchTestRequest(tc.getQuery(), RECALL_TOP_N, 0.5, FINAL_TOP_K);
    request.setSkipRerank(skipRerank);
    applyExpectedFilters(request, tc.getExpectedMetadata());
    return request;
}
```

- [ ] **Step 4: 运行测试**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/dto/RagSearchTestRequest.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationService.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationServiceTest.java
git commit -m "test(rag): separate recall pool from final top k"
```

### Task 3: 正确的指标与 No-answer

**Files:**
- Modify: `RagEvaluationCase.java` - 增加 `boolean noAnswer`。
- Modify: `RagEvaluationResult.java` - 增加正样本数、No-answer 总数、No-answer Accuracy、FPR。
- Modify: `RagEvaluationService.java` - 修正分母、Filter 与 nDCG。
- Test: `RagEvaluationServiceTest.java`。

**Interfaces:** `isNoAnswer()`；`getPositiveCases()`、`getNoAnswerCases()`、`getNoAnswerAccuracy()`、`getFalsePositiveRate()`。

- [ ] **Step 1: 写失败测试**

```java
@Test
void noAnswerCorrectAbstentionDoesNotReducePositiveMetrics() {
    RagEvaluationResult result = service.run(List.of(relevantCase, noAnswerCase));
    assertEquals(1, result.getPositiveCases());
    assertEquals(1, result.getNoAnswerCases());
    assertEquals(1.0, result.getMrr());
    assertEquals(1.0, result.getNoAnswerAccuracy());
    assertEquals(0.0, result.getFalsePositiveRate());
}
```

```java
@Test
void ndcgAtFiveUsesPerPositionRelevanceAndNeverExceedsOne() {
    RagEvaluationResult result = service.run(List.of(caseWithTwoRelevantChunks));
    assertEquals(1.0, result.getNdcg5());
    assertTrue(result.getNdcg5() >= 0.0 && result.getNdcg5() <= 1.0);
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationServiceTest test`

Expected: FAIL，当前 No-answer 混入排序分母，且 nDCG 使用累计相关数。

- [ ] **Step 3: 最小实现**

对 No-answer，只检查最终选中 Chunk 中是否有可用证据；没有为正确拒答，有为 false positive。No-answer 不累计 MRR/Hit/Recall/nDCG。nDCG 使用逐位 relevance 和正确 IDCG。

```java
if (tc.isNoAnswer()) {
    noAnswerTotal++;
    if (hasUsableSelectedEvidence(response, tc)) falsePositive++; else noAnswerCorrect++;
    continue;
}
double dcg = 0.0;
for (int rank = 1; rank <= FINAL_TOP_K; rank++) {
    if (isRelevantAtRank(tc, response, rank)) dcg += 1.0 / log2(rank + 1);
}
double ndcg = dcg / idealDcg(Math.min(expectedRelevantCount(tc), FINAL_TOP_K));
```

Filter Accuracy 只统计正样本且返回至少一个 Chunk 的 case；所有返回 Chunk 均需符合 expectedMetadata。

- [ ] **Step 4: 运行测试**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationCase.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationResult.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationService.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationServiceTest.java
git commit -m "test(rag): add abstention and normalized ranking metrics"
```

### Task 4: 双模式消融与 JSON 报告

**Files:**
- Create: `RagEvaluationMode.java` - `VECTOR_ONLY`、`VECTOR_RERANK`。
- Create: `RagEvaluationRunReport.java` - results、run metadata、P50/P95、报告路径。
- Modify: `RagEvaluationService.java`。
- Test: `RagEvaluationServiceTest.java`。

**Interfaces:** `RagEvaluationRunReport runFrozenV1Ablation()`，返回 `Map<RagEvaluationMode, RagEvaluationResult>`。

- [ ] **Step 1: 写失败测试**

```java
@Test
void frozenAblationRunsBothModesOnSameEighteenCases() {
    RagEvaluationRunReport report = service.runFrozenV1Ablation();
    assertEquals(18, report.getResults().get(VECTOR_ONLY).getTotalCases());
    assertEquals(18, report.getResults().get(VECTOR_RERANK).getTotalCases());
}
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationServiceTest#frozenAblationRunsBothModesOnSameEighteenCases test`

Expected: FAIL，双模式和报告 DTO 不存在。

- [ ] **Step 3: 最小实现**

```java
public RagEvaluationRunReport runFrozenV1Ablation() {
    List<RagEvaluationCase> cases = datasetLoader.loadFrozenV1();
    return new RagEvaluationRunReport(
        run(RagEvaluationMode.VECTOR_ONLY, cases),
        run(RagEvaluationMode.VECTOR_RERANK, cases),
        buildRuntimeMetadata(datasetLoader.verifyFrozenV1Checksum()));
}
```

采用 nearest-rank 计算 P50/P95：`index = ceil(p * n) - 1`。每次运行写入不覆盖的 `target/rag-eval/<UTC timestamp>-frozen-v1.json`。Alias、Collection 或 Git commit 读取失败时写 `unknown` 与原因，禁止伪造值。

- [ ] **Step 4: 运行测试与本地真实评测**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationServiceTest,RagEvaluationDatasetLoaderTest test`

Expected: PASS。随后在 Qdrant Alias 可用的本地环境调用 `runFrozenV1Ablation()`，确认两种模式均为 18 条、报告包含 P50/P95 与 No-answer 指标。

- [ ] **Step 5: 提交**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationMode.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationRunReport.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationService.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationServiceTest.java
git commit -m "test(rag): add frozen rerank ablation report"
```

### Task 5: 管理端摘要与验收

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/controller/RagAdminController.java`
- Modify: `ruoyi-ui/src/api/postgrad/rag.js`
- Modify: `ruoyi-ui/src/views/postgrad/rag/index.vue`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/controller/RagAdminControllerTraceTest.java`

**Interfaces:** `POST /postgrad/rag/evaluation/frozen-v1` 返回双模式摘要和报告路径；UI 只读展示 frozen 样本数、MRR/Hit@5、No-answer Accuracy/FPR。

- [ ] **Step 1: 写失败 Controller 测试**

```java
mockMvc.perform(post("/postgrad/rag/evaluation/frozen-v1"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.results.VECTOR_ONLY.mrr").exists())
    .andExpect(jsonPath("$.data.results.VECTOR_RERANK.hitAt5").exists())
    .andExpect(jsonPath("$.data.results.VECTOR_RERANK.noAnswerAccuracy").exists());
```

- [ ] **Step 2: 运行失败测试**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagAdminControllerTraceTest#frozenEvaluationEndpointReturnsBothModesAndAbstentionMetrics test`

Expected: FAIL，端点不存在。

- [ ] **Step 3: 增加端点和摘要 UI**

```java
@PostMapping("/evaluation/frozen-v1")
public AjaxResult runFrozenEvaluation() {
    return success(evaluationService.runFrozenV1Ablation());
}
```

页面只展示摘要与报告路径，不提供 frozen case 编辑能力。

- [ ] **Step 4: 回归与人工验收**

Run: `mvn -pl ruoyi-postgrad -Dtest=RagAdminControllerTraceTest,RagEvaluationServiceTest,RagEvaluationDatasetLoaderTest test`

Expected: PASS。管理端运行一次冻结评测，确认两模式均为 18 条、No-answer 字段存在、报告路径可见，常规 RAG 检索测试未回归。

- [ ] **Step 5: 提交**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/controller/RagAdminController.java ruoyi-ui/src/api/postgrad/rag.js ruoyi-ui/src/views/postgrad/rag/index.vue ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/controller/RagAdminControllerTraceTest.java
git commit -m "feat(rag): expose frozen evaluation summary"
```

## Self-Review

- Spec coverage: Task 1 完成冻结版本；Task 2 保证公平候选池；Task 3 完成正确指标与拒答；Task 4 生成可复现消融报告；Task 5 提供不修改冻结数据的管理端验收入口。
- No placeholders: 每个任务包含确定的路径、接口、测试、命令与预期。
- Type consistency: 所有新流程只经 `RagEvaluationCase`、`RagEvaluationResult`、`RagEvaluationMode`、`RagEvaluationRunReport` 和既有 `RagRetrievalService`。
