import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import assert from 'node:assert/strict'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'Profile.vue'), 'utf8')

assert.match(source, /备选池/)
assert.match(source, /shortlist-overview/)
assert.match(source, /shortlist-card/)
assert.match(source, /shortlistSelectedIds/)
assert.match(source, /移出备选/)
assert.match(source, /加入备选后/)
assert.doesNotMatch(source, /取消收藏/)
assert.doesNotMatch(source, /集中管理收藏/)
assert.doesNotMatch(source, /收藏学校/)

console.log('Profile shortlist UI checks passed')
