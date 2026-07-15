import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

const adminVueConfig = read('ruoyi-ui/vue.config.js')
const adminProdEnv = read('ruoyi-ui/.env.production')
const adminLogin = read('ruoyi-ui/src/views/login.vue')
const adminSettings = read('ruoyi-ui/src/settings.js')
const adminHome = read('ruoyi-ui/src/views/index.vue')
const adminNavbar = read('ruoyi-ui/src/layout/components/Navbar.vue')
const appRequest = read('user-ui/src/api/request.js')
const appSse = read('user-ui/src/utils/event-source.js')
const appRecommendApi = read('user-ui/src/api/recommend-v2.js')
const appAuthController = read('ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppAuthController.java')
const appRecommendController = read('ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppV2RecommendController.java')
const readme = read('README.md')

assert.match(adminVueConfig, /publicPath:\s*process\.env\.NODE_ENV\s*===\s*["']production["']\s*\?\s*["']\/admin\/["']/, 'admin production publicPath must be /admin/')
assert.match(adminProdEnv, /VUE_APP_BASE_API\s*=\s*['"]\/dev-api['"]/, 'admin production API prefix must be /dev-api')
assert.match(readme, /location\s+\/dev-api\//, 'README production nginx must proxy /dev-api/')
assert.doesNotMatch(readme, /location\s+\/api\//, 'README production nginx must not advertise /api/ when frontends use /dev-api')

assert.match(appRequest, /API_BASE_URL/, 'user axios base URL must use shared API_BASE_URL')
assert.match(appSse, /API_BASE_URL/, 'user EventSource URL must use shared API_BASE_URL')
assert.match(appRecommendApi, /apiUrl\(/, 'user fetch SSE chat must use shared API URL helper')
assert.doesNotMatch(appRecommendApi, /fetch\(['"`]\/dev-api/, 'user chat fetch must not hardcode /dev-api')

assert.doesNotMatch(adminLogin, /username:\s*["']admin["']/, 'admin login must not prefill admin username')
assert.doesNotMatch(adminLogin, /password:\s*["']admin123["']/, 'admin login must not prefill admin password')
assert.doesNotMatch(adminSettings, /RuoYi/, 'admin footer must not expose RuoYi branding')
assert.doesNotMatch(adminHome, /若依后台管理框架|ruoyi\.vip|gitee\.com\/y_project\/RuoYi-Vue/, 'admin home must not show default RuoYi landing copy')
assert.doesNotMatch(adminNavbar, /RuoYiGit|RuoYiDoc|源码地址|文档地址/, 'admin navbar must not show RuoYi source/doc shortcuts')

assert.doesNotMatch(appAuthController, /暂时未开放测试通道/, 'app registration endpoint must not be hard-disabled for launch')
assert.match(appAuthController, /insertAppUser/, 'app registration must persist a user through AppUserService')
assert.doesNotMatch(appRecommendController, /payload\.put\("message",\s*error\.getMessage\(\)\)/, 'SSE should not expose raw Throwable messages')
assert.doesNotMatch(appRecommendController, /payload\.put\("message",\s*e\.getMessage\(\)\)/, 'SSE should not expose raw Exception messages')
