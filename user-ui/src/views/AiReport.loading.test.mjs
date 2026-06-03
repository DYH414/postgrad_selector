import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import assert from 'node:assert/strict'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'AiReport.vue'), 'utf8')

assert.match(source, /AI 择校研判台/)
assert.match(source, /正在生成冲稳保择校方案/)
assert.match(source, /历年复试线/)
assert.match(source, /院校画像/)
assert.match(source, /fit-preview-card/)
assert.match(source, /MIN_PENDING_LOADING_MS = 9000/)
assert.match(source, /TYPE_CHAR_DELAY_MS = 45/)
assert.match(source, /scheduleReportCompletion/)
assert.doesNotMatch(source, /408-RECRUIT-ENGINE/)
assert.doesNotMatch(source, /terminal-panel/)

console.log('AiReport loading theme checks passed')
