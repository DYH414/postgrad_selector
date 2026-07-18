# RAG Evaluation Admin Usability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the RAG status and evaluation admin pages truthful, resilient, paginated, readable, and safe for repeated operational use.

**Architecture:** Qdrant remains the source of truth for the searchable collection and realtime point count; MySQL build metadata is attached only when it belongs to the collection resolved by the active alias. The existing RuoYi paged controller contract is extended with run filters, while the Vue 2 workbench formats the existing metrics payload into operator-facing views and only polls active runs.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, Mockito, RuoYi `TableDataInfo`, Vue 2, Element UI, Qdrant.

## Global Constraints

- Do not change retrieval, rerank, or evaluation metric formulas.
- Do not add a new frontend dependency or evaluation framework.
- Preserve existing `/postgrad/rag/status`, `/postgrad/rag-evaluation/**`, export, and comparison contracts unless fields are added compatibly.
- Qdrant alias resolution and realtime point count are authoritative for the active searchable index.
- Missing values display as `--`; numeric zero remains `0`.
- Raw JSON remains available only as a collapsed diagnostic view.
- Do not delete historical evaluation runs or Qdrant collections.

---

## File Structure

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/RagStatus.java`: additive index consistency response fields.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/status/RagIndexStatusService.java`: reconcile alias, build metadata, and realtime Qdrant count.
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/status/RagIndexStatusServiceTest.java`: consistency-state service tests.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/controller/RagEvaluationWorkbenchController.java`: accept run list filters while retaining RuoYi paging.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/service/RagEvalRunService.java`: pass the run query object to persistence.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/mapper/RagEvaluationWorkbenchMapper.java`: filtered run query signature.
- `ruoyi-postgrad/src/main/resources/mapper/postgrad/RagEvaluationWorkbenchMapper.xml`: dataset, status, run ID, and checksum predicates.
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchControllerTest.java`: page/filter contract coverage.
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchMapperXmlTest.java`: SQL predicate coverage.
- `ruoyi-ui/src/layout/components/Sidebar/SidebarItem.vue`: invalid route-path guard.
- `ruoyi-ui/src/components/HeaderSearch/index.vue`: invalid route/search-path guard.
- `ruoyi-ui/src/views/postgrad/rag-evaluation/index.vue`: paging, filters, conditional polling, metric/result presentation, and comparison selectors.
- `ruoyi-ui/src/api/postgrad/ragEvaluation.js`: retain the existing request functions; only update if a parameter normalization helper is needed.

### Task 1: Truthful active index status

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/RagStatus.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/status/RagIndexStatusService.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/status/RagIndexStatusServiceTest.java`

**Interfaces:**
- Produces: `RagStatus.indexConsistency`, `indexConsistencyMessage`, `expectedDocumentCount`, `expectedChunkCount`, and `actualPointCount`.
- Consumes: `RagIndexRun.candidateCollection`, `indexedChunkCount`, document counts, and `RagCollectionManager.getPointCount(String)`.

- [ ] **Step 1: Add failing consistency tests**

Add cases that set `candidateCollection` explicitly and assert:

```java
assertEquals("CONSISTENT", status.getIndexConsistency());
assertEquals(15, status.getExpectedDocumentCount());
assertEquals(200, status.getExpectedChunkCount());
assertEquals(200L, status.getActualPointCount());
```

Add a mismatched-count case expecting `MISMATCH`, and an alias/run collection mismatch case expecting `UNKNOWN`, `documentCount == 0`, and no reuse of the unrelated run metadata.

- [ ] **Step 2: Run the status test and verify failure**

Run:

```powershell
mvn -pl ruoyi-postgrad -am -Dtest=RagIndexStatusServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or assertion failure because the consistency properties do not exist and collection identity is not checked.

- [ ] **Step 3: Add the additive response fields**

Add fields and conventional getters/setters to `RagStatus`:

