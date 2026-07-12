import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()
const api = readFileSync(join(root, 'src/api/postgrad/aiObserve.js'), 'utf8')
const page = readFileSync(join(root, 'src/views/postgrad/observe/index.vue'), 'utf8')

assert.match(api, /tool-traces/)
assert.match(api, /runs\/\$\{runId\}\/tool-traces/)
assert.match(page, /el-table/)
assert.match(page, /runId/)
assert.match(page, /resultCode/)
assert.match(page, /durationMs/)
assert.match(page, /el-drawer/)

console.log('ai tool trace contract passed')
