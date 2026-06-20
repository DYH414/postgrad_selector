import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const profile = readFileSync(new URL('./Profile.vue', import.meta.url), 'utf8')

const completionBlockMatch = profile.match(/const completionPercent = computed\(\(\) => \{[\s\S]*?\n\}\)/)
assert.ok(completionBlockMatch, 'Profile.vue should define completionPercent computed value')

const completionBlock = completionBlockMatch[0]

assert.doesNotMatch(
  completionBlock,
  /targetRegions\s*&&\s*profile\.targetRegions\.length/,
  '不限地区 is a valid profile choice, so completion should not require selected targetRegions'
)
assert.match(completionBlock, /!!profile\.estimatedScore/)
assert.match(completionBlock, /!!profile\.examCombo/)
assert.match(completionBlock, /!!profile\.schoolTierPreference/)