```java
private String indexConsistency;
private String indexConsistencyMessage;
private Integer expectedDocumentCount;
private Integer expectedChunkCount;
private Long actualPointCount;
```

Keep `documentCount` and `chunkCount` for compatibility. Populate them only from the matching active build and realtime point count respectively.

- [ ] **Step 4: Reconcile the active run with the alias**

Implement these decisions in `getStatus()`:

```java
boolean matchingRun = activeRun != null
    && activeCollection != null
    && activeCollection.equals(activeRun.getCandidateCollection())
    && "SUCCEEDED".equals(activeRun.getStatus());

long pointCount = activeCollection == null
    ? -1L
    : collectionManager.getPointCount(activeCollection);
status.setActualPointCount(pointCount >= 0 ? pointCount : null);
status.setChunkCount((int) Math.max(pointCount, 0));
status.setIndexed(activeCollection != null && pointCount >= 0);
```

For a matching run, set expected counts and return `CONSISTENT` only when expected chunks equal points; otherwise return `MISMATCH`. Without matching metadata, return `UNKNOWN` with a concise Chinese operator message and leave expected document/chunk fields unset.

- [ ] **Step 5: Run the focused and module tests**

Run:

```powershell
mvn -pl ruoyi-postgrad -am -Dtest=RagIndexStatusServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl ruoyi-postgrad -am -DskipTests package
```

Expected: status tests pass and the module compiles.

