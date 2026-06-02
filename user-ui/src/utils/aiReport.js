const JUDGEMENT_LABELS = {
  safe: '保底',
  steady: '稳妥',
  steady_reach: '稳妥偏冲',
  small_reach: '小冲',
  high_risk_reach: '高风险冲刺',
  data_insufficient_pending: '数据不足待核验'
}

const VERIFICATION_STATUS_LABELS = {
  official: '官网核实',
  third_party: '第三方来源',
  local_data_only: '仅本地数据',
  verification_failed: '核实失败',
  pending: '待核实'
}

const JUDGEMENT_ORDER = [
  'steady',
  'steady_reach',
  'safe',
  'small_reach',
  'high_risk_reach',
  'data_insufficient_pending'
]

function parseReport(data) {
  if (!data) return null
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch (e) {
      return null
    }
  }
  return data
}

function toArray(value) {
  if (!value) return []
  return Array.isArray(value) ? value.filter(Boolean) : [value]
}

function judgementRank(value) {
  const index = JUDGEMENT_ORDER.indexOf(value)
  return index >= 0 ? index : JUDGEMENT_ORDER.length
}

function completenessRank(value) {
  if (value === 'A') return 0
  if (value === 'B') return 1
  if (value === 'C') return 2
  return 3
}

function normalizeSchool(school) {
  const judgement = JUDGEMENT_LABELS[school.judgement] ? school.judgement : 'data_insufficient_pending'
  const verificationStatus = VERIFICATION_STATUS_LABELS[school.verificationStatus]
    ? school.verificationStatus
    : 'local_data_only'
  const evidence = toArray(school.evidence || school.reason)
  const risks = toArray(school.risks || school.cons)
  const avgScoreGap = school.avgScoreGap ?? school.gap ?? null

  return {
    ...school,
    judgement,
    judgementLabel: school.judgementLabel || JUDGEMENT_LABELS[judgement],
    verificationStatus,
    verificationStatusLabel: VERIFICATION_STATUS_LABELS[verificationStatus],
    evidence,
    risks,
    avgScoreGap,
    recommendedAction: school.recommendedAction || ''
  }
}

function sortSchools(schools) {
  return [...schools].sort((left, right) => {
    const judgementDiff = judgementRank(left.judgement) - judgementRank(right.judgement)
    if (judgementDiff !== 0) return judgementDiff

    const completenessDiff = completenessRank(left.dataCompleteness) - completenessRank(right.dataCompleteness)
    if (completenessDiff !== 0) return completenessDiff

    const leftGap = left.avgScoreGap ?? Number.POSITIVE_INFINITY
    const rightGap = right.avgScoreGap ?? Number.POSITIVE_INFINITY
    return Math.abs(leftGap) - Math.abs(rightGap)
  })
}

export function normalizeAiReport(raw) {
  const data = parseReport(raw && raw.result ? raw.result : raw)
  if (!data) {
    return { summary: '', tiers: [], metadata: {}, legacy: false }
  }

  const tiers = Array.isArray(data.tiers) ? data.tiers : []
  const normalizedTiers = tiers.map(tier => ({
    ...tier,
    schools: sortSchools(toArray(tier.schools).map(normalizeSchool))
  }))
  const legacy = normalizedTiers.some(tier =>
    tier.schools.some(school => school.matchScore != null && !school.rawJudgement && !school.judgement)
  ) || tiers.some(tier =>
    toArray(tier.schools).some(school => school.matchScore != null && !school.judgement)
  )

  return {
    ...data,
    summary: data.summary || '',
    tiers: normalizedTiers,
    metadata: data.metadata || {},
    legacy
  }
}
