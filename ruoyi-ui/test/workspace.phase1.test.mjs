import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '..')

const files = {
  view: path.join(repoRoot, 'src/views/postgrad/workspace/index.vue'),
  editorPanel: path.join(repoRoot, 'src/views/postgrad/workspace/components/WorkspaceEditorPanel.vue'),
  api: path.join(repoRoot, 'src/api/postgrad/workspace.js'),
  menuSql: path.resolve(repoRoot, '../sql/postgrad_workspace_menu.sql')
}

for (const [name, file] of Object.entries(files)) {
  if (!fs.existsSync(file)) {
    throw new Error(`Missing ${name} file: ${file}`)
  }
}

const view = fs.readFileSync(files.view, 'utf8')
const editorPanel = fs.readFileSync(files.editorPanel, 'utf8')
const api = fs.readFileSync(files.api, 'utf8')
const menuSql = fs.readFileSync(files.menuSql, 'utf8')

const expectations = [
  [view.includes('学校数据工作台'), 'view should render the workspace title'],
  [view.includes('N诺 Data Ops'), 'view should use the Nnuo workspace context'],
  [view.includes('workspace-shell'), 'view should expose the workspace layout shell'],
  [view.includes('loadWorkspace'), 'view should have a loadWorkspace action'],
  [view.includes('year: 2025'), 'view should default to the current Nnuo data year'],
  [view.includes("is408: '1'"), 'view should default to 408-only workspace data'],
  [view.includes('N诺完整度分布'), 'view should render the Nnuo completeness chart panel'],
  [view.includes('N诺覆盖率'), 'view should render the Nnuo coverage chart panel'],
  [view.includes('近年复试线趋势') || editorPanel.includes('近年复试线趋势'), 'workspace should render the score trend section'],
  [view.includes('renderCharts'), 'view should refresh charts with workspace data'],
  [view.includes('getSchoolWorkspace(this.selectedSchool.id, this.requestParams())'), 'school workspace should use the same filters as the global workspace'],
  [!view.includes('Object.assign({}, this.stats || {}, this.workspace.stats)'), 'school stats should not overwrite global chart stats'],
  [view.includes('Math.min(100'), 'coverage chart percentages should be capped at 100'],
  [view.includes('WorkspaceEditorPanel'), 'workspace view should render the inline editor panel component'],
  [view.includes('@saved="handleEditorSaved"'), 'workspace view should refresh after inline editor saves'],
  [view.includes('@year-change="handleEditorYearChange"'), 'workspace view should let the editor change the active maintenance year'],
  [
    view.includes('handleEditorYearChange(year)') &&
      view.includes('this.editorYear = Number(year)') &&
      !view.includes('handleEditorYearChange(year) {\n      if (!year || Number(year) === Number(this.filters.year)) return\n      this.filters.year = Number(year)\n      this.loadWorkspace()'),
    'editor year changes should stay inside the editor year state'
  ],
  [view.includes('editorYear: 2025'), 'workspace should keep editor maintenance year separate from workspace filters'],
  [view.includes(':year="editorYear"'), 'workspace should pass editorYear to the inline editor panel'],
  [
    view.includes('this.editorYear = Number(year)') &&
      !view.includes('handleEditorYearChange(year) {\n      if (!year || Number(year) === Number(this.filters.year)) return\n      this.filters.year = Number(year)'),
    'editor year changes should update editorYear without changing workspace filter year'
  ],
  [view.includes('grid-template-columns: 300px minmax(560px, 1fr) 520px'), 'workspace grid should reserve enough width for the editor panel'],
  [editorPanel.includes('name: \'WorkspaceEditorPanel\''), 'editor panel should define a stable component name'],
  [editorPanel.includes('维护年份'), 'editor panel should expose an editable maintenance year control'],
  [
    editorPanel.includes('currentYearSummary') &&
      editorPanel.includes('currentYearSummary.scoreLine') &&
      editorPanel.includes('currentYearSummary.totalPlan') &&
      editorPanel.includes('currentYearSummary.minAdmittedScore'),
    'editor overview should render the currently selected year instead of stale selectedProgram fields'
  ],
  [editorPanel.includes('moduleRequestSeq'), 'editor panel should ignore stale module responses when year changes quickly'],
  [editorPanel.includes('$emit(\'year-change\''), 'editor panel should emit year changes to the workspace'],
  [editorPanel.includes('editableModules'), 'editor panel should declare editable modules'],
  [editorPanel.includes('listCrud(module, query)'), 'editor panel should load existing module records by program and year'],
  [editorPanel.includes('updateCrud(module, payload)'), 'editor panel should update existing records'],
  [editorPanel.includes('addCrud(module, payload)'), 'editor panel should add missing current-year records'],
  [editorPanel.includes('$emit(\'saved\''), 'editor panel should notify parent after save'],
  [editorPanel.includes('return 24'), 'editor panel should use single-column form rows in the side panel'],
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
