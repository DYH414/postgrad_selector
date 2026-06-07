const SOURCE_LABELS = {
  conversation_ai: '对话加入',
  background_ai: '后台推荐',
  user_confirmed: '用户确认',
  auto_fill_discussed: '对话补入',
  auto_fill_search: '系统兜底'
}

const JUDGEMENT_LABELS = {
  reach: '冲刺候选',
  steady: '稳妥线索',
  safe: '严格保底'
}

const INTERNAL_FIELD_RE = /\b(?:programId|canBeSafe|quotaRisk|dataCompleteness|safeBlockReason|aiJudgement|finalJudgement)\b\s*[:：=]?\s*[\w一-龥.-]*/gi
const ID_RE = /\bID\s*[:：]?\s*\d+\b/gi

// ── 基础工具 ──

export function firstNonEmpty(...values) {
  for (const v of values) {
    if (v !== undefined && v !== null && String(v).trim() !== '') return String(v).trim()
  }
  return ''
}

function safeText(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

export function cleanVisibleText(value) {
  if (value == null) return ''
  return String(value)
    .replace(ID_RE, '')
    .replace(INTERNAL_FIELD_RE, '')
    .replace(/\s{2,}/g, ' ')
    .replace(/[，,、；;：:\s]+$/g, '')
    .trim()
}

// ── 标签映射 ──

export function sourceLabel(source) {
  return SOURCE_LABELS[source] || cleanVisibleText(source) || '来源待确认'
}

export function formatSource(source) {
  return SOURCE_LABELS[source] || cleanVisibleText(source) || '系统推荐'
}

export function judgementLabel(value) {
  return JUDGEMENT_LABELS[value] || cleanVisibleText(value) || '待判断'
}

export function formatJudgement(judgement) {
  return JUDGEMENT_LABELS[judgement] || '推荐候选'
}

export function formatDataCompleteness(level) {
  if (!level) return null
  const s = String(level).trim().toUpperCase()
  if (/^[A-E]$/.test(s)) return `数据 ${s}`
  if (/^数据\s?[A-E]$/i.test(s)) return s.replace(/\s/g, '')
  return null
}

// ── 字段解析（多路径回退）──

/**
 * 学校名：按优先级尝试多个可能的字段路径
 */
export function resolveSchoolName(bookmark = {}) {
  if (!bookmark || typeof bookmark !== 'object') return '未知学校'

  const school = bookmark.school
  const program = bookmark.program
  const programInfo = bookmark.programInfo
  const item = bookmark.item

  const raw = firstNonEmpty(
    bookmark.schoolName,
    bookmark.name,
    school && school.schoolName,
    school && school.name,
    program && program.schoolName,
    programInfo && programInfo.schoolName,
    item && item.schoolName
  )

  if (!raw) return '未知学校'
  return cleanVisibleText(raw) || '未知学校'
}

/**
 * 专业方向：按优先级尝试多个可能的字段路径
 */
export function resolveProgramName(bookmark = {}) {
  if (!bookmark || typeof bookmark !== 'object') return '专业方向待确认'

  const school = bookmark.school
  const program = bookmark.program
  const programInfo = bookmark.programInfo
  const item = bookmark.item

  const raw = firstNonEmpty(
    bookmark.programName,
    bookmark.majorName,
    bookmark.directionName,
    school && school.programName,
    program && program.programName,
    programInfo && programInfo.programName,
    item && item.programName
  )

  if (!raw) return '专业方向待确认'
  return cleanVisibleText(raw) || '专业方向待确认'
}

// ── 指标提取（结构化字段优先，文本解析回退）──

const GAP_STRUCT_FIELDS = ['gap', 'scoreGap']
const QUOTA_STRUCT_FIELDS = ['quota', 'planCount', 'enrollment', 'unifiedExamQuota']
const COMPLETENESS_STRUCT_FIELDS = ['completenessLevel', 'dataCompleteness', 'completeness']

function resolveStructNumber(bookmark, fieldNames) {
  for (const name of fieldNames) {
    const v = bookmark[name]
    if (v === undefined || v === null) continue
    const n = Number(v)
    if (Number.isFinite(n)) return n
  }
  return null
}

function resolveStructString(bookmark, fieldNames) {
  for (const name of fieldNames) {
    const v = bookmark[name]
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      return String(v).trim()
    }
  }
  return null
}

// 文本解析回退
function findInTexts(bookmark, regex) {
  const sources = []
  if (Array.isArray(bookmark.pros)) sources.push(...bookmark.pros)
  if (Array.isArray(bookmark.cons)) sources.push(...bookmark.cons)
  if (bookmark.reason) sources.push(bookmark.reason)
  for (const src of sources) {
    const match = String(src).match(regex)
    if (match) return match
  }
  return null
}

