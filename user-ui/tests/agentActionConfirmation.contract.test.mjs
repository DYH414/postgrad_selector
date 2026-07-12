import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()
const api = readFileSync(join(root, 'src/api/recommend-v2.js'), 'utf8')
const workspace = readFileSync(join(root, 'src/views/ai-recommend-v2/AiRecommendV2Workspace.vue'), 'utf8')

assert.match(api, /chat\/action\/confirm/)
assert.match(api, /chat\/action\/cancel/)
assert.match(workspace, /confirmationRequired/)
assert.match(workspace, /confirmationToken/)
assert.match(workspace, /confirmAgentAction/)
assert.match(workspace, /cancelAgentAction/)
assert.match(workspace, /confirmation_required/)
assert.doesNotMatch(workspace, /programId.*确认草稿调整/)

console.log('agent action confirmation contract passed')
