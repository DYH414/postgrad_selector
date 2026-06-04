import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import assert from 'node:assert/strict'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'AiReport.vue'), 'utf8')

assert.match(source, /school-seal/)
assert.match(source, /school-meta-line/)
assert.match(source, /score-line/)
assert.match(source, /score-metric/)
assert.match(source, /拟录取区间/)
assert.match(source, /最低录取分/)
assert.match(source, /dataCompletenessText/)
assert.match(source, /formatAdmissionQuota/)
assert.match(source, /source-missing/)
assert.doesNotMatch(source, /quick-stats/)

console.log('AiReport result-card parity checks passed')
