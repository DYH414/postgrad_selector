import { formatExamCombo, hasExamCombo } from './examComboDisplay.mjs'

export function isDirectionListExpanded({
  cardKey,
  isCatalogSearch = false,
  expandedKeys = [],
  collapsedKeys = []
} = {}) {
  if (collapsedKeys.includes(cardKey)) return false
  if (expandedKeys.includes(cardKey)) return true
  return Boolean(isCatalogSearch)
}

export function directionToggleLabel(directionCount, expanded) {
  if (expanded) return `收起方向列表（共 ${directionCount} 个）`
  return `查看全部 ${directionCount} 个招生方向`
}

export function listedDirections(school) {
  return Array.isArray(school?.directions) ? school.directions : []
}

export function listedDirectionCount(school) {
  return listedDirections(school).length
}

export function directionMetaExam(value) {
  return hasExamCombo(value) ? formatExamCombo(value) : ''
}

export function directionMetaYear(dataYear) {
  return dataYear ? `${dataYear}年数据` : '暂无'
}

/**
 * Truthful "listed" copy: never claim 全部 unless every matching direction is shown.
 */
export function directionListHeader(listed, total) {
  const shown = Number(listed) || 0
  const all = Number(total) || shown
  if (shown >= all) return `已列出全部 ${shown} 个符合方向`
  return `已列出 ${shown} / 共 ${all} 个符合方向`
}

export function catalogProgressLabel(listed, total) {
  const shown = Number(listed) || 0
  const all = Number(total) || 0
  if (all === 0) return '暂无匹配方向'
  if (shown >= all) return `已列出全部 ${all} 个符合方向`
  return `已列出 ${shown} / 共 ${all} 个符合方向`
}
