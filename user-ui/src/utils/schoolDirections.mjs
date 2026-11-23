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
