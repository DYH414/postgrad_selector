import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = process.cwd()
const sidebarItem = readFileSync(join(root, 'src/layout/components/Sidebar/SidebarItem.vue'), 'utf8')

// Nested menu items must inherit their parent's base path exactly once.
assert.match(sidebarItem, /:base-path="resolvePath\(item\.path\)"/)
assert.doesNotMatch(sidebarItem, /:base-path="resolvePath\(child\.path\)"/)

console.log('postgrad menu path contract passed')
