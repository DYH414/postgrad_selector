import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const api = readFileSync(join(currentDir, '../api/recommend-v2.js'), 'utf8')
const workspace = readFileSync(join(currentDir, 'ai-recommend-v2/AiRecommendV2Workspace.vue'), 'utf8')

assert.match(api, /startGenerateDraft/)
assert.match(api, /openDraftGenerationStream/)
assert.doesNotMatch(api, /fetch\('\/dev-api\/app\/ai-recommend-v2\/draft\/generate'/)
assert.match(workspace, /openDraftGenerationStream/)
assert.match(workspace, /draftEventSource/)
assert.match(workspace, /onBeforeUnmount/)

console.log('AiRecommend v2 streaming checks passed')
