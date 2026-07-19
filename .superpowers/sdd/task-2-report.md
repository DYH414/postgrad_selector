# Task 2 Report: Paginated and Filterable Evaluation Runs

## Status

Implemented the run-list query contract:

- `GET /postgrad/rag-evaluation/runs?pageNum=1&pageSize=10&datasetId=&status=&runIdKeyword=&datasetChecksum=`
- `RagEvalRunService.list(Long, String, String, String)`
- `RagEvaluationWorkbenchMapper.selectRuns(Long, String, String, String)`

The controller keeps `startPage()` immediately before the service call.
`runIdKeyword` and `datasetChecksum` are trimmed to `null` for blank input in
the service. The mapper uses MyBatis bound parameters for both new filters:
`run_id LIKE CONCAT('%', #{runIdKeyword}, '%')` and
`dataset_checksum = #{datasetChecksum}`. Existing dataset/status filtering and
`ORDER BY created_at DESC` are retained.

NEEDS_CONTEXT: No. The stale report content was unrelated, but did not affect
implementation or verification of this task.

## TDD Evidence

### RED

Added these tests before changing production code:

- `runListingPassesAllFiltersToService` uses `pageNum=2` and `pageSize=10`,
  calls `listRuns(7L, "SUCCEEDED", "abc123", "checksum-v1")`, and verifies
  the exact four-argument service call.
- `selectRunsUsesBoundKeywordAndChecksumFilters` checks guarded XML predicates,
  the bound `LIKE CONCAT` expression, checksum binding, and the absence of
  `${runIdKeyword}` / `${datasetChecksum}` interpolation.

Exact PowerShell command (Maven properties are quoted so PowerShell passes the
commas and dots as one argument):

```powershell
mvn -pl ruoyi-postgrad -am '-Dtest=RagEvaluationWorkbenchControllerTest,RagEvaluationWorkbenchMapperXmlTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD FAILURE` during `testCompile`, as expected. The compiler
reported that `RagEvalRunService.list` and controller `listRuns` required only
`Long, String`, but the new tests supplied `Long, String, String, String`.

### GREEN

Implemented the four-parameter controller, service, and mapper contracts plus
the two bound XML predicates. Re-ran the exact quoted focused-test command:

```powershell
mvn -pl ruoyi-postgrad -am '-Dtest=RagEvaluationWorkbenchControllerTest,RagEvaluationWorkbenchMapperXmlTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`; 6 tests run, 0 failures, 0 errors, 0 skipped.

## Package Verification

```powershell
mvn -pl ruoyi-postgrad -am -DskipTests package
```

Result: `BUILD SUCCESS`; `ruoyi-postgrad-3.9.2.jar` was built.

## Scope

Changed only these assigned files:

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/controller/RagEvaluationWorkbenchController.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/service/RagEvalRunService.java`
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/mapper/RagEvaluationWorkbenchMapper.java`
- `ruoyi-postgrad/src/main/resources/mapper/postgrad/RagEvaluationWorkbenchMapper.xml`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchControllerTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchMapperXmlTest.java`

This report is intentionally not included in the source commit.

## Commit

`ede69cf1` - `feat: filter and page rag evaluation runs`

The commit contains only the six files listed in Scope. Git's automatic
post-commit garbage collection reported a pre-existing invalid checkpoint ref;
the commit completed successfully before that maintenance step failed.

## Concerns

- Maven emits the existing warning that `maven-surefire-plugin` has no explicit
  version. It did not affect the focused tests or package build.
- Git automatic garbage collection reported a bad checkpoint ref after commit;
  this did not prevent creation of `ede69cf1`.

## Review Fix Evidence

### Added Regression Coverage

- `runListingTrimsKeywordAndNullsBlankChecksumBeforeMapperInvocation` proves
  `"  abc  "` reaches `selectRuns` as `"abc"`, while a spaces-only checksum
  reaches it as `null`.
- `runListingStartsRequestedPageBeforePassingFiltersToService` inspects
  `PageHelper.getLocalPage()` inside the mocked service invocation and proves
  page 2 with a page size of 10 already exists before the service is called.
- `@AfterEach` now calls `PageHelper.clearPage()` before resetting request
  attributes, preventing thread-local pagination leakage between tests.

### RED

To verify the new tests detect regressions, the existing correct implementation
was temporarily mutated to pass raw filter values and to call `startPage()`
after the service. The focused command was:

```powershell
mvn -pl ruoyi-postgrad -am '-Dtest=RagEvaluationWorkbenchControllerTest,RagEvaluationWorkbenchMapperXmlTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD FAILURE`; 7 tests run with 2 expected failures. The pagination
test found no local page at service-invocation time, and the service regression
test observed raw `"  abc  "` / `"   "` values instead of `"abc"` / `null`.
The correct controller ordering and `StringUtils.trimToNull` calls were then
restored.

### GREEN

The normal test JVM initially exhausted native memory during test compilation;
the crash report showed a 4 GB default heap with only about 130 MB of pagefile
available. The rerun bounded parent and surefire JVM memory and preloaded the
project's resolved Byte Buddy 1.17.8 agent to avoid resource-constrained
runtime self-attachment:

```powershell
$env:MAVEN_OPTS='-Xmx768m -XX:MaxMetaspaceSize=256m'
$agent='C:\Users\111\.m2\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar'
mvn -pl ruoyi-postgrad -am '-Dtest=RagEvaluationWorkbenchControllerTest,RagEvaluationWorkbenchMapperXmlTest' '-Dsurefire.failIfNoSpecifiedTests=false' "-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m -javaagent:$agent" test
```

Result: `BUILD SUCCESS`; 7 tests run, 0 failures, 0 errors, 0 skipped.
