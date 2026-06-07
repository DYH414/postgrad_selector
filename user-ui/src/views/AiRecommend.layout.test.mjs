import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const workspace = readFileSync(join(currentDir, 'ai-recommend/AiRecommendWorkspace.vue'), 'utf8')
const chat = readFileSync(join(currentDir, '../components/AiChatPanel.vue'), 'utf8')
const candidates = readFileSync(join(currentDir, 'ai-recommend/components/ReportCandidatePanel.vue'), 'utf8')

assert.match(workspace, /grid-template-columns:\s*300px minmax\(560px, 1fr\) 360px/)
assert.match(workspace, /height:\s*calc\(100vh - 180px\)/)
assert.match(workspace, /\.profile-scroll,[\s\S]*\.chat-scroll,[\s\S]*\.candidate-scroll/)
assert.match(chat, /\.ai-chat-panel\s*{[\s\S]*height:\s*100%/)
assert.match(chat, /width:\s*min\(100%, 720px\)/)
assert.match(candidates, /\.candidate-card\s*{[\s\S]*height:\s*100%/)
assert.match(candidates, /\.candidate-groups\s*{[\s\S]*overflow-y:\s*auto/)

console.log('AiRecommend layout checks passed')
