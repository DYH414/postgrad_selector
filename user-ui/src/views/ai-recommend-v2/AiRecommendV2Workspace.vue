<template>
  <div class="ai-workspace">
    <AppHeader current-page="ai" />

    <main class="ai-main">
      <!-- Body grid: rail / canvas / draft -->
      <div class="body-grid">
        <!-- Left rail -->
        <aside class="rail">
          <ProfileSidebar
            :profile="profile"
            :loading="loadingProfile"
            :missing-fields="missingFields"
            @edit="$router.push('/profile')"
          />
          <GenerateDraftButton
            :generating="generating"
            :progress="progressState"
            @generate="handleGenerate"
          />
        </aside>

        <!-- Main canvas: chat -->
        <section class="canvas">
          <div class="canvas-head">
            <div class="canvas-head-left">
              <div class="canvas-orb" :class="{ live: chatStreaming }" />
              <div>
                <h2 class="canvas-title">AI 助手</h2>
                <p class="canvas-sub">解释候选理由 · 校准冲稳保 · 检查风险</p>
              </div>
            </div>
            <span
              :class="['canvas-state', { live: chatStreaming }]"
            >
              <span class="state-dot" />
              {{ chatStreaming ? '生成中' : (chatMessages.length ? '可追问' : '待对话') }}
            </span>
          </div>

          <AiChatMiniPanel
            class="canvas-chat"
            :visible="chatVisible"
            :messages="chatMessages"
            :streaming="chatStreaming"
            :streaming-text="chatStreamingText"
            :tool-call="currentToolCall"
            @send="handleChatSend"
            @toggle="chatVisible = !chatVisible"
          />
        </section>

        <!-- Right column: draft -->
        <aside class="draft-col">
          <DraftPanel
            :draft="draft"
            :loading="generating"
            :progress="progressState"
            @remove="handleRemove"
            @replace="handleReplace"
            @add-back="handleAddBack"
            @add-from-workspace="handleAddFromWorkspace"
            @ask-about="handleAskAbout"
            @generate-report="handleGenerateReport"
          />
        </aside>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDraft, startGenerateDraft, openDraftGenerationStream, removeCandidate, replaceCandidate,
  addBackCandidate, addFromWorkspace, generateReport, sendChatMessage, startChat, resumeChat
} from '@/api/recommend-v2'
import { getProfile } from '@/api/profile'
import { closeEventSource } from '@/utils/event-source'
import AppHeader from '@/components/AppHeader.vue'
import ProfileSidebar from './components/ProfileSidebar.vue'
import GenerateDraftButton from './components/GenerateDraftButton.vue'
import AiChatMiniPanel from './components/AiChatMiniPanel.vue'
import DraftPanel from './components/DraftPanel.vue'
import { mergeTierRevealFrame, revealTierFrames } from './draftRevealQueue.mjs'
import {
  getGenerationStartMessage,
  getGenerationProgressMessage,
  getGenerationDoneMessage
} from './generationChatMessages.mjs'

const router = useRouter()
const STAGES = [
  { phase: 'profile_analysis', label: '分析用户画像' },
  { phase: 'filter_408', label: '筛选408专业' },
  { phase: 'candidate_pool', label: '构建候选池' },
  { phase: 'ai_selecting_reach', label: 'AI选择冲刺档' },
  { phase: 'ai_selecting_steady', label: 'AI选择稳妥档' },
  { phase: 'ai_selecting_safe', label: 'AI选择保底档' },
  { phase: 'finalize', label: '生成候选草稿' }
]

// ── 画像 ──
const loadingProfile = ref(false)
const profile = ref({})
const missingFields = ref([])

// ── 草稿 ──
const draft = shallowRef(null)
const generating = ref(false)
const progressState = ref({ phase: '', message: '' })
const draftEventSource = ref(null)
const pendingFinalDraft = ref(null)
const revealTimers = new Set()
let revealFrameIndex = 0
const {
  applyProgress,
  startProgress,
  stopProgress,
  resetProgress
} = useGenerationProgress()

// ── 对话 ──
const chatVisible = ref(true)
const chatMessages = ref([])
const chatStreaming = ref(false)
const chatStreamingText = ref('')
const currentToolCall = ref('')
const generationBubbleQueue = ref([])
const generationBubbleKeys = new Set()
let generationBubbleTimer = null
let generationBubbleTicking = false

