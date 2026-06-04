import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const profile = readFileSync(new URL('./Profile.vue', import.meta.url), 'utf8')
const recommend = readFileSync(new URL('./AiRecommend.vue', import.meta.url), 'utf8')

for (const field of [
  'priorityPreference',
  'schoolTierPreference',
  'regionStrategy',
  'dataReliabilityPreference'
]) {
  assert.match(profile, new RegExp(`form\\.${field}`), `${field} should be editable in profile form`)
  assert.match(profile, new RegExp(`profile\\.${field}`), `${field} should be loaded into profile state`)
}

assert.match(profile, /择校偏好/)
assert.match(profile, /更看重/)
assert.match(profile, /学校层次倾向/)
assert.match(profile, /地区策略/)
assert.match(profile, /数据可靠性/)
assert.match(recommend, /择校偏好/)
