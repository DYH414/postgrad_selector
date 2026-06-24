import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const home = readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const router = readFileSync(new URL('../router/index.js', import.meta.url), 'utf8')
const app = readFileSync(new URL('../App.vue', import.meta.url), 'utf8')

const homeRouteMatch = router.match(/path:\s*'\/'[\s\S]*?component:\s*\(\)\s*=>\s*import\('@\/views\/Home\.vue'\)[\s\S]*?meta:\s*\{[^}]*public:\s*true[^}]*\}/)
assert.ok(homeRouteMatch, 'root route should render Home.vue and be public')

assert.match(
  router,
  /path:\s*'\/recommend'[\s\S]*?component:\s*\(\)\s*=>\s*import\('@\/views\/Recommend\.vue'\)/,
  '/recommend should continue to render Recommend.vue'
)

assert.match(app, /currentRoute\?\.meta\?\.public|currentRoute\?\.value\?\.meta\?\.public|currentRoute\.value\.meta\.public/, 'App.vue token watcher should respect public routes')
assert.match(app, /routeIsPublic|isPublicRoute|meta\?\.public|meta\.public/, 'App.vue should name or check public route state before redirecting')

for (const sectionId of ['hero', 'simulation', 'ai-risk', 'client']) {
  assert.match(home, new RegExp(`id="${sectionId}"|id='${sectionId}'|id=\\{?["']${sectionId}`), `Home.vue should include #${sectionId} section`)
}

for (const copy of [
  '精准破译 408 统考指标',
  '告别信息泡沫',
  '按你的画像，精准召回符合条件的院校',
  '经条件筛选，共召回符合报考方向的院校专业：20 所',
  '408-RECRUIT-ENGINE',
  'AI 智能风险评估',
  '填写画像，让数据帮你决策',
  '进入正式客户端'
]) {
  assert.match(home, new RegExp(copy.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), `Home.vue should contain copy: ${copy}`)
}

for (const cssHook of [
  'estimate-card',
  'candidate-card',
  'risk-card',
  'terminal-line',
  'animation-delay',
  'prefers-reduced-motion'
]) {
  assert.match(home, new RegExp(cssHook), `Home.vue should include ${cssHook}`)
}

for (const removedField of ['本科层次', '跨考', '本科专业', '接受学硕', '安全边际']) {
  assert.doesNotMatch(home, new RegExp(removedField), `Home.vue should not show removed profile field: ${removedField}`)
}