// ── 计算属性 ──
function useGenerationProgress() {
  const currentPhase = ref('')
  const currentMessage = ref('')
  const currentTierKey = ref('')
  const currentStageLabel = ref('')
  const completedTiers = ref([])
  const tierCandidateCounts = ref({})
  const elapsedSec = ref(0)
  const stageElapsed = ref(0)
  const currentStep = ref(0)
  const totalSteps = ref(STAGES.length)
  const progressPercent = ref(0)
  let timer = null

  function applyProgress(data = {}) {
    currentPhase.value = data.phase || 'running'
    currentMessage.value = data.message || '正在生成草稿...'
    progressState.value = {
      phase: currentPhase.value,
      message: currentMessage.value
    }
    currentTierKey.value = data.tier || ''
    const stageIndex = STAGES.findIndex(stage => stage.phase === currentPhase.value)
    if (stageIndex >= 0) {
      currentStep.value = stageIndex + 1
      currentStageLabel.value = STAGES[stageIndex].label
    } else {
      currentStageLabel.value = currentMessage.value
    }
    progressPercent.value = Math.max(0, Math.min(100, Math.round((currentStep.value / totalSteps.value) * 100)))

    if (data.status === 'success' && data.tier && !completedTiers.value.includes(data.tier)) {
      completedTiers.value = [...completedTiers.value, data.tier]
    }
    if (data.tier && data.afterCount != null) {
      tierCandidateCounts.value = { ...tierCandidateCounts.value, [data.tier]: data.afterCount }
    }
  }

  function startProgress() {
    stopProgress()
    elapsedSec.value = 0
    stageElapsed.value = 0
    timer = window.setInterval(() => {
      elapsedSec.value += 1
      stageElapsed.value += 1
    }, 1000)
  }

  function stopProgress() {
    if (timer) {
      window.clearInterval(timer)
      timer = null
    }
  }

  function resetProgress() {
    stopProgress()
    currentPhase.value = ''
    currentMessage.value = ''
    currentTierKey.value = ''
    currentStageLabel.value = ''
    completedTiers.value = []
    tierCandidateCounts.value = {}
    elapsedSec.value = 0
    stageElapsed.value = 0
    currentStep.value = 0
    progressPercent.value = 0
  }

  return {
    currentPhase,
    currentMessage,
    currentTierKey,
    currentStageLabel,
    completedTiers,
    tierCandidateCounts,
    elapsedSec,
    stageElapsed,
    currentStep,
    totalSteps,
    progressPercent,
    applyProgress,
    startProgress,
    stopProgress,
    resetProgress
  }
}

