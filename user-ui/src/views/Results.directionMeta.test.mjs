import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import assert from 'node:assert/strict'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(currentDir, 'Results.vue'), 'utf8')

// The meta line must win over `.direction-main small { display: block }` and use a gap.
assert.match(source, /\.direction-main small\.direction-meta\s*\{[^}]*display:\s*flex/)
assert.match(source, /\.direction-main small\.direction-meta\s*\{[^}]*gap:/)

// An explicit separator keeps non-standard combos from reading as "8922025".
assert.match(source, /class="direction-meta-sep"/)
assert.match(source, /class="direction-year"/)
assert.doesNotMatch(source, /\$\{direction\.dataYear\}年数据/) // routed through directionMetaYear

// Listed counts and source labels must be truthful/tooltipped.
assert.match(source, /directionListHeader\(listedDirectionCount\(school\), school\.schoolTotal\)/)
assert.doesNotMatch(source, /已列出全部 \{\{ listedDirectionCount/)
assert.match(source, /:title="direction\.sourceLabel"/)
assert.match(source, /\.direction-source\s*\{[^}]*overflow-wrap:\s*anywhere/)
assert.match(source, /catalog-more-btn/)
assert.match(source, /searchPrograms\(keyword, CATALOG_PAGE_SIZE, 0\)/)

console.log('Results direction meta checks passed')
