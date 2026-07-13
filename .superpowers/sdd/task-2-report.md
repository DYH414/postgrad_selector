# Task 2 Report: Separate Recall Pool from Final TopK

## Result

Implemented vector-only evaluation request separation:

- Recall pool: `maxResults=20`
- Final selection: `rerankTopN=5`
- Vector-only mode: `skipRerank=true`

The evaluation metrics continue to use final Top5 semantics. No controller or UI
files were changed.

The existing evaluation service and dataset changes are integrated prerequisites
for this feature: `RagEvaluationService` directly references
`RagEvaluationDataset`, so the dataset source is intentionally included for a
reproducible checkout. This does not expand Task2 behavior beyond the request
pool/final-selection change.

The dataset source was inspected against frozen-v1 semantics. Its test groups
contain 3 fact + 5 trend + 3 quality + 3 general + 4 edge cases = 18 cases,
matching frozen-v1. The case IDs, queries, expected metadata, and relevant
conditions are preserved; Task1 loader tests verify representative query and
metadata preservation plus the frozen resource checksum. The source comment was
corrected from 20 to the actual 18 test cases; case data was not changed.

## TDD Evidence

1. RED: added `vectorOnlyUsesRecallTwentyAndFinalTopFive` to
   `RagEvaluationServiceTest` and ran:
   `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationServiceTest#vectorOnlyUsesRecallTwentyAndFinalTopFive test`
2. RED result: failed as expected because the actual request did not match
   `maxResults=20`, `rerankTopN=5`, and `skipRerank=true`.
3. GREEN: added `RECALL_TOP_N`, `FINAL_TOP_K`, and `requestFor(...)` in
   `RagEvaluationService`.
4. GREEN result: targeted test passed, then the full evaluation test class
   passed with 6 tests, 0 failures, and 0 errors.

## Files

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/dto/RagSearchTestRequest.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationService.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/RagEvaluationDataset.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/RagEvaluationServiceTest.java`

## Commit

`b72e4a2c` - `test(rag): separate recall pool from final top k`

Review prerequisite commit:

`5fcd0e0e` - `test(rag): include evaluation dataset prerequisite`

## Concerns

- Maven reports the existing warning that `maven-surefire-plugin` has no
  explicit version.
- Git's post-commit automatic gc reported a pre-existing bad checkpoint ref;
  the commit itself completed successfully.

## Review Fix Evidence

Commands and results:

- `mvn -pl ruoyi-postgrad -DskipTests compile`: `BUILD SUCCESS`; compilation
  included the now-intentionally tracked dataset source.
- `mvn -pl ruoyi-postgrad -Dtest=RagEvaluationDatasetLoaderTest test`: 6 tests,
  0 failures, 0 errors; `BUILD SUCCESS`.
- After committing the dataset source, clean `mvn -pl ruoyi-postgrad clean
  test` completed with 218 tests, 0 failures, and 0 errors; Maven reported
  `BUILD SUCCESS`. This confirms compilation/test execution does not rely on
  the dataset remaining untracked.

The broader service/dataset files remain integrated feature prerequisites and
were not reverted to artificially narrow the commit.

## Retrieval Guard Review Fix

Root cause: the working-tree `RagRetrievalService` already contained the
necessary `request.isSkipRerank() ? null : rerankerProvider.getIfAvailable()`
guard, but that pre-existing change was not included in the Task2 commit.

The guard and its focused regression test are now intentionally owned in:

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/service/RagRetrievalService.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/service/RagRetrievalServiceTest.java`

TDD evidence:

1. RED: temporarily reverted only the working-tree guard, then ran
   `mvn -pl ruoyi-postgrad -Dtest=RagRetrievalServiceTest#retrieveShouldSkipRerankerAndPreserveVectorOrderWhenRequested test`.
   It failed at `verifyNoInteractions(reranker)` because the reranker was
   invoked.
2. GREEN: restored the existing guard without changing its behavior. The same
   focused test passed with 1 test, 0 failures, and 0 errors.
3. Full focused class passed with
   `mvn -pl ruoyi-postgrad -Dtest=RagRetrievalServiceTest test`: 4 tests,
   0 failures, and 0 errors.

Commit: `735866a0` - `test(rag): cover vector-only rerank bypass`.

The new test proves `skipRerank=true` does not invoke
`SiliconFlowReranker` and preserves vector order in selected segments. No
controller, UI, or other uncommitted evaluation files were modified.
