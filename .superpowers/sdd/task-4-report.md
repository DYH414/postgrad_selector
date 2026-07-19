# Task 4 Report: RAG Evaluation Run Management

## Status

Completed the run-history controls in `ruoyi-ui/src/views/postgrad/rag-evaluation/index.vue`.

## Delivered

- Added `runQuery`, `runTotal`, and `poller` state.
- Added dataset, status, and Run ID filters with query and reset actions.
- Replaced the fixed 100-row run request with the paged `rows` and `total` response.
- Added standard RuoYi pagination and created/finished time columns.
- Reset to page 1 for filter changes and new run submission; pagination keeps active filters.
- Started polling only when the visible page contains `PENDING` or `RUNNING` rows; polling skips an in-flight request and stops when all visible rows are terminal or the view is destroyed.

## Validation

Executed successfully:

```powershell
$env:NODE_OPTIONS='--max-old-space-size=1024'; npm --prefix ruoyi-ui run build:prod
```

The build completed with exit code 0. Webpack emitted pre-existing asset-size warnings only.

## Scope

No Task 5 redesign was implemented. The API module requires no Task 4 change because it already forwards the query object unchanged.

## Review Fixes

- Added `runQueryVersion`, `runLoadInFlight`, and `runReloadQueued`. Every load intent advances the version; an in-flight request records exactly one queued follow-up load. A stale response cannot update `runRows`, `runTotal`, or polling state, and the follow-up reads the newest filter and paging state.
- Added `isDestroyed` and `stopRunPolling()`. `beforeDestroy` marks the component as destroyed and clears the timer. In-flight request completion exits without changing component state or creating a new timer after teardown.
- `fetchRuns` catches request rejections without adding a duplicate UI message. It preserves the last successful rows and total, stops the poller, and prevents rejected polling promises from surfacing every three seconds.
- Kept the existing comparison UI unchanged. It predates Task 4 and remains part of the preserved workbench behavior.

## Review Validation

Executed an inline component-method regression check against the page source. It verified that multiple requests during one in-flight load produce exactly one latest-query follow-up; stale results do not replace the last successful rows; rejected requests preserve those rows and stop polling; and a destroyed component ignores late completion. Result:

```text
loadRuns concurrency, failure, and teardown checks passed
```

Executed successfully:

```powershell
$env:NODE_OPTIONS='--max-old-space-size=1024'; npm --prefix ruoyi-ui run build:prod
```

Result: exit code 0 in 58.7 seconds. Webpack compiled with two existing size-limit warning groups: asset-size limits and entrypoint-size limits. No compilation errors occurred.