function sanitizeAssistantText(text) {
  if (!text) return ''
  return String(text)
    .replace(/\s*[（(]\s*(?:ID|Id|id|programId|schoolId|院校ID|专业ID)\s*[:：#]?\s*\d+\s*[)）]/g, '')
    .replace(/\b(?:ID|Id|id|programId|schoolId)\s*[:：#]\s*\d+\b/g, '')
    .replace(/(?:院校ID|专业ID|学校ID|内部编号)\s*[:：#]?\s*\d+/g, '')
}

function normalizeChatMessage(msg) {
  if (!msg || !msg.role) return null
  if (msg.role === 'system') return null
  const content = msg.content || msg.displayContent || ''
  return {
    role: msg.role,
    content: msg.role === 'assistant' ? sanitizeAssistantText(content) : content,
    messageType: msg.messageType || 'text',
    status: msg.status || 'completed',
    metadataJson: msg.metadataJson || null
  }
}

function resetGenerationBubbles() {
  generationBubbleQueue.value = []
  generationBubbleKeys.clear()
  if (generationBubbleTimer) {
    window.clearTimeout(generationBubbleTimer)
    generationBubbleTimer = null
  }
  generationBubbleTicking = false
}

function enqueueGenerationBubble(message) {
  if (!message || !message.content) return
  const key = message.metadataJson?.key || `${message.role}:${message.content}`
  if (generationBubbleKeys.has(key)) return

  generationBubbleKeys.add(key)
  generationBubbleQueue.value.push(message)
  scheduleGenerationBubbleFlush()
}

function scheduleGenerationBubbleFlush() {
  if (generationBubbleTicking || !generationBubbleQueue.value.length) return
  generationBubbleTicking = true

  const flushNext = () => {
    const next = generationBubbleQueue.value.shift()
    if (next) {
      chatMessages.value.push(next)
    }

    if (!generationBubbleQueue.value.length) {
      generationBubbleTicking = false
      generationBubbleTimer = null
      return
    }

    const delay = next?.metadataJson?.key === 'start'
      ? 900
      : 1900 + Math.floor(Math.random() * 1100)
    generationBubbleTimer = window.setTimeout(flushNext, delay)
  }

  generationBubbleTimer = window.setTimeout(flushNext, 320)
}

function flushGenerationBubblesNow() {
  if (generationBubbleTimer) {
    window.clearTimeout(generationBubbleTimer)
    generationBubbleTimer = null
  }
  generationBubbleQueue.value.forEach(message => {
    chatMessages.value.push(message)
  })
  generationBubbleQueue.value = []
  generationBubbleTicking = false
}

async function loadProfileData() {
  loadingProfile.value = true
  try {
    const res = await getProfile()
    profile.value = res.data || {}
  } finally {
    loadingProfile.value = false
  }
}

async function loadDraftData() {
  try {
    const res = await getDraft()
    draft.value = res.data || null
  } catch (e) { /* 草稿不存在 */ }
}

async function loadChatHistory(options = {}) {
  try {
    const res = await resumeChat()
    const messages = Array.isArray(res.data?.messages) ? res.data.messages : []
    const preserved = options.preserveGenerationStatus
      ? chatMessages.value.filter(message => message?.messageType === 'generation_status')
      : []
    chatMessages.value = preserved.concat(messages
      .map(normalizeChatMessage)
      .filter(Boolean))
  } catch (e) {
    if (!options.preserveGenerationStatus) {
      chatMessages.value = []
    }
  }
}

async function handleGenerate() {
  generating.value = true
  clearRevealTimers()
  revealFrameIndex = 0
  resetGenerationBubbles()
  resetProgress()
  startProgress()
  applyProgress({ phase: 'queued', message: '正在准备生成草稿...' })
  draft.value = null
  pendingFinalDraft.value = null
  chatMessages.value = []
  enqueueGenerationBubble(getGenerationStartMessage())
  closeEventSource(draftEventSource)

  try {
    const res = await startGenerateDraft()
    const task = res.data || {}
    if (task.status === 'busy') {
      generating.value = false
      stopProgress()
      resetGenerationBubbles()
      applyProgress({ phase: 'busy', message: task.message || '已有草稿正在生成' })
      ElMessage.warning(task.message || '已有草稿正在生成，请稍候')
      return
    }
    if (!task.taskId || !task.streamToken) {
      throw new Error('生成任务创建失败')
    }

    const source = openDraftGenerationStream({
      taskId: task.taskId,
      streamToken: task.streamToken
    })
    draftEventSource.value = source

    source.addEventListener('progress', event => {
      const data = JSON.parse(event.data)
      applyProgress(data)
      enqueueGenerationBubble(getGenerationProgressMessage(data))
      if (data.tierData) {
        revealTierCandidates(data.tierData)
      }
    })

    source.addEventListener('done', async event => {
      const data = JSON.parse(event.data)
      pendingFinalDraft.value = data.draft
      applyProgress({ phase: 'finalize', message: '正在整理候选草稿...' })
      enqueueGenerationBubble(getGenerationProgressMessage({ phase: 'finalize' }))
      enqueueGenerationBubble(getGenerationDoneMessage(data.draft))
      closeEventSource(draftEventSource)
      await completeGenerationWhenRevealSettles()
    })

    source.addEventListener('error', event => {
      let message = '生成草稿连接中断'
      if (event?.data) {
        try {
          message = JSON.parse(event.data).message || message
        } catch (_) {}
      }
      applyProgress({ phase: 'error', message })
      generating.value = false
      stopProgress()
      clearRevealTimers()
      resetGenerationBubbles()
      closeEventSource(draftEventSource)
      ElMessage.error(message)
    })
  } catch (e) {
    const msg = e.message || '网络错误'
    if (msg.includes('正在生成')) {
      ElMessage.warning('已有草稿正在生成，请稍候')
    } else {
      ElMessage.error('生成草稿失败：' + msg)
    }
    applyProgress({ phase: 'error', message: msg })
    generating.value = false
    stopProgress()
    clearRevealTimers()
    resetGenerationBubbles()
    closeEventSource(draftEventSource)
  }
}

async function handleRemove(programId) {
  try {
    const res = await removeCandidate(programId)
    draft.value = res.data
    ElMessage.success('已移出草稿')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleReplace({ removeProgramId, tier, preference }) {
  try {
    const res = await replaceCandidate(removeProgramId, tier, preference || 'safer')
    draft.value = res.data.draft
    ElMessage.success('已替换')
  } catch (e) {
    ElMessage.error('替换失败：' + (e.response?.data?.msg || e.message))
  }
}

async function handleAddFromWorkspace({ tier, preference }) {
  try {
    const res = await addFromWorkspace(tier, preference || 'safer')
    draft.value = res.data
    ElMessage.success('已补充候选')
  } catch (e) {
    ElMessage.error('补充失败：' + (e.response?.data?.msg || e.message))
  }
}

async function handleAddBack(programId) {
  try {
    const res = await addBackCandidate(programId)
    draft.value = res.data
    ElMessage.success('已加回草稿')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

function handleAskAbout(programId, schoolName, programName) {
  chatVisible.value = true
  const label = programName ? `${schoolName}-${programName}` : (schoolName || '这所学校')
  handleChatSend(`帮我分析一下 ${label} 为什么被推荐，有什么风险`)
}

async function handleChatSend(message) {
  chatVisible.value = true
  chatMessages.value.push({ role: 'user', content: message })
  chatStreaming.value = true
  chatStreamingText.value = ''
  currentToolCall.value = ''

  try {
    const response = await sendChatMessage(message)
    if (!response.ok) throw new Error('请求失败')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''
    let assistantText = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          try {
            const data = JSON.parse(line.slice(5))
            if (currentEvent === 'tool_call') {
              currentToolCall.value = data.tool || ''
            } else if (currentEvent === 'token') {
              currentToolCall.value = ''
              assistantText += data.text
              chatStreamingText.value = sanitizeAssistantText(assistantText)
            } else if (currentEvent === 'done') {
              currentToolCall.value = ''
              chatStreamingText.value = ''
              chatMessages.value.push({ role: 'assistant', content: sanitizeAssistantText(data.message) })
              if (data.draftChanged) {
                await loadDraftData()
              }
            } else if (currentEvent === 'error') {
              currentToolCall.value = ''
              chatStreamingText.value = ''
              ElMessage.error(data.message || '对话失败')
            }
          } catch (e) { /* skip */ }
        }
      }
    }
  } catch (e) {
    currentToolCall.value = ''
    chatStreamingText.value = ''
    ElMessage.error('对话失败：' + (e.message || '网络错误'))
  } finally {
    currentToolCall.value = ''
    chatStreaming.value = false
  }
}


async function handleGenerateReport() {
  const insufficientTiers = (draft.value?.tiers || [])
    .filter(t => t.insufficient)
    .map(t => t.label)

  if (insufficientTiers.length > 0) {
    try {
      await ElMessageBox.confirm(
        `${insufficientTiers.join('、')}候选不足，确定生成报告？`,
        '提示',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }

  try {
    const res = await generateReport()
    const reportId = res.data?.reportId
    if (reportId) {
      router.push(`/ai-report-v2/${reportId}`)
      ElMessage.success('报告已生成')
    }
  } catch (e) {
    ElMessage.error('生成报告失败：' + (e.response?.data?.msg || e.message))
  }
}

function revealTierCandidates(tierData) {
  if (!tierData?.level) return
  if (!draft.value) {
    draft.value = { tiers: [], removedCandidates: [], blockedCandidates: [] }
  }
  const frames = revealTierFrames(tierData)
  if (!frames.length) {
    draft.value = mergeTierRevealFrame(draft.value, tierData, 0)
    return
  }

  frames.forEach((visibleCount) => {
    const delay = revealFrameIndex * 420
    revealFrameIndex += 1
    const timer = window.setTimeout(() => {
      revealTimers.delete(timer)
      draft.value = mergeTierRevealFrame(draft.value, tierData, visibleCount)
      completeGenerationWhenRevealSettles()
    }, delay)
    revealTimers.add(timer)
  })
}

function clearRevealTimers() {
  revealTimers.forEach(timer => window.clearTimeout(timer))
  revealTimers.clear()
}

async function completeGenerationWhenRevealSettles() {
  if (!pendingFinalDraft.value || revealTimers.size > 0) return
  draft.value = pendingFinalDraft.value
  pendingFinalDraft.value = null
  generating.value = false
  stopProgress()
  applyProgress({ phase: 'done', message: '草稿生成完成' })
  ElMessage.success('草稿生成完成')
  flushGenerationBubblesNow()
  await loadChatHistory({ preserveGenerationStatus: true })
}

// ── 草稿恢复（刷新后自动轮询直到完整）──
let pollTimer = null

function isIncomplete(d) {
  if (!d || !d.tiers) return false
  return d.tiers.some(t => t.insufficient && t.insufficientReason && t.insufficientReason.includes('正在'))
}

function startDraftPolling() {
  if (!isIncomplete(draft.value)) return
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const res = await getDraft()
      draft.value = res.data || null
      if (!isIncomplete(draft.value)) {
        clearInterval(pollTimer)
        pollTimer = null
        if (draft.value) {
          ElMessage.success('草稿已恢复')
          loadChatHistory()
        }
      }
    } catch { /* 继续轮询 */ }
  }, 2000)
  setTimeout(() => {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
      ElMessage.warning('草稿恢复超时，请重新生成')
    }
  }, 60000)
}

// ── 初始化 ──
onMounted(async () => {
  await loadProfileData()
  await loadDraftData()
  await loadChatHistory()
  startDraftPolling()
})

onBeforeUnmount(() => {
  closeEventSource(draftEventSource)
  stopProgress()
  clearRevealTimers()
  resetGenerationBubbles()
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.ai-workspace {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(ellipse 60% 60% at 20% 0%, #e0ebff 0%, transparent 50%),
    radial-gradient(ellipse 50% 40% at 90% 10%, #f0e9ff 0%, transparent 50%),
    linear-gradient(180deg, #f5f8ff 0%, #eef4ff 320px, #f5f8ff 100%);
  color: var(--ink-1);
  overflow: hidden;
  position: relative;
}

.ai-workspace::before {
  /* 微妙点阵网格，丰富背景 */
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle, rgba(36, 78, 156, 0.06) 1px, transparent 1px);
  background-size: 24px 24px;
  pointer-events: none;
  opacity: 0.5;
  mask-image: linear-gradient(180deg, #000 0%, transparent 70%);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, transparent 70%);
}

.ai-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  max-width: 1480px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 40px 20px;
  gap: 20px;
  overflow: hidden;
}

.body-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 380px;
  gap: 18px;
}

.rail {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

/* ── Canvas — 视觉锚点，圆角略大，加深阴影 + 渐变头 ── */
.canvas {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--bg-elev);
  box-shadow: 0 10px 28px rgba(36, 78, 156, 0.08);
  overflow: hidden;
  position: relative;
}

.canvas::before {
  /* 极淡的网格背景，给聊天区一些肌理 */
  content: "";
  position: absolute;
  inset: 60px 0 0 0;
  background-image:
    linear-gradient(rgba(23, 105, 246, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(23, 105, 246, 0.025) 1px, transparent 1px);
  background-size: 24px 24px;
  pointer-events: none;
  opacity: 0.6;
  z-index: 0;
}

.canvas-head {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  background:
    linear-gradient(135deg, rgba(23, 105, 246, 0.04) 0%, rgba(155, 89, 255, 0.04) 100%),
    var(--bg-elev);
}

.canvas-head-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.canvas-orb {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--brand-gradient);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #fff;
  font-weight: 700;
  letter-spacing: 0;
  box-shadow: 0 6px 16px rgba(23, 105, 246, 0.3);
  position: relative;
}

.canvas-orb::after {
  content: "AI";
}

.canvas-orb.live {
  animation: orb-pulse 1.6s var(--ease) infinite;
}

@keyframes orb-pulse {
  0%, 100% { box-shadow: 0 6px 16px rgba(23, 105, 246, 0.3), 0 0 0 0 rgba(23, 105, 246, 0.4); }
  50% { box-shadow: 0 6px 16px rgba(23, 105, 246, 0.3), 0 0 0 10px rgba(23, 105, 246, 0); }
}

.canvas-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-1);
  letter-spacing: -0.01em;
}

.canvas-sub {
  margin: 1px 0 0;
  font-size: 11px;
  color: var(--ink-3);
}

.canvas-state {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-3);
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-elev);
  transition: all var(--t-base) var(--ease);
}

.state-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ink-4);
  transition: background var(--t-base) var(--ease);
}

.canvas-state.live {
  color: var(--brand);
  border-color: var(--brand-soft-2);
  background: var(--brand-soft);
}
.canvas-state.live .state-dot {
  background: var(--brand);
  animation: dot-blink 1.2s var(--ease) infinite;
}

@keyframes dot-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

.canvas-chat {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
}

.draft-col {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ── Responsive ── */
@media (max-width: 1280px) {
  .ai-main { padding: 20px 24px 16px; }
  .body-grid {
    grid-template-columns: 220px minmax(0, 1fr) 340px;
    gap: 14px;
  }
}

@media (max-width: 960px) {
  .ai-workspace { height: auto; min-height: 100vh; overflow: visible; }
  .ai-main { overflow: visible; }
  .body-grid {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }
  .rail { order: 1; overflow: visible; }
  .canvas { order: 2; height: 600px; }
  .draft-col { order: 3; }
}
</style>
