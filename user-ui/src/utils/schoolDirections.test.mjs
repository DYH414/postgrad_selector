import assert from 'node:assert/strict'
import { directionToggleLabel, isDirectionListExpanded } from './schoolDirections.mjs'

assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: true }), true)
assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: true, collapsedKeys: ['s1'] }), false)
assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: false }), false)
assert.equal(isDirectionListExpanded({ cardKey: 's1', isCatalogSearch: false, expandedKeys: ['s1'] }), true)
assert.equal(directionToggleLabel(21, true), '收起方向列表（共 21 个）')
assert.equal(directionToggleLabel(21, false), '查看全部 21 个招生方向')
