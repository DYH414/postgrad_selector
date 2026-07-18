# RAG Evaluation Admin Usability Design

## Background

The RAG evaluation workbench is functionally connected, but the current admin experience exposes several contract and presentation problems:

- The RAG status response combines the document count from a MySQL build record with the point count from the active Qdrant collection without proving they represent the same index version. This can display values such as 3,299 documents and only 5 active points as if they were consistent.
- Dynamic menu data can contain missing or non-string paths. `SidebarItem` and `HeaderSearch` pass these values directly to path helpers and produce runtime exceptions.
- The experiment list loads up to 100 historical runs in one request and offers no server-side paging or practical filters.
- Evaluation metrics are primarily shown as raw JSON, which makes routine analysis slow and error-prone.
- Comparison selectors use full run UUIDs, so administrators cannot identify meaningful runs quickly.

## Goals

1. Make index status truthful and explicitly surface consistency problems.
2. Prevent malformed route data from breaking sidebar and header search rendering.
3. Make repeated evaluation runs manageable through paging and filters.
4. Present evaluation metrics as readable operational information while retaining raw data for debugging.
5. Make run comparison understandable and prevent misleading comparisons.

## Non-goals

- Redesigning the entire RAG evaluation architecture.
- Changing retrieval, rerank, or metric formulas.
- Replacing the existing admin design system.
- Automatically deleting historical runs or Qdrant collections.
- Adding a new evaluation framework when the existing backend contracts can be extended safely.

## 1. Index Status Consistency

### Current problem

`RagIndexStatusService` resolves the active Qdrant collection, reads the active MySQL index run, and combines fields from both sources. It does not verify that the MySQL run's candidate collection equals the collection currently resolved by the alias. Therefore, values from two different index versions can appear in one status response.

### Design

The service will treat Qdrant as the source of truth for the currently searchable collection and its realtime point count.

The active MySQL build record may provide build metadata only when:

- its candidate collection equals the collection resolved from the active alias; and
- the build status represents a successfully activated version.

The status contract will expose:

- `activeCollection`: collection currently resolved from the alias;
- `expectedDocumentCount`: document count recorded by the matching successful build;
- `expectedChunkCount`: chunk count recorded by that build;
- `actualPointCount`: realtime Qdrant point count;
- `indexConsistency`: `CONSISTENT`, `MISMATCH`, or `UNKNOWN`;
- `indexConsistencyMessage`: short diagnostic message suitable for the admin UI.

Consistency rules:

- `CONSISTENT`: a matching successful build exists and `expectedChunkCount == actualPointCount`.
- `MISMATCH`: a matching build exists but the expected and actual counts differ.
- `UNKNOWN`: no matching build metadata exists, the alias cannot be resolved, or the realtime point count cannot be read.

The UI must not present an unmatched MySQL document count as the document count of the active collection. A mismatch is displayed as an operational warning with the expected and actual counts.

## 2. Defensive Dynamic Routing

### Current problem

`SidebarItem.resolvePath` and `HeaderSearch.generateRoutes` assume every route path is a non-empty string. Invalid dynamic menu records can therefore crash navigation rendering.

### Design

Before calling `isExternal`, `isHttp`, `path.resolve`, or equivalent helpers:

- verify that the route path is a string;
- trim it and reject an empty value;
- skip invalid route/search entries instead of manufacturing a route;
- log a concise development warning containing the route name or menu identifier when available.

The backend/menu SQL should still be corrected if a malformed record is found, but frontend navigation must remain resilient because menu data is external input.

## 3. Evaluation Run Management

### API behavior

The existing run list endpoint will support server-side paging and filtering through the established RuoYi page contract:

- `pageNum`, default `1`;
- `pageSize`, default `10`;
- `datasetId` or dataset version/checksum filter;
- `status` filter;
- optional run ID keyword filter.

The response continues to return `rows` and `total` so the change remains compatible with existing admin conventions.

### UI behavior

The run list will provide:

- 10 rows per page by default;
- dataset and status selectors;
- run ID keyword search;
- creation/completion time;
- explicit empty, loading, and failure states;
- polling only while at least one visible or newly created run is `PENDING` or `RUNNING`.

