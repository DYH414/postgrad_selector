import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()
const api = readFileSync(join(root, 'src/api/postgrad/aiObserve.js'), 'utf8')
const page = readFileSync(join(root, 'src/views/postgrad/observe/index.vue'), 'utf8')

assert.match(api, /params:\s*\{\s*window\s*\}/)
assert.match(page, /24小时/)
assert.match(page, /7天/)
assert.match(page, /30天/)
assert.match(page, /overview\.realtime/)
assert.match(page, /runLlmTraces/)
assert.match(page, /LLM 调用/)

console.log('ai observe persistence contract passed')
