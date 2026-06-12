import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const router = readFileSync(join(currentDir, '../router/index.js'), 'utf8')
const workspace = readFileSync(join(currentDir, 'ai-recommend-v2/AiRecommendV2Workspace.vue'), 'utf8')
const profile = readFileSync(join(currentDir, 'ai-recommend-v2/components/ProfileSidebar.vue'), 'utf8')
const chat = readFileSync(join(currentDir, 'ai-recommend-v2/components/AiChatMiniPanel.vue'), 'utf8')
const candidates = readFileSync(join(currentDir, 'ai-recommend-v2/components/DraftPanel.vue'), 'utf8')

assert.match(router, /component:\s*\(\)\s*=>\s*import\('@\/views\/ai-recommend-v2\/AiRecommendV2Workspace\.vue'\)/)
assert.match(workspace, /class="hero-status-strip"/)
assert.match(workspace, /class="chat-panel-frame"/)
assert.match(workspace, /候选分析与追问/)
assert.match(workspace, /grid-template-columns:\s*300px minmax\(560px, 1fr\) 420px/)
assert.match(workspace, /height:\s*calc\(100vh - 206px\)/)
assert.match(profile, /class="score-panel"/)
assert.match(chat, /\.chat-mini\s*{[\s\S]*height:\s*100%/)
assert.match(candidates, /\.draft-panel\s*{[\s\S]*height:\s*100%/)
assert.match(candidates, /class="draft-steps"/)

console.log('AiRecommend layout checks passed')
