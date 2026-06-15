import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import assert from 'node:assert/strict'

const currentDir = dirname(fileURLToPath(import.meta.url))
const root = resolve(currentDir, '..')

const review = readFileSync(resolve(root, 'src/views/postgrad/review/index.vue'), 'utf8')
const workspace = readFileSync(resolve(root, 'src/views/postgrad/workspace/index.vue'), 'utf8')

assert.match(workspace, /jumpReview\(\)/)
assert.match(workspace, /path:\s*'\/postgrad\/review'/)
assert.match(workspace, /schoolName:/)
assert.match(workspace, /programCode:/)
assert.match(workspace, /status:\s*'pending'/)

assert.match(review, /applyRouteQuery\(\)/)
assert.match(review, /\$route\.query/)
assert.match(review, /'\$route\.query'/)
assert.match(review, /schoolName/)
assert.match(review, /programCode/)
assert.match(review, /matchStatus/)
assert.match(review, /is408/)

console.log('review query link checks passed')
