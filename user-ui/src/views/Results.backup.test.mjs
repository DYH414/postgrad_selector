import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import assert from 'node:assert/strict'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'Results.vue'), 'utf8')

assert.match(source, /备选池/)
assert.match(source, /backup-overview/)
assert.match(source, /backup-item-card/)
assert.match(source, /backup-item-meta/)
assert.match(source, /加入备选/)
assert.doesNotMatch(source, /我的收藏/)
assert.doesNotMatch(source, /已收藏/)
assert.doesNotMatch(source, /收藏意向专业/)
assert.doesNotMatch(source, /取消收藏/)
assert.doesNotMatch(source, /加入收藏/)

console.log('Results backup UI checks passed')
