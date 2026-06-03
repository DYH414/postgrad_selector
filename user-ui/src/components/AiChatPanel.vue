<template>
  <div class="ai-chat-panel" :class="{ open: visible }">
    <div class="panel-header">
      <span><i class="el-icon-chat-dot-round" /> AI 择校顾问</span>
      <el-button type="text" icon="el-icon-close" @click="emit('close')" />
    </div>

    <div class="panel-body" ref="body">
      <div v-if="messages.length === 0 && !loading" class="empty-state">
        <i class="el-icon-chat-line-round" />
        <p>点击「AI 推荐」开始智能择校对话</p>
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" :class="['msg', msg.role]">
        <div class="msg-avatar">
          <i v-if="msg.role === 'assistant'" class="el-icon-cpu" />
          <i v-else class="el-icon-user" />
        </div>
        <div class="msg-bubble">{{ stripMarkdown(msg.content) }}</div>
      </div>

      <div v-if="loading" class="msg assistant">
        <div class="msg-avatar"><i class="el-icon-cpu" /></div>
        <div class="msg-bubble typing"><span>.</span><span>.</span><span>.</span></div>
      </div>
    </div>

    <div v-if="currentOptions.length > 0 && !loading" class="options-bar">
      <el-button v-for="(opt, i) in currentOptions" :key="i"
        size="small" type="primary" plain @click="sendOption(opt)">
        {{ opt }}
      </el-button>
    </div>

    <div class="input-bar">
      <el-input v-model="input" placeholder="输入你的想法..."
        size="small" @keyup.enter="sendMessage">
        <template #append>
          <el-button icon="el-icon-s-promotion"
            :disabled="!input.trim() || loading" @click="sendMessage" />
        </template>
      </el-input>
      <el-button v-if="messages.length >= 4" type="success" size="small"
        style="margin-top:6px;width:100%" @click="generateReport">
        生成推荐报告
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { postAiStart, postAiChat, postAiChatStream, postAiGenerateReport } from '@/api/ai'

const props = defineProps({
  visible: { type: Boolean, default: false },
  candidateIds: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'fallback'])

const router = useRouter()

const body = ref(null)
const conversationId = ref(null)
const messages = ref([])
const currentOptions = ref([])
const input = ref('')
const loading = ref(false)

watch(() => props.visible, (val) => {
  if (val && !conversationId.value) startConversation()
})

async function startConversation() {
  loading.value = true
  try {
    const res = await postAiStart({ candidateIds: props.candidateIds })
    conversationId.value = res.data.conversationId
    messages.value = [{ role: 'assistant', content: res.data.message }]
    currentOptions.value = res.data.options || []
    saveToLocal()
  } catch (e) {
    ElMessage.error('启动 AI 对话失败')
  } finally {
    loading.value = false
  }
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  currentOptions.value = []
  loading.value = true
  await callChat(text)
  scrollToBottom()
}

async function sendOption(opt) {
  messages.value.push({ role: 'user', content: opt })
  currentOptions.value = []
  loading.value = true
  await callChat(opt)
  scrollToBottom()
}

async function callChat(text) {
  let assistantIndex = -1
  let rawAssistantContent = ''
  const ensureAssistantMsg = () => {
    if (assistantIndex < 0) {
      messages.value.push({ role: 'assistant', content: '' })
      assistantIndex = messages.value.length - 1
    }
    return assistantIndex
  }
  try {
    await postAiChatStream(
      { conversationId: conversationId.value, message: text },
      {
        onToken(token) {
          rawAssistantContent += token
          const idx = ensureAssistantMsg()
          messages.value[idx].content = stripMarkdown(visibleStreamContent(rawAssistantContent))
          loading.value = false
          scrollToBottom()
        },
        onDone(data) {
          const idx = ensureAssistantMsg()
          messages.value[idx].content = visibleStreamContent(data.message || messages.value[idx].content)
          currentOptions.value = data.options || []
          saveToLocal()
        },
        onError(error) {
          throw error
        }
      }
    )
  } catch (e) {
    await callChatFallback(text, ensureAssistantMsg())
  } finally {
    loading.value = false
  }
}

function visibleStreamContent(content) {
  const optionsMarker = content.indexOf('---OPTIONS---')
  return (optionsMarker >= 0 ? content.slice(0, optionsMarker) : content).trimStart()
}

