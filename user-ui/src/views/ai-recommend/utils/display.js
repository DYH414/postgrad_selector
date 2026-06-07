const SOURCE_LABELS = {
  conversation_ai: '对话加入',
  background_ai: '后台推荐',
  user_confirmed: '用户确认',
  auto_fill_discussed: '对话补入',
  auto_fill_search: '系统补入'
}

const JUDGEMENT_LABELS = {
  reach: '冲刺候选',
  steady: '稳妥线索',
  safe: '严格保底'
}

const INTERNAL_FIELD_RE = /\b(?:programId|canBeSafe|quotaRisk|dataCompleteness|safeBlockReason|aiJudgement|finalJudgement)\b\s*[:：=]?\s*[\w\u4e00-\u9fa5.-]*/gi
const ID_RE = /\bID\s*[:：]?\s*\d+\b/gi

export function cleanVisibleText(value) {
  if (value == null) return ''
  return String(value)
    .replace(ID_RE, '')
    .replace(INTERNAL_FIELD_RE, '')
    .replace(/\s{2,}/g, ' ')
    .replace(/[，,、；;：:\s]+$/g, '')
    .trim()
}

export function sourceLabel(source) {
  return SOURCE_LABELS[source] || cleanVisibleText(source) || '来源待确认'
}

export function judgementLabel(value) {
  return JUDGEMENT_LABELS[value] || cleanVisibleText(value) || '待判断'
}

export function judgementKey(bookmark = {}) {
  const value = bookmark.finalJudgement || bookmark.judgement
  return JUDGEMENT_LABELS[value] ? value : 'steady'
}

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

export function displayCandidate(bookmark = {}) {
  const judgement = judgementKey(bookmark)
  return {
    ...bookmark,
    schoolName: cleanVisibleText(bookmark.schoolName) || '-',
    programName: cleanVisibleText(bookmark.programName),
    reason: cleanVisibleText(bookmark.reason),
    sourceLabel: sourceLabel(bookmark.source),
    judgement,
    judgementLabel: judgementLabel(judgement),
    adjustedLabel: bookmark.adjusted === true ? '系统已调整档位' : '',
    tags: collectDisplayTags(bookmark)
  }
}
