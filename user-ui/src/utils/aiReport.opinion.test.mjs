import assert from 'node:assert/strict'
import { normalizeAiReport } from './aiReport.js'

const report = normalizeAiReport({
  summary: '推荐以稳妥为主',
  tiers: [{
    level: 'steady',
    label: '稳妥档',
    schools: [{
      programId: 123,
      schoolName: '北京信息科技大学',
      collegeName: '计算机学院',
      programName: '计算机科学与技术',
      avgAdmittedScore: 295,
      avgScoreGap: 5,
      admissionLow: 270,
      admissionHigh: 331,
      opinion: {
        judgement: 'steady',
        risk: 'medium',
        decision: '主力稳妥',
        reason: '分数匹配，地区符合偏好',
        pros: ['分数匹配度较高'],
        cons: ['数据完整度为 C，需要核验'],
        tradeoffs: ['上岸稳定性优先于学校层次'],
        recommendedAction: '加入备选并核验官网'
      }
    }]
  }]
})

const school = report.tiers[0].schools[0]
assert.equal(school.collegeName, '计算机学院')
assert.equal(school.judgement, 'steady')
assert.equal(school.decision, '主力稳妥')
assert.deepEqual(school.evidence, ['分数匹配，地区符合偏好', '分数匹配度较高'])
assert.deepEqual(school.risks, ['数据完整度为 C，需要核验'])
assert.deepEqual(school.tradeoffs, ['上岸稳定性优先于学校层次'])
assert.equal(school.recommendedAction, '加入备选并核验官网')