function stripMarkdown(text) {
  if (!text) return text
  return text
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^###\s+/gm, '')
    .replace(/^##\s+/gm, '')
    .replace(/^#\s+/gm, '')
    .replace(/^>\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '· ')
    .replace(/\|/g, ' ')
}

async function callChatFallback(text, assistantIndex) {
  messages.value[assistantIndex].content = '正在处理...'
  // 等一下让 AI 完成工具调用
  await new Promise(r => setTimeout(r, 2000))
  try {
    const res = await postAiChat({ conversationId: conversationId.value, message: text })
    if (res.data.fallback) {
      messages.value.splice(assistantIndex, 1)
      emit('fallback', res.data)
      return
    }
    messages.value[assistantIndex].content = visibleStreamContent(res.data.message)
    currentOptions.value = res.data.options || []
    saveToLocal()
  } catch (e) {
    // 再试一次
    await new Promise(r => setTimeout(r, 3000))
    try {
      const res = await postAiChat({ conversationId: conversationId.value, message: text })
      messages.value[assistantIndex].content = visibleStreamContent(res.data.message || 'AI 对话暂不可用，请稍后重试。')
      currentOptions.value = res.data.options || []
    } catch (_) {
      messages.value[assistantIndex].content = 'AI 对话暂不可用，请稍后重试。'
    }
  }
}

function trackPendingReport(reportId) {
  try {
    const pending = JSON.parse(sessionStorage.getItem('pending_reports') || '[]')
    pending.push({ id: reportId, ts: Date.now() })
    sessionStorage.setItem('pending_reports', JSON.stringify(pending.slice(-5)))
  } catch (_) {}
}

async function generateReport() {
  loading.value = true
  try {
    const res = await postAiGenerateReport({ conversationId: conversationId.value })
    const reportId = res.data.reportId
    trackPendingReport(reportId)
    router.push({ name: 'AiReport', params: { id: reportId } })
  } catch (e) {
    ElMessage.error('生成报告失败')
  } finally {
    loading.value = false
  }
}

function saveToLocal() {
  try {
    const data = JSON.stringify({ messages: messages.value, options: currentOptions.value })
    if (data.length > 500_000) {
      const trimmed = { messages: messages.value.slice(-10), options: currentOptions.value }
      localStorage.setItem('ai_conv_' + conversationId.value, JSON.stringify(trimmed))
      return
    }
    localStorage.setItem('ai_conv_' + conversationId.value, data)
  } catch (e) { /* quota exceeded, ignore */ }
}

function scrollToBottom() {
  nextTick(() => {
    const el = body.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.ai-chat-panel {
  position: fixed; right: -420px; top: 0; width: 400px; height: 100vh;
  background: #fff; box-shadow: -2px 0 12px rgba(0,0,0,.1);
  display: flex; flex-direction: column; transition: right .3s; z-index: 2000;
}
.ai-chat-panel.open { right: 0; }
.panel-header {
  padding: 12px 16px; border-bottom: 1px solid #ebeef5;
  display: flex; justify-content: space-between; align-items: center;
  font-weight: 600;
}
.panel-body { flex: 1; overflow-y: auto; padding: 12px; }
.empty-state { text-align: center; color: #909399; padding-top: 80px; }
.empty-state i { font-size: 48px; display: block; margin-bottom: 12px; }
.msg { display: flex; margin-bottom: 12px; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%;
  background: #f0f2f5; display: flex; align-items: center; justify-content: center;
  margin: 0 8px; flex-shrink: 0; }
.msg.assistant .msg-avatar { background: #409eff; color: #fff; }
.msg-bubble { max-width: 280px; padding: 8px 12px; border-radius: 12px;
  font-size: 14px; line-height: 1.6; }
.msg.assistant .msg-bubble { background: #f0f2f5; border-top-left-radius: 2px; }
.msg.user .msg-bubble { background: #409eff; color: #fff; border-top-right-radius: 2px; }
.typing span { animation: blink 1.4s infinite both; }
.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,80%,100% { opacity: 0; } 40% { opacity: 1; } }
.options-bar { padding: 8px 12px; display: flex; flex-wrap: wrap; gap: 6px; }
.input-bar { padding: 8px 12px; border-top: 1px solid #ebeef5; }
</style>
