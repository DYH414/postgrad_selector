/**
 * Generation recovery helpers — detect in-progress draft and build status messages.
 */

/**
 * Is the draft still being generated?
 * Matches backend placeholder and partial drafts that have any tier with
 * insufficient=true and reason containing "正在".
 */
export function isDraftGenerating(draft) {
  if (!draft || !draft.tiers || !draft.tiers.length) return false
  return draft.tiers.some(
    t => t.insufficient && t.insufficientReason && t.insufficientReason.includes('正在')
  )
}

/**
 * Build a single AI status bubble to show after page refresh during generation.
 * Not persisted — stays in frontend memory only.
 */
export function getRestoredGenerationMessage() {
  return {
    role: 'assistant',
    content: '我正在后台继续为你筛选学校，稍等片刻就好～',
    messageType: 'generation_status',
    status: 'displayed',
    seq: -1
  }
}
