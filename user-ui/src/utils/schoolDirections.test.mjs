import assert from 'node:assert/strict'
import {
  catalogProgressLabel,
  directionListHeader,
  directionMetaExam,
  directionMetaYear,
  directionToggleLabel,
  isDirectionListExpanded,
  listedDirectionCount
} from './schoolDirections.mjs'

assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: true }), true)
assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: true, collapsedKeys: ['s1'] }), false)
assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: false }), false)
assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: false, expandedKeys: ['s1'] }), true)
assert.equal(directionToggleLabel(21, true), '收起方向列表（共 21 个）')
assert.equal(directionToggleLabel(21, false), '查看全部 21 个招生方向')

const nankai = {
  directionCount: 15,
  directions: Array.from({ length: 9 }, (_, i) => ({ programId: i + 1 }))
}
assert.notEqual(nankai.directionCount, nankai.directions.length)
assert.equal(listedDirectionCount(nankai), 9)
assert.equal(directionToggleLabel(listedDirectionCount({ directions: Array.from({ length: 15 }) }), true), '收起方向列表（共 15 个）')

// Non-standard combos must be separated from the year, never glued as "8922025".
assert.equal(directionMetaExam('101,204,302,892'), '101/204/302/892')
assert.equal(directionMetaYear(2025), '2025年数据')
assert.equal(directionMetaExam('22408'), '22408（101/204/302/408）')
assert.equal(directionMetaYear(null), '暂无')

// Never claim 全部 while the backend still has more matching directions.
assert.equal(directionListHeader(101, 101), '已列出全部 101 个符合方向')
assert.equal(directionListHeader(50, 101), '已列出 50 / 共 101 个符合方向')
assert.equal(directionListHeader(50, undefined), '已列出全部 50 个符合方向')
assert.equal(catalogProgressLabel(103, 103), '已列出全部 103 个符合方向')
assert.equal(catalogProgressLabel(500, 1714), '已列出 500 / 共 1714 个符合方向')
assert.equal(catalogProgressLabel(0, 0), '暂无匹配方向')
