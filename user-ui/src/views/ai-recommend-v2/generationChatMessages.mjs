const TIER_LABELS = {
  reach: '冲刺档',
  steady: '稳妥档',
  safe: '保底档'
}

const PHASE_MESSAGES = {
  profile_analysis: {
    key: 'profile',
    content: '我先看你的分数、地区偏好和风险倾向，尽量把候选范围收窄到真正值得讨论的学校。'
  },
  filter_408: {
    key: 'filter-408',
    content: '正在排除明显不匹配的专业方向，先保证推荐池和你的考试科目、目标方向一致。'
  },
  candidate_pool: {
    key: 'pool',
    content: '候选池已经搭起来了，接下来会分冲刺、稳妥、保底三档挑学校。'
  },
  ai_selecting_reach: {
    key: 'select-reach',
    content: '我在看冲刺档，会保留有上限但理由要足够站得住的选择。'
  },
  ai_selecting_steady: {
    key: 'select-steady',
    content: '正在挑稳妥档，这一档会更重视分数优势、城市和招生稳定性。'
  },
  ai_selecting_safe: {
    key: 'select-safe',
    content: '我开始补保底档，会优先看分数安全边际和名额稳定性。'
  },
  finalize: {
    key: 'finalize',
    content: '学校已经选得差不多了，我正在整理推荐理由和右侧草稿展示顺序。'
  }
}

export function createGenerationStatusMessage(key, content) {
  return {
    role: 'assistant',
    content,
    messageType: 'generation_status',
    status: 'completed',
    metadataJson: { key }
  }
}

export function getGenerationStartMessage() {
  return createGenerationStatusMessage(
    'start',
    '我开始生成候选草稿。这个过程会先筛学校，再分档选择，右侧会逐步出现结果。'
  )
}

export function getGenerationProgressMessage(event = {}) {
  if (event.tierData?.level) {
    const label = event.tierData.label || TIER_LABELS[event.tierData.level] || '当前档位'
    return createGenerationStatusMessage(
      `tier-ready-${event.tierData.level}`,
      `${label}已经初选完成，学校会一所一所加入右侧草稿，方便你跟着看。`
    )
  }

  const phaseMessage = PHASE_MESSAGES[event.phase]
  if (!phaseMessage) return null
  return createGenerationStatusMessage(phaseMessage.key, phaseMessage.content)
}

export function getGenerationDoneMessage(draft = {}) {
  const count = Array.isArray(draft?.tiers)
    ? draft.tiers.reduce((sum, tier) => sum + (Array.isArray(tier.candidates) ? tier.candidates.length : 0), 0)
    : 0

  return createGenerationStatusMessage(
    'done',
    count > 0
      ? `草稿已经生成完成，共 ${count} 所学校。你可以先看冲刺档风险，再决定要不要调整稳妥或保底。`
      : '草稿已经生成完成。你可以先看右侧分档结果，再让我解释某一所学校的风险。'
  )
}

export function shouldKeepGenerationStatus(messages = []) {
  return messages.some(message => message?.messageType === 'generation_status')
}
