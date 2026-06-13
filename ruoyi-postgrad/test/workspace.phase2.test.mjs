import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '..')
const projectRoot = path.resolve(repoRoot, '..')

const files = {
  controller: path.join(projectRoot, 'ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/WorkspaceController.java'),
  service: path.join(repoRoot, 'src/main/java/com/ruoyi/postgrad/service/IWorkspaceService.java'),
  serviceImpl: path.join(repoRoot, 'src/main/java/com/ruoyi/postgrad/service/impl/WorkspaceServiceImpl.java'),
  mapper: path.join(repoRoot, 'src/main/java/com/ruoyi/postgrad/mapper/WorkspaceMapper.java'),
  mapperXml: path.join(repoRoot, 'src/main/resources/mapper/postgrad/WorkspaceMapper.xml')
}

for (const [name, file] of Object.entries(files)) {
  if (!fs.existsSync(file)) {
    throw new Error(`Missing ${name} file: ${file}`)
  }
}

const controller = fs.readFileSync(files.controller, 'utf8')
const service = fs.readFileSync(files.service, 'utf8')
const serviceImpl = fs.readFileSync(files.serviceImpl, 'utf8')
const mapper = fs.readFileSync(files.mapper, 'utf8')
const mapperXml = fs.readFileSync(files.mapperXml, 'utf8')

const expectations = [
  [controller.includes('@RequestMapping("/postgrad/workspace")'), 'controller should expose /postgrad/workspace'],
  [controller.includes('@GetMapping("/stats")'), 'controller should expose /stats'],
  [controller.includes('@GetMapping("/schools")'), 'controller should expose /schools'],
  [controller.includes('@GetMapping("/school/{id}")'), 'controller should expose /school/{id}'],
  [service.includes('selectWorkspaceStats'), 'service should define selectWorkspaceStats'],
  [service.includes('selectWorkspaceSchools'), 'service should define selectWorkspaceSchools'],
  [service.includes('selectSchoolWorkspace'), 'service should define selectSchoolWorkspace'],
  [serviceImpl.includes('WorkspaceMapper'), 'service implementation should use WorkspaceMapper'],
  [mapper.includes('selectWorkspaceStats'), 'mapper should define selectWorkspaceStats'],
  [mapper.includes('selectWorkspaceSchools'), 'mapper should define selectWorkspaceSchools'],
  [mapper.includes('selectSchoolPrograms'), 'mapper should define selectSchoolPrograms'],
  [mapperXml.includes('<sql id="DynamicCompletenessLevel">'), 'mapper XML should centralize dynamic completeness SQL'],
  [mapperXml.includes('when q.completeness_level is not null then q.completeness_level'), 'dynamic completeness should prefer quality records'],
  [mapperXml.includes("then 'A'"), 'dynamic completeness should calculate A'],
  [mapperXml.includes("then 'B'"), 'dynamic completeness should calculate B'],
  [mapperXml.includes("then 'D'"), 'dynamic completeness should calculate D'],
  [!mapperXml.includes("coalesce(q.completeness_level, 'C')"), 'mapper XML should not fall back with coalesce(q.completeness_level, C)']
]

const failed = expectations.filter(([passed]) => !passed)
if (failed.length) {
  throw new Error(failed.map(([, message]) => message).join('\n'))
}

console.log('Phase 2 workspace backend contract test passed')
