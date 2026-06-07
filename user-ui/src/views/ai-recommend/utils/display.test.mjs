import assert from 'node:assert/strict'
import {
  cleanVisibleText,
  displayCandidate,
  extractMetrics,
  extractReason,
  firstNonEmpty,
  formatSource,
  formatJudgement,
  formatDataCompleteness,
  resolveSchoolName,
  resolveProgramName,
  judgementLabel,
  sourceLabel
} from './display.js'

// ── firstNonEmpty ──
assert.equal(firstNonEmpty('', 'hello', 'world'), 'hello')
assert.equal(firstNonEmpty(null, undefined, '', '  ', 'found'), 'found')
assert.equal(firstNonEmpty(), '')
assert.equal(firstNonEmpty(null, undefined, ''), '')

// ── sourceLabel / formatSource ──
assert.equal(sourceLabel('conversation_ai'), '对话加入')
assert.equal(formatSource('conversation_ai'), '对话加入')
assert.equal(sourceLabel('background_ai'), '后台推荐')
assert.equal(formatSource('background_ai'), '后台推荐')
assert.equal(sourceLabel('user_confirmed'), '用户确认')
assert.equal(formatSource('user_confirmed'), '用户确认')
assert.equal(sourceLabel('auto_fill_discussed'), '对话补入')
assert.equal(formatSource('auto_fill_discussed'), '对话补入')
assert.equal(formatSource('auto_fill_search'), '系统兜底')

// ── judgementLabel / formatJudgement ──
assert.equal(judgementLabel('reach'), '冲刺候选')
assert.equal(formatJudgement('reach'), '冲刺候选')
assert.equal(judgementLabel('steady'), '稳妥线索')
assert.equal(formatJudgement('steady'), '稳妥线索')
assert.equal(judgementLabel('safe'), '严格保底')
assert.equal(formatJudgement('safe'), '严格保底')
assert.equal(formatJudgement('unknown'), '推荐候选')

// ── formatDataCompleteness ──
assert.equal(formatDataCompleteness('A'), '数据 A')
assert.equal(formatDataCompleteness('B'), '数据 B')
assert.equal(formatDataCompleteness(''), null)
assert.equal(formatDataCompleteness(null), null)

// ── cleanVisibleText ──
const dirty = '北京信息科技大学 ID:823 programId:823 canBeSafe=true quotaRisk=high dataCompleteness=A safeBlockReason:xx'
const cleaned = cleanVisibleText(dirty)
assert.equal(cleaned.includes('ID:823'), false)
assert.equal(cleaned.includes('programId'), false)
assert.equal(cleaned.includes('canBeSafe'), false)
assert.equal(cleaned.includes('quotaRisk'), false)
assert.equal(cleaned.includes('dataCompleteness'), false)
assert.equal(cleaned.includes('safeBlockReason'), false)

// ── resolveSchoolName 多路径回退 ──
assert.equal(resolveSchoolName({ schoolName: '北京信息科技大学' }), '北京信息科技大学')
assert.equal(resolveSchoolName({ name: '清华大学' }), '清华大学')
assert.equal(resolveSchoolName({ school: { schoolName: '北大' } }), '北大')
assert.equal(resolveSchoolName({ school: { name: '复旦' } }), '复旦')
assert.equal(resolveSchoolName({ program: { schoolName: '浙大' } }), '浙大')
assert.equal(resolveSchoolName({ programInfo: { schoolName: '南大' } }), '南大')
assert.equal(resolveSchoolName({ item: { schoolName: '上海交大' } }), '上海交大')
assert.equal(resolveSchoolName({}), '未知学校')
assert.equal(resolveSchoolName(null), '未知学校')

// ── resolveProgramName 多路径回退 ──
assert.equal(resolveProgramName({ programName: '计算机科学与技术' }), '计算机科学与技术')
assert.equal(resolveProgramName({ majorName: '软件工程' }), '软件工程')
assert.equal(resolveProgramName({ directionName: '人工智能' }), '人工智能')
assert.equal(resolveProgramName({ school: { programName: '电子信息' } }), '电子信息')
assert.equal(resolveProgramName({ program: { programName: '网络空间安全' } }), '网络空间安全')
assert.equal(resolveProgramName({ programInfo: { programName: '大数据' } }), '大数据')
assert.equal(resolveProgramName({ item: { programName: '物联网' } }), '物联网')
assert.equal(resolveProgramName({}), '专业方向待确认')

