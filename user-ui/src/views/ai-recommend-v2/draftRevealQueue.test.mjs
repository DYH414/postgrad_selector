import test from 'node:test'
import assert from 'node:assert/strict'
import { mergeTierRevealFrame, revealTierFrames } from './draftRevealQueue.mjs'

const tier = {
  level: 'reach',
  label: '冲刺档',
  targetCount: 3,
  candidates: [
    { fact: { programId: 1, schoolName: 'A' } },
    { fact: { programId: 2, schoolName: 'B' } },
    { fact: { programId: 3, schoolName: 'C' } }
  ],
  insufficient: false
}

test('reveals a tier one candidate at a time', () => {
  const draft = mergeTierRevealFrame(null, tier, 1)

  assert.equal(draft.tiers.length, 1)
  assert.equal(draft.tiers[0].level, 'reach')
  assert.deepEqual(
    draft.tiers[0].candidates.map(c => c.fact.programId),
    [1]
  )
  assert.equal(draft.tiers[0].insufficient, true)
})

test('replaces the same tier frame instead of duplicating it', () => {
  const first = mergeTierRevealFrame(null, tier, 1)
  const second = mergeTierRevealFrame(first, tier, 2)

  assert.equal(second.tiers.length, 1)
  assert.deepEqual(
    second.tiers[0].candidates.map(c => c.fact.programId),
    [1, 2]
  )
})

test('marks the tier complete on the final reveal frame', () => {
  const draft = mergeTierRevealFrame(null, tier, 3)

  assert.equal(draft.tiers[0].candidates.length, 3)
  assert.equal(draft.tiers[0].insufficient, false)
})

test('builds one reveal frame per candidate', () => {
  assert.deepEqual(revealTierFrames(tier), [1, 2, 3])
})