function parseGapFromTexts(bookmark) {
  const match = findInTexts(bookmark, /gap\s*([+-]?\d+)/)
  return match ? parseInt(match[1], 10) : null
}

function parseQuotaFromTexts(bookmark) {
  const match = findInTexts(bookmark, /(?:招生|名额|统考名额)\s*(\d+)/)
  return match ? parseInt(match[1], 10) : null
}

function parseCompletenessFromTexts(bookmark) {
  const match = findInTexts(bookmark, /数据完整度\s*([A-Ea-e])|completeness[=:：]?\s*([A-Ea-e])/i)
  if (match) {
    const level = (match[1] || match[2]).toUpperCase()
    return `数据 ${level}`
  }
  return null
}

export function extractMetrics(bookmark = {}) {
  // 结构化字段优先
  const gap = resolveStructNumber(bookmark, GAP_STRUCT_FIELDS)
    ?? parseGapFromTexts(bookmark)

  const quota = resolveStructNumber(bookmark, QUOTA_STRUCT_FIELDS)
    ?? parseQuotaFromTexts(bookmark)

  const completeness = formatDataCompleteness(
    resolveStructString(bookmark, COMPLETENESS_STRUCT_FIELDS)
  ) || parseCompletenessFromTexts(bookmark)

  return {
    gap,
    quota,
    completeness,
    // 只有值存在时才产生展示文本，无值返回 null 以便模板 v-if 控制
    gapText: gap != null ? (gap >= 0 ? `gap +${gap}` : `gap ${gap}`) : null,
    quotaText: quota != null ? `招生 ${quota}` : null,
    completenessText: completeness || null
  }
}

// ── 推荐理由 ──

export function extractReason(bookmark = {}) {
  if (bookmark.reason && bookmark.reason.trim()) {
    return cleanVisibleText(bookmark.reason)
  }
  const parts = []
  if (Array.isArray(bookmark.pros) && bookmark.pros.length) {
    parts.push(bookmark.pros.map(cleanVisibleText).filter(Boolean).join('；'))
  }
  if (Array.isArray(bookmark.cons) && bookmark.cons.length) {
    parts.push(bookmark.cons.map(cleanVisibleText).filter(Boolean).join('；'))
  }
  if (bookmark.recommendedAction) {
    parts.push(cleanVisibleText(bookmark.recommendedAction))
  }
  return parts.filter(Boolean).join('。') || '等待 AI 补充推荐理由'
}

// ── 标签收集 ──

export function collectDisplayTags(bookmark = {}) {
  const rawTags = [
    ...(Array.isArray(bookmark.tags) ? bookmark.tags : []),
    ...(Array.isArray(bookmark.riskTags) ? bookmark.riskTags : [])
  ]
  return Array.from(new Set(
    rawTags
      .map(cleanVisibleText)
      .filter(Boolean)
  ))
}

// ── judgement 相关 ──

export function judgementKey(bookmark = {}) {
  const value = bookmark.finalJudgement || bookmark.judgement
  return JUDGEMENT_LABELS[value] ? value : 'steady'
}

// ── 主 displayCandidate ──

export function displayCandidate(bookmark = {}) {
  const judgement = judgementKey(bookmark)
  const metrics = extractMetrics(bookmark)

  return {
    programId: bookmark.programId,
    schoolName: resolveSchoolName(bookmark),
    programName: resolveProgramName(bookmark),
    reason: extractReason(bookmark),
    pros: (Array.isArray(bookmark.pros) ? bookmark.pros : []).map(cleanVisibleText).filter(Boolean),
    cons: (Array.isArray(bookmark.cons) ? bookmark.cons : []).map(cleanVisibleText).filter(Boolean),

    source: bookmark.source || '',
    sourceLabel: formatSource(bookmark.source),

    judgement,
    judgementLabel: formatJudgement(judgement),

    adjusted: bookmark.adjusted === true,
    adjustedLabel: bookmark.adjusted === true ? '系统已调整档位' : '',
    adjustReason: cleanVisibleText(bookmark.adjustReason || ''),

    status: bookmark.status || '',
    userConfirmed: bookmark.userConfirmed || bookmark.status === 'confirmed',
    statusLabel: (bookmark.userConfirmed || bookmark.status === 'confirmed') ? '已确认' : '待确认',

    tags: collectDisplayTags(bookmark),
    metrics
  }
}
