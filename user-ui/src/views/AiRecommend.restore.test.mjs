import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const chat = readFileSync(join(currentDir, '../components/AiChatPanel.vue'), 'utf8')
const workspace = readFileSync(join(currentDir, 'ai-recommend/AiRecommendWorkspace.vue'), 'utf8')

assert.match(chat, /AI_RECENT_CONVERSATION_KEY/)
assert.match(chat, /restoreConversation/)
assert.match(chat, /localStorage\.setItem\(AI_RECENT_CONVERSATION_KEY/)
assert.match(workspace, /AI_RECENT_CONVERSATION_KEY/)
assert.match(workspace, /restoreConversationContext/)
assert.match(workspace, /refreshBookmarks\(storedId\)/)

console.log('AiRecommend restore checks passed')
