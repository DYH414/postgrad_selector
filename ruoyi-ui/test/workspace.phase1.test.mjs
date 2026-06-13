import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '..')

const files = {
  view: path.join(repoRoot, 'src/views/postgrad/workspace/index.vue'),
  api: path.join(repoRoot, 'src/api/postgrad/workspace.js'),
  menuSql: path.resolve(repoRoot, '../sql/postgrad_workspace_menu.sql')
}

for (const [name, file] of Object.entries(files)) {
  if (!fs.existsSync(file)) {
    throw new Error(`Missing ${name} file: ${file}`)
  }
}

const view = fs.readFileSync(files.view, 'utf8')
const api = fs.readFileSync(files.api, 'utf8')
const menuSql = fs.readFileSync(files.menuSql, 'utf8')

const expectations = [
  [view.includes('学校数据工作台'), 'view should render the workspace title'],
  [view.includes('workspace-shell'), 'view should expose the workspace layout shell'],
  [view.includes('loadWorkspace'), 'view should have a loadWorkspace action'],
  [api.includes('listWorkspaceStats'), 'api should export listWorkspaceStats'],
  [api.includes('listWorkspaceSchools'), 'api should export listWorkspaceSchools'],
  [api.includes('getSchoolWorkspace'), 'api should export getSchoolWorkspace'],
  [menuSql.includes('postgrad/workspace/index'), 'menu SQL should point to the workspace component'],
  [menuSql.includes('postgrad:workspace:view'), 'menu SQL should define the workspace permission']
]

const failed = expectations.filter(([passed]) => !passed)
if (failed.length) {
  throw new Error(failed.map(([, message]) => message).join('\n'))
}

console.log('Phase 1 workspace structure test passed')
