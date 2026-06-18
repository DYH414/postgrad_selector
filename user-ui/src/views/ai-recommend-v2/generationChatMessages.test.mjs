import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getGenerationStartMessage,
  getGenerationProgressMessage,
  getGenerationDoneMessage
} from './generationChatMessages.mjs'

test('creates a start status message for draft generation', () => {
  const message = getGenerationStartMessage()

  assert.equal(message.role, 'assistant')
  assert.equal(message.messageType, 'generation_status')
  assert.equal(message.metadataJson.key, 'start')
  assert.match(message.content, /生成候选草稿/)
})

test('maps tier data events to one stable message key per tier', () => {
  const message = getGenerationProgressMessage({
    tierData: {
      level: 'steady',
      label: '稳妥档',
      candidates: [{}, {}]
    }
  })

  assert.equal(message.metadataJson.key, 'tier-ready-steady')
  assert.match(message.content, /稳妥档/)
})

test('ignores unknown progress phases', () => {
  assert.equal(getGenerationProgressMessage({ phase: 'unknown' }), null)
})

test('summarizes the final draft count', () => {
  const message = getGenerationDoneMessage({
    tiers: [
      { candidates: [{}, {}] },
      { candidates: [{}] }
    ]
  })

  assert.equal(message.metadataJson.key, 'done')
  assert.match(message.content, /共 3 所学校/)
})
