import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const profile = readFileSync(new URL('./Profile.vue', import.meta.url), 'utf8')
const recommend = readFileSync(new URL('./AiRecommend.vue', import.meta.url), 'utf8')

for (const field of [
  'riskPreference',
  'schoolTierPreference',
  'regionStrategy'
]) {
  assert.match(profile, new RegExp(`form\\.${field}`), `${field} should be editable in profile form`)
  assert.match(profile, new RegExp(`profile\\.${field}`), `${field} should be loaded into profile state`)
}

assert.match(profile, /择校偏好/)
assert.match(profile, /整体择校策略/)
assert.match(profile, /院校层次取舍/)
assert.match(profile, /地区取舍/)
assert.doesNotMatch(profile, /更看重/)
assert.doesNotMatch(profile, /数据可靠性/)
assert.match(recommend, /整体策略/)
assert.match(recommend, /院校层次取舍/)
assert.match(recommend, /地区取舍/)