// ── extractMetrics 结构化字段优先 ──
const m1 = extractMetrics({
  gap: 6,
  planCount: 97,
  dataCompleteness: 'A'
})
assert.equal(m1.gap, 6)
assert.equal(m1.quota, 97)
assert.equal(m1.gapText, 'gap +6')
assert.equal(m1.quotaText, '招生 97')
assert.equal(m1.completenessText, '数据 A')

// 结构化字段 scoreGap / enrollment / completenessLevel
const m2 = extractMetrics({
  scoreGap: -10,
  enrollment: 2,
  completenessLevel: 'B'
})
assert.equal(m2.gap, -10)
assert.equal(m2.quota, 2)
assert.equal(m2.gapText, 'gap -10')
assert.equal(m2.quotaText, '招生 2')
assert.equal(m2.completenessText, '数据 B')

// 文本回退
const m3 = extractMetrics({
  pros: ['gap+15，分数充裕', '招生30，名额充裕'],
  reason: '录取均分320'
})
assert.equal(m3.gap, 15)
assert.equal(m3.quota, 30)

// 无 gap 时 gapText 为 null
const m4 = extractMetrics({})
assert.equal(m4.gap, null)
assert.equal(m4.gapText, null)
assert.equal(m4.quotaText, null)
assert.equal(m4.completenessText, null)

// ── extractReason ──
assert.ok(extractReason({ reason: '推荐理由' }).includes('推荐理由'))
assert.ok(extractReason({ pros: ['优势1'], cons: ['风险1'] }).length > 0)
assert.equal(extractReason({}), '等待 AI 补充推荐理由')

// ── displayCandidate 完整流程 ──
const candidate = displayCandidate({
  schoolName: '中国科学院大学',
  programName: '计算机技术',
  reason: '招生稳定，分数充裕',
  source: 'background_ai',
  judgement: 'reach',
  finalJudgement: 'steady',
  adjusted: true,
  adjustReason: 'gap<15，降级为稳妥线索',
  gap: 15,
  planCount: 30,
  dataCompleteness: 'A',
  pros: ['gap+15，分数充裕'],
  cons: [],
  tags: ['双一流'],
  riskTags: ['复试竞争大']
})

assert.equal(candidate.schoolName, '中国科学院大学')
assert.equal(candidate.programName, '计算机技术')
assert.equal(candidate.sourceLabel, '后台推荐')
assert.equal(candidate.judgementLabel, '稳妥线索')
assert.equal(candidate.adjusted, true)
assert.equal(candidate.adjustedLabel, '系统已调整档位')
assert.deepEqual(candidate.tags, ['双一流', '复试竞争大'])
assert.equal(candidate.metrics.gap, 15)
assert.equal(candidate.metrics.quota, 30)
assert.equal(candidate.metrics.gapText, 'gap +15')
assert.equal(candidate.metrics.quotaText, '招生 30')
assert.equal(candidate.metrics.completenessText, '数据 A')
assert.equal(candidate.statusLabel, '待确认')
assert.ok(candidate.reason.length > 0)

// 无 schoolName 时显示"未知学校"
const c2 = displayCandidate({})
assert.equal(c2.schoolName, '未知学校')
assert.equal(c2.programName, '专业方向待确认')
assert.equal(c2.metrics.gapText, null)

// programId 等后端字段不泄漏到展示文本
const c3 = displayCandidate({
  schoolName: '北京信息科技大学 ID:823',
  programName: '计算机科学与技术 programId:823',
  source: 'conversation_ai',
  pros: ['gap+6，分数有余量 canBeSafe=true'],
  cons: ['quotaRisk=high dataCompleteness=B']
})
assert.equal(c3.schoolName, '北京信息科技大学')  // ID:823 stripped
assert.equal(c3.programName, '计算机科学与技术')  // programId:823 stripped
assert.equal(c3.metrics.gap, 6)
assert.equal(c3.pros.some(p => p.includes('canBeSafe')), false)
assert.equal(c3.cons.some(c => c.includes('quotaRisk')), false)

console.log('ai recommend display helpers — all passed')
