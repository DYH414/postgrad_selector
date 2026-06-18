export function mergeTierRevealFrame(draft, tierData, visibleCount) {
  const baseDraft = draft || { tiers: [], removedCandidates: [], blockedCandidates: [] }
  const sourceCandidates = Array.isArray(tierData?.candidates) ? tierData.candidates : []
  const count = Math.max(0, Math.min(visibleCount, sourceCandidates.length))
  const visibleTier = {
    ...tierData,
    candidates: sourceCandidates.slice(0, count)
  }

  if (count < sourceCandidates.length) {
    visibleTier.insufficient = true
    visibleTier.insufficientReason = 'AI 正在整理推荐理由...'
  }

  const tiers = Array.isArray(baseDraft.tiers) ? [...baseDraft.tiers] : []
  const existingIdx = tiers.findIndex(t => t.level === tierData.level)
  if (existingIdx >= 0) {
    tiers[existingIdx] = visibleTier
  } else {
    tiers.push(visibleTier)
  }

  return {
    ...baseDraft,
    tiers
  }
}

export function revealTierFrames(tierData) {
  const candidates = Array.isArray(tierData?.candidates) ? tierData.candidates : []
  return candidates.map((_, index) => index + 1)
}