- [ ] **Step 6: Commit the status contract**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/RagStatus.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/status/RagIndexStatusService.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/status/RagIndexStatusServiceTest.java
git commit -m "fix: reconcile active rag index status"
```

### Task 2: Paginated and filterable evaluation runs

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/controller/RagEvaluationWorkbenchController.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/service/RagEvalRunService.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/mapper/RagEvaluationWorkbenchMapper.java`
- Modify: `ruoyi-postgrad/src/main/resources/mapper/postgrad/RagEvaluationWorkbenchMapper.xml`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchControllerTest.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchMapperXmlTest.java`

**Interfaces:**
- Produces: `GET /postgrad/rag-evaluation/runs?pageNum=1&pageSize=10&datasetId=&status=&runIdKeyword=&datasetChecksum=`.
- Produces: `RagEvalRunService.list(Long, String, String, String)`.
- Produces: `RagEvaluationWorkbenchMapper.selectRuns(Long, String, String, String)`.

- [ ] **Step 1: Add failing controller and mapper tests**

Use a `MockHttpServletRequest` with `pageNum=2`, `pageSize=10`, and call:

```java
controller.listRuns(7L, "SUCCEEDED", "abc123", "checksum-v1");
verify(runs).list(7L, "SUCCEEDED", "abc123", "checksum-v1");
```

In the XML test, assert the mapper contains guarded predicates for `runIdKeyword` and `datasetChecksum`, including a bound `LIKE CONCAT('%', #{runIdKeyword}, '%')` expression rather than interpolated `${...}` SQL.

- [ ] **Step 2: Run tests and verify the new signature fails**

```powershell
mvn -pl ruoyi-postgrad -am -Dtest=RagEvaluationWorkbenchControllerTest,RagEvaluationWorkbenchMapperXmlTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation/assertion failure because only dataset and status filters exist.

- [ ] **Step 3: Extend the controller and service signatures**

Keep `startPage()` before the service call:

```java
public TableDataInfo listRuns(Long datasetId, String status,
                              String runIdKeyword, String datasetChecksum) {
    startPage();
    return getDataTable(runs.list(datasetId, status, runIdKeyword, datasetChecksum));
}
```

Trim blank keyword/checksum values to `null` in the service before invoking the mapper.

- [ ] **Step 4: Add safe MyBatis predicates**

Expand `selectRuns` using bound parameters:

```xml
<if test="runIdKeyword != null and runIdKeyword != ''">
  AND run_id LIKE CONCAT('%', #{runIdKeyword}, '%')
</if>
<if test="datasetChecksum != null and datasetChecksum != ''">
  AND dataset_checksum = #{datasetChecksum}
</if>
```

Retain `ORDER BY created_at DESC` and existing dataset/status predicates.

- [ ] **Step 5: Run focused tests and package the backend**

```powershell
mvn -pl ruoyi-postgrad -am -Dtest=RagEvaluationWorkbenchControllerTest,RagEvaluationWorkbenchMapperXmlTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl ruoyi-postgrad -am -DskipTests package
```

Expected: filter contract tests pass and backend packaging succeeds.

- [ ] **Step 6: Commit the run query contract**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/controller/RagEvaluationWorkbenchController.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/service/RagEvalRunService.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/eval/workbench/mapper/RagEvaluationWorkbenchMapper.java ruoyi-postgrad/src/main/resources/mapper/postgrad/RagEvaluationWorkbenchMapper.xml ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchControllerTest.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/eval/workbench/RagEvaluationWorkbenchMapperXmlTest.java
git commit -m "feat: filter and page rag evaluation runs"
```

### Task 3: Resilient dynamic navigation

**Files:**
- Modify: `ruoyi-ui/src/layout/components/Sidebar/SidebarItem.vue`
- Modify: `ruoyi-ui/src/components/HeaderSearch/index.vue`

**Interfaces:**
- Produces: `normalizeRoutePath(value)` behavior local to each component: non-string/blank values are rejected before path helpers run.
- Consumes: backend-provided dynamic route objects.

- [ ] **Step 1: Record the current failure with a production build baseline**

Run:

```powershell
npm --prefix ruoyi-ui run build:prod
```

Expected: build succeeds, while browser reproduction currently logs `path.resolve` or `indexOf` exceptions for malformed runtime menu data.

- [ ] **Step 2: Guard sidebar path resolution**

At the start of `resolvePath`, normalize input:

```javascript
const normalizedPath = typeof routePath === 'string' ? routePath.trim() : ''
if (!normalizedPath) {
  if (process.env.NODE_ENV === 'development') console.warn('[SidebarItem] skipped invalid route path', this.item && this.item.name)
  return typeof this.basePath === 'string' && this.basePath ? this.basePath : '/'
}
```

Use `normalizedPath` for `isExternal` and `path.resolve`. Parse `routeQuery` in a `try/catch`; invalid query JSON should navigate without query instead of breaking the menu.

- [ ] **Step 3: Skip invalid header-search entries**

In `generateRoutes`, reject malformed entries before `isHttp`:

```javascript
if (!router || typeof router.path !== 'string' || !router.path.trim()) {
  if (process.env.NODE_ENV === 'development') console.warn('[HeaderSearch] skipped invalid route', router && router.name)
  continue
}
const routePath = router.path.trim()
```

Use `routePath` for path resolution and recurse only with a valid `data.path`.

- [ ] **Step 4: Build and manually exercise navigation**

```powershell
npm --prefix ruoyi-ui run build:prod
```

Expected: production build succeeds. In the browser, expand the RAG menu and use header search without `Arguments to path.resolve must be strings` or `reading 'indexOf'` errors.

- [ ] **Step 5: Commit navigation resilience**

```powershell
git add ruoyi-ui/src/layout/components/Sidebar/SidebarItem.vue ruoyi-ui/src/components/HeaderSearch/index.vue
git commit -m "fix: tolerate malformed dynamic menu paths"
```

### Task 4: Evaluation run filters, pagination, and conditional polling

**Files:**
- Modify: `ruoyi-ui/src/views/postgrad/rag-evaluation/index.vue`
- Modify: `ruoyi-ui/src/api/postgrad/ragEvaluation.js` only if request normalization is required.

**Interfaces:**
- Consumes: paged `rows` and `total` from Task 2.
- Produces: `runQuery = { pageNum, pageSize, datasetId, status, runIdKeyword }` and `runTotal`.

- [ ] **Step 1: Add run query state and filter controls**

Add state:

```javascript
runQuery: { pageNum: 1, pageSize: 10, datasetId: null, status: '', runIdKeyword: '' },
runTotal: 0,
poller: null
```

Add dataset/status selectors, a run ID input, query/reset buttons, created/finished time columns, and an `el-pagination` bound to `runTotal`.

- [ ] **Step 2: Replace the fixed 100-row request**

Implement:

```javascript
async loadRuns() {
  this.loading.runs = true
  try {
    const response = await api.listRuns(this.runQuery)
    this.runRows = response.rows || []
    this.runTotal = Number(response.total || 0)
    this.syncRunPolling()
  } finally {
    this.loading.runs = false
  }
}
```

Filter changes reset `pageNum` to 1. Pagination changes call `loadRuns()` without resetting filters.

- [ ] **Step 3: Poll only active runs**

Define terminal-aware polling:

```javascript
syncRunPolling() {
  const active = this.runRows.some(row => ['PENDING', 'RUNNING'].includes(row.status))
  if (active && !this.poller) this.poller = setInterval(() => this.loadRuns(), 3000)
  if (!active && this.poller) { clearInterval(this.poller); this.poller = null }
}
```

Call it after a run is submitted and after each load; clear it in `beforeDestroy`.

- [ ] **Step 4: Build and verify the page interactions**

```powershell
npm --prefix ruoyi-ui run build:prod
```

Expected: build succeeds; browser requests use `pageSize=10`; filters combine correctly; paging changes rows; no 3-second requests continue when all visible runs are terminal.

- [ ] **Step 5: Commit the run management UI**

```powershell
git add ruoyi-ui/src/views/postgrad/rag-evaluation/index.vue ruoyi-ui/src/api/postgrad/ragEvaluation.js
git commit -m "feat: manage rag evaluation run history"
```

### Task 5: Readable metrics, cases, and run comparison

**Files:**
- Modify: `ruoyi-ui/src/views/postgrad/rag-evaluation/index.vue`

**Interfaces:**
- Consumes: `run.aggregateMetricsJson`, case result fields, and `compareRuns` response.
- Produces: parsed metric groups keyed by evaluation mode and compatible comparison selection labels.

- [ ] **Step 1: Add defensive metrics normalization**

Add helpers that preserve zero and distinguish missing values:

```javascript
parseObject(value) {
  if (!value) return {}
  try { return typeof value === 'string' ? JSON.parse(value) : value }
  catch (_) { return {} }
},
metricValue(metrics, key, percent = false) {
  const value = metrics && metrics[key]
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '--'
  return percent ? `${(Number(value) * 100).toFixed(1)}%` : Number(value).toFixed(3)
}
```

Expose metric rows for MRR, Hit@1/3/5, nDCG@5, Pass Rate, Filter Accuracy, No-answer Accuracy, and False Positive Rate for each mode.

- [ ] **Step 2: Replace raw metrics with operator-facing summaries**

In the run drawer, show mode sections with compact metric cards/table. Move `aggregateMetricsJson` and `parameterSnapshotJson` into collapsed `el-collapse-item` diagnostics. Show `--` for absent metrics and `0` for real zero.

- [ ] **Step 3: Improve case result diagnosis**

Add an evaluation mode filter alongside existing failed/technical filters. Translate known failure values through a fixed display map while retaining the original value in the diagnostic dialog. Include query text from `runDetail.cases`, rank, source/evidence summary, and latency.

- [ ] **Step 4: Make comparison run selection meaningful**

Format completed run options as:

```javascript
runOptionLabel(row) {
  const metrics = this.primaryMetrics(row)
  return `${this.formatTime(row.finishedAt)} · ${row.datasetChecksum ? row.datasetChecksum.slice(0, 8) : '--'} · ${this.modeLabel(row.modesJson)} · MRR ${this.metricValue(metrics, 'mrr')} · ${row.runId.slice(0, 8)}`
}
```

Only succeeded runs are selectable. Default to the newest two runs sharing a dataset checksum. Disable comparison until two distinct runs are selected. Preserve the backend warning and suppress delta emphasis when `compatible === false`.

- [ ] **Step 5: Build and perform visual verification**

```powershell
npm --prefix ruoyi-ui run build:prod
```

Expected: build succeeds; completed results are understandable without opening JSON; Vector Only and Vector + Rerank are side by side; no-answer zero values are not rendered as missing; incompatible runs show a warning without a percentage claim.

- [ ] **Step 6: Commit result presentation**

```powershell
git add ruoyi-ui/src/views/postgrad/rag-evaluation/index.vue
git commit -m "feat: present rag evaluation metrics clearly"
```

### Task 6: Status UI and end-to-end acceptance

**Files:**
- Modify: `ruoyi-ui/src/views/postgrad/rag/index.vue`
- Verify: `ruoyi-ui/src/views/postgrad/rag-evaluation/index.vue`
- Verify: backend and frontend runtime configuration from the existing IDEA run configuration.

**Interfaces:**
- Consumes: Task 1's additive `RagStatus` fields.
- Produces: visible `CONSISTENT`, `MISMATCH`, or `UNKNOWN` status without combining unrelated counts.

- [ ] **Step 1: Render consistency state on the RAG status page**

Show the active collection and actual Qdrant points first. When consistency is `CONSISTENT`, show matching document/chunk build metadata. For `MISMATCH` or `UNKNOWN`, render an `el-alert` with `indexConsistencyMessage`, expected chunks, and actual points; do not label unmatched document metadata as active.

- [ ] **Step 2: Run the complete focused verification suite**

```powershell
mvn -pl ruoyi-postgrad -am -Dtest=RagIndexStatusServiceTest,RagEvaluationWorkbenchControllerTest,RagEvaluationWorkbenchMapperXmlTest -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix ruoyi-ui run build:prod
```

Expected: all focused backend tests pass and the admin production build succeeds.

- [ ] **Step 3: Start backend and admin frontend using the existing local environment**

Load the IDEA environment variables already used by the project. Start the backend on `8080` and admin frontend on `8081`; do not change committed application configuration merely to match a local Redis password mismatch.

- [ ] **Step 4: Verify authenticated APIs**

With an admin token, call:

```powershell
Invoke-RestMethod -Headers @{ Authorization = "Bearer $token" } http://localhost:8080/postgrad/rag/status
Invoke-RestMethod -Headers @{ Authorization = "Bearer $token" } "http://localhost:8080/postgrad/rag-evaluation/runs?pageNum=1&pageSize=10&status=SUCCEEDED"
```

Expected: status contains consistency fields; runs response contains at most 10 rows and a correct `total`.

- [ ] **Step 5: Complete browser acceptance**

Using the authenticated in-app browser:

1. Open `/postgrad/ops/rag` and verify counts are consistent or explicitly warned.
2. Expand navigation and use header search; inspect the console for route exceptions.
3. Open `/postgrad/ops/rag-evaluation`; filter by dataset/status/run ID and paginate.
4. Open a completed run and verify metric readability and case filters.
5. Compare compatible runs, then incompatible runs, and verify warning behavior.
6. Confirm network polling stops after all listed runs reach terminal state.

- [ ] **Step 6: Commit the status UI and acceptance record**

```powershell
git add ruoyi-ui/src/views
git commit -m "fix: surface rag index consistency in admin"
```

- [ ] **Step 7: Final diff and regression review**

```powershell
git status --short
git diff HEAD~5 --check
git log --oneline -6
```

Expected: no whitespace errors, no generated build artifacts staged, and only the scoped backend/admin files changed by this plan.
