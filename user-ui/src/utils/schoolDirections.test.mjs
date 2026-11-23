import assert from 'node:assert/strict'
import { directionToggleLabel, isDirectionListExpanded, listedDirectionCount } from './schoolDirections.mjs'

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
