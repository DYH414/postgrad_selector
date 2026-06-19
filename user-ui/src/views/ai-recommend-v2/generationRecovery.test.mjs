import assert from 'node:assert/strict'
import { isDraftGenerating, getRestoredGenerationMessage } from './generationRecovery.mjs'

// 1. Generating placeholder detected
{
  const draft = {
    tiers: [
      { level: 'reach', insufficient: true, insufficientReason: 'AI 正在冲刺档挑选合适的学校...' },
      { level: 'steady', insufficient: true, insufficientReason: 'AI 正在稳妥档挑选合适的学校...' },
      { level: 'safe', insufficient: true, insufficientReason: 'AI 正在保底档挑选合适的学校...' }
    ]
  }
  assert.strictEqual(isDraftGenerating(draft), true)
}

// 2. Partial draft (one tier done, others still generating)
{
  const draft = {
    tiers: [
      { level: 'reach', insufficient: false, candidates: [{}] },
      { level: 'steady', insufficient: true, insufficientReason: 'AI 正在稳妥档挑选合适的学校...' },
      { level: 'safe', insufficient: true, insufficientReason: 'AI 正在保底档挑选合适的学校...' }
    ]
  }
  assert.strictEqual(isDraftGenerating(draft), true)
}

// 3. Complete draft (all tiers done) — NOT generating
{
  const draft = {
    tiers: [
      { level: 'reach', insufficient: false, candidates: [{}, {}, {}] },
      { level: 'steady', insufficient: false, candidates: [{}, {}, {}, {}] },
      { level: 'safe', insufficient: false, candidates: [{}, {}, {}] }
    ]
  }
  assert.strictEqual(isDraftGenerating(draft), false)
}

// 4. Empty draft
{
  assert.strictEqual(isDraftGenerating(null), false)
  assert.strictEqual(isDraftGenerating({}), false)
  assert.strictEqual(isDraftGenerating({ tiers: null }), false)
  assert.strictEqual(isDraftGenerating({ tiers: [] }), false)
}

// 5. Failed placeholder (insufficient but no "正在") — NOT generating
{
  const draft = {
    tiers: [
      { level: 'reach', insufficient: true, insufficientReason: '草稿生成失败，请重新生成' },
      { level: 'steady', insufficient: true, insufficientReason: '草稿生成失败，请重新生成' },
      { level: 'safe', insufficient: true, insufficientReason: '草稿生成失败，请重新生成' }
    ]
  }
  assert.strictEqual(isDraftGenerating(draft), false)
}

// 6. Recovery message shape
{
  const msg = getRestoredGenerationMessage()
  assert.strictEqual(msg.role, 'assistant')
  assert.strictEqual(msg.messageType, 'generation_status')
  assert.ok(msg.content.includes('筛选学校'))
}

console.log('generationRecovery tests passed')
