import assert from 'node:assert/strict'
import {
  cleanVisibleText,
  displayCandidate,
  judgementLabel,
  sourceLabel
} from './display.js'

assert.equal(sourceLabel('conversation_ai'), '对话加入')
assert.equal(sourceLabel('background_ai'), '后台推荐')
assert.equal(sourceLabel('user_confirmed'), '用户确认')
assert.equal(sourceLabel('auto_fill_discussed'), '对话补入')

assert.equal(judgementLabel('reach'), '冲刺候选')
assert.equal(judgementLabel('steady'), '稳妥线索')
assert.equal(judgementLabel('safe'), '严格保底')

const dirty = '北京信息科技大学 ID:823 programId:823 canBeSafe=true quotaRisk=high dataCompleteness=A safeBlockReason:xx'
const cleaned = cleanVisibleText(dirty)
assert.equal(cleaned.includes('ID:823'), false)
assert.equal(cleaned.includes('programId'), false)
assert.equal(cleaned.includes('canBeSafe'), false)
assert.equal(cleaned.includes('quotaRisk'), false)
assert.equal(cleaned.includes('dataCompleteness'), false)
assert.equal(cleaned.includes('safeBlockReason'), false)

const candidate = displayCandidate({
  schoolName: '中国科学院大学 ID:823',
  programName: '计算机技术 programId:823',
  reason: '招生稳定 canBeSafe=false quotaRisk=high',
  source: 'background_ai',
  judgement: 'reach',
  finalJudgement: 'steady',
  adjusted: true,
  tags: ['双一流', 'ID:12'],
  riskTags: ['复试竞争大', 'dataCompleteness=B']
})

assert.equal(candidate.schoolName, '中国科学院大学')
assert.equal(candidate.programName, '计算机技术')
assert.equal(candidate.sourceLabel, '后台推荐')
assert.equal(candidate.judgementLabel, '稳妥线索')
assert.equal(candidate.adjustedLabel, '系统已调整档位')
assert.deepEqual(candidate.tags, ['双一流', '复试竞争大'])

console.log('ai recommend display helpers passed')
