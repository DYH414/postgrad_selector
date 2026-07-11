import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()
const api = readFileSync(join(root, 'src/api/postgrad/rag.js'), 'utf8')
const page = readFileSync(join(root, 'src/views/postgrad/rag/index.vue'), 'utf8')

assert.match(api, /startIndexBuild/, 'RAG API must expose asynchronous build creation')
assert.match(api, /getIndexRun/, 'RAG API must expose build status polling')
assert.doesNotMatch(api, /reindexDatabaseRag|generateDatabaseDocuments/, 'Removed synchronous rebuild APIs must not return')

assert.match(page, /重建完整知识库/, 'RAG page must expose one unambiguous full rebuild action')
assert.match(page, /resumeBuildPolling/, 'RAG page must restore polling after refresh')
assert.match(page, /finishBuild/, 'RAG page must centralize terminal-state cleanup')
assert.match(page, /pollFailures/, 'RAG page must surface repeated polling failures')
assert.match(page, /this\.building = true[\s\S]*await startIndexBuild/, 'Build button must lock before the POST resolves')
assert.match(page, /loadLatestRun[\s\S]*resumeBuildPolling/, 'Latest non-terminal run must resume polling')
assert.match(page, /stopPolling\(\)[\s\S]*clearInterval/, 'Polling cleanup must clear the active timer')
assert.match(page, /startYear/, 'RAG page must expose start year parameter')
assert.match(page, /endYear/, 'RAG page must expose end year parameter')
assert.match(page, /programId/, 'RAG page must expose program metadata filter')
assert.match(page, /schoolId/, 'RAG page must expose school metadata filter')
assert.match(page, /docType/, 'RAG page must expose document type metadata filter')
assert.match(page, /section/, 'RAG page must expose section metadata filter')
assert.match(page, /embeddingLatencyMs/, 'Trace UI must expose embedding latency when present')
assert.match(page, /retrievalLatencyMs/, 'Trace UI must expose retrieval latency when present')
assert.match(page, /rerankLatencyMs/, 'Trace UI must expose rerank latency when present')