Changing filters resets the page to 1. A refresh preserves filters and the current page when valid.

## 4. Evaluation Result Presentation

### Summary metrics

The result view will render named metric cards or compact tables for:

- MRR;
- Hit@1, Hit@3, Hit@5;
- nDCG@5;
- Pass Rate;
- Filter Accuracy;
- No-answer Accuracy;
- False Positive Rate;
- P50/P95 latency when present.

Percentages and bounded metrics are formatted consistently. Missing metrics display `--`, not zero.

### Retrieval mode breakdown

When both modes are available, show `Vector Only` and `Vector + Rerank` side by side. Improvements, ties, and regressions must be derived only from compatible metrics in the same run or dataset version.

### Case details

The case list will include:

- query or case summary;
- expected and actual route/filter information when available;
- retrieval mode filter;
- translated failure reason labels;
- rank and matched source information;
- a details action for complete diagnostic data.

Raw JSON remains available in a collapsed diagnostic section. It is not the primary result presentation.

## 5. Run Comparison

### Selection labels

Each option will use a readable label:

`completed time | dataset/version | mode | MRR | short run ID`

Only completed runs are selectable. Full run IDs remain visible in detail views and copy actions.

### Compatibility rules

Comparisons prioritize runs with the same dataset checksum and compatible metric definitions. The latest two compatible completed runs are selected by default when available.

If selected runs are incompatible:

- show a clear warning explaining the mismatch;
- display raw values when useful;
- do not calculate or emphasize percentage improvement as if the experiment conditions were equivalent.

## 6. Error Handling and Observability

- API failures show actionable messages and retain the last successful view when possible.
- Status consistency warnings are visible in the status area and included in backend logs without leaking credentials or payload content.
- Frontend route guards skip malformed entries and emit development diagnostics without interrupting the rest of the page.
- Existing run IDs remain the correlation key for evaluation details and related trace information.

## 7. Implementation Scope

### Backend

- RAG status DTO and `RagIndexStatusService` consistency resolution.
- Evaluation run query DTO/controller/service/mapper changes required for paging and filters.
- Unit or integration tests for status matching, mismatching, and unknown states.
- API tests for paging and filter combinations.

### Admin frontend

- RAG status warning and count presentation.
- Evaluation workbench run pagination and filters.
- Structured result and comparison presentation.
- Defensive handling in `SidebarItem` and `HeaderSearch`.

### Data scripts

Menu SQL is changed only if a concrete malformed RAG evaluation menu record is confirmed. Frontend resilience is implemented regardless.

## 8. Verification

### Automated verification

1. A matching active build and Qdrant point count returns `CONSISTENT`.
2. A matching active build with different counts returns `MISMATCH` and both expected and actual values.
3. An alias without matching build metadata returns `UNKNOWN` and does not reuse another run's document count.
4. Invalid route paths do not throw from sidebar or header search generation.
5. Run paging returns correct `rows` and `total` for multiple pages.
6. Dataset, status, and run ID filters can be combined.
7. Result formatting distinguishes missing values from numeric zero.
8. Incompatible comparison runs do not show a calculated improvement percentage.

### Browser acceptance

1. Open `/postgrad/ops/rag` and confirm active collection counts are internally consistent or visibly warned.
2. Navigate and use header search without console exceptions from malformed route paths.
3. Open `/postgrad/ops/rag-evaluation`, filter and paginate historical runs, then refresh without losing the selected conditions.
4. Open a completed result and understand core metrics without reading JSON.
5. Compare two compatible runs using readable labels and verify differences match the underlying values.
6. Select incompatible runs and verify the UI prevents a misleading conclusion.

## Risks and Mitigations

- **Historical build metadata may be incomplete.** Use `UNKNOWN` rather than guessing and keep Qdrant realtime counts visible.
- **Metric payloads may vary between older runs.** Normalize defensively in the UI and preserve raw JSON diagnostics.
- **Polling can create unnecessary load.** Poll only for active runs and stop after terminal status or page exit.
- **Route skipping can hide a bad menu record.** Emit a development warning and separately correct confirmed data defects.

