<template>
  <div class="prototype-page">
    <AppHeader current-page="ai" />

    <main class="v2-wrap">
      <!-- Hero -->
      <section class="hero">
        <div class="hero-copy">
          <span class="hero-kicker">AI 推荐工作流</span>
          <h1>AI 择校工作台</h1>
          <p>从画像出发生成候选池，在对话中确认冲刺、稳妥、保底学校，最后沉淀成可交付报告。</p>
        </div>
        <div class="hero-status-strip">
          <div class="status-item primary">
            <span>候选池</span>
            <strong>{{ poolCount }}</strong>
            <em>所</em>
          </div>
          <div class="status-item">
            <span>草稿中</span>
            <strong>{{ draftCount }}</strong>
            <em>/ 10 所</em>
          </div>
          <div class="status-item strategy">
            <span>当前策略</span>
            <strong>{{ strategyLabel }}</strong>
            <em>冲稳保均衡</em>
          </div>
        </div>
      </section>

      <!-- 三栏网格 -->
      <section class="main-grid">
        <!-- 左栏 -->
        <aside class="left-col">
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

        <!-- 中栏 -->
        <section class="center-col">
          <div class="ai-workflow-strip">
            <div
              v-for="step in workflowSteps"
              :key="step.key"
              class="workflow-step"
              :class="{ active: step.active, done: step.done }"
            >
              <span class="workflow-index">{{ step.index }}</span>
              <div>
                <strong>{{ step.title }}</strong>
                <em>{{ step.desc }}</em>
              </div>
            </div>
          </div>
          <div class="chat-panel-frame">
            <div class="chat-frame-head">
              <div>
                <h2>候选分析与追问</h2>
                <p>AI 会解释候选来源、风险和纳入草稿的理由。</p>
              </div>
              <span :class="['chat-state', chatVisible || chatMessages.length ? 'active' : 'idle']">
                {{ chatVisible || chatMessages.length ? '可追问' : '未开始' }}
              </span>
            </div>
            <AiChatMiniPanel
              :visible="chatVisible"
              :messages="chatMessages"
              :streaming="chatStreaming"
              :streaming-text="chatStreamingText"
              @send="handleChatSend"
              @toggle="chatVisible = !chatVisible"
            />
          </div>
        </section>

        <!-- 右栏 -->
        <aside class="right-col">
          <DraftPanel
            :draft="draft"
            :loading="generating"
            :progress="progressState"
            @remove="handleRemove"
            @replace="handleReplace"
            @add-back="handleAddBack"
            @ask-about="handleAskAbout"
            @generate-report="handleGenerateReport"
          />
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDraft, startGenerateDraft, openDraftGenerationStream, removeCandidate, replaceCandidate,
  addBackCandidate, generateReport, sendChatMessage, startChat, resumeChat
} from '@/api/recommend-v2'
import { getProfile } from '@/api/profile'
import { closeEventSource } from '@/utils/event-source'
import AppHeader from '@/components/AppHeader.vue'
import ProfileSidebar from './components/ProfileSidebar.vue'
import GenerateDraftButton from './components/GenerateDraftButton.vue'
import AiChatMiniPanel from './components/AiChatMiniPanel.vue'
import DraftPanel from './components/DraftPanel.vue'

const router = useRouter()

// ── 画像 ──
const loadingProfile = ref(false)
const profile = ref({})
const missingFields = ref([])

// ── 草稿 ──
const draft = shallowRef(null)
const generating = ref(false)
const progressState = ref({ phase: '', message: '' })
const draftEventSource = ref(null)

// ── 对话 ──
const chatVisible = ref(true)
const chatMessages = ref([])
const chatStreaming = ref(false)
const chatStreamingText = ref('')

// ── 计算属性 ──
const poolCount = computed(() => {
  if (!draft.value?.tiers) return 0
  return draft.value.tiers.reduce((s, t) => s + (t.candidates?.length || 0), 0)
})

const draftCount = computed(() => poolCount.value)

const strategyLabel = computed(() => {
  const p = profile.value
  if (p?.riskPreference === 'safe_first') return '稳妥优先'
  if (p?.riskPreference === 'reach_first') return '冲刺优先'
  return '均衡策略'
})

const workflowSteps = computed(() => [
  {
    key: 'profile',
    index: '01',
    title: '画像读取',
    desc: missingFields.value.length ? `缺 ${missingFields.value.length} 项信息` : '画像可用',
    active: !draft.value && !generating.value,
    done: missingFields.value.length === 0
  },
  {
    key: 'pool',
    index: '02',
    title: '候选生成',
    desc: progressState.value.message || '按冲稳保形成候选池',
    active: generating.value,
    done: poolCount.value > 0
  },
  {
    key: 'chat',
    index: '03',
    title: '对话调整',
    desc: chatMessages.value.length ? '已进入追问调整' : '可解释和替换学校',
    active: chatVisible.value || chatMessages.value.length > 0,
    done: chatMessages.value.length > 0
  },
  {
    key: 'report',
    index: '04',
    title: '报告确认',
    desc: draftCount.value ? `${draftCount.value}/10 所待确认` : '等待草稿',
    active: draftCount.value > 0,
    done: draftCount.value >= 10
  }
])

// ── 方法 ──

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
    if (res.data?.tiers?.some(t => t.candidates?.length > 0)) {
      draft.value = res.data
    }
  } catch (e) { /* 草稿不存在 */ }
}

async function loadChatHistory() {
  try {
    const res = await resumeChat()
    const messages = Array.isArray(res.data?.messages) ? res.data.messages : []
    chatMessages.value = messages
      .map(normalizeChatMessage)
      .filter(Boolean)
  } catch (e) {
    chatMessages.value = []
  }
}

async function handleGenerate() {
  generating.value = true
  progressState.value = { phase: 'queued', message: '正在准备生成草稿...' }
  draft.value = null
  closeEventSource(draftEventSource)

  try {
    const res = await startGenerateDraft()
    const task = res.data || {}
    if (task.status === 'busy') {
      generating.value = false
      progressState.value = { phase: 'busy', message: task.message || '已有草稿正在生成' }
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
      progressState.value = {
        phase: data.phase || 'running',
        message: data.message || '正在生成草稿...'
      }
      // 逐档实时渲染：后端推送某档完整数据后立即展示，不等全部完成
      if (data.tierData) {
        if (!draft.value) {
          draft.value = { tiers: [], removedCandidates: [], blockedCandidates: [] }
        }
        const existingIdx = draft.value.tiers.findIndex(t => t.level === data.tierData.level)
        if (existingIdx >= 0) {
          draft.value.tiers[existingIdx] = data.tierData
        } else {
          draft.value.tiers.push(data.tierData)
        }
      }
    })

    source.addEventListener('done', event => {
      const data = JSON.parse(event.data)
      draft.value = data.draft
      progressState.value = { phase: 'done', message: '草稿生成完成' }
      generating.value = false
      closeEventSource(draftEventSource)
      ElMessage.success('草稿生成完成')
    })

    source.addEventListener('error', event => {
      let message = '生成草稿连接中断'
      if (event?.data) {
        try {
          message = JSON.parse(event.data).message || message
        } catch (_) {}
      }
      progressState.value = { phase: 'error', message }
      generating.value = false
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
    progressState.value = { phase: 'error', message: msg }
    generating.value = false
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
  // 用户只看到自然语言问题。AI 通过 getDraftContext 定位学校，按名调用 getProgramDetail。
  handleChatSend(`帮我分析一下 ${label} 为什么被推荐，有什么风险`)
}

async function handleChatSend(message) {
  chatVisible.value = true
  chatMessages.value.push({ role: 'user', content: message })
  chatStreaming.value = true
  chatStreamingText.value = ''

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
            if (currentEvent === 'token') {
              assistantText += data.text
              chatStreamingText.value = sanitizeAssistantText(assistantText)
            } else if (currentEvent === 'done') {
              chatStreamingText.value = ''
              chatMessages.value.push({ role: 'assistant', content: sanitizeAssistantText(data.message) })
              if (data.draftAction && data.draftAction.type !== 'none') {
                executeDraftAction(data.draftAction)
              }
            } else if (currentEvent === 'error') {
              chatStreamingText.value = ''
              ElMessage.error(data.message || '对话失败')
            }
          } catch (e) { /* skip */ }
        }
      }
    }
  } catch (e) {
    chatStreamingText.value = ''
    ElMessage.error('对话失败：' + (e.message || '网络错误'))
  } finally {
    chatStreaming.value = false
  }
}

function executeDraftAction(action) {
  if (action.type === 'remove' && action.programId) {
    handleRemove(action.programId)
  } else if (action.type === 'replace' && action.programId) {
    handleReplace({
      removeProgramId: action.programId,
      tier: action.tier || 'steady',
      preference: action.preference || 'safer'
    })
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

// ── 初始化 ──
onMounted(() => {
  loadProfileData()
  loadDraftData()
  loadChatHistory()
})

onBeforeUnmount(() => {
  closeEventSource(draftEventSource)
})
</script>

<style scoped>
.prototype-page {
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    linear-gradient(180deg, #f7faff 0%, #f3f6fb 42%, #f6f8fc 100%);
  color: #10213f;
}

:deep(.app-header) {
  flex-shrink: 0;
}

.v2-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  max-width: 1520px;
  width: 100%;
  margin: 0 auto;
  padding: 0 24px 16px;
  overflow: hidden;
}

/* ── Hero ── */
.hero {
  flex-shrink: 0;
  min-height: 118px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 18px 0 14px;
}
.hero-copy { flex: 1; }
.hero-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eaf2ff;
  color: #1769f6;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
  margin-bottom: 8px;
}
.hero-copy h1 { margin: 0 0 8px; font-size: 30px; font-weight: 800; line-height: 36px; letter-spacing: 0; }
.hero-copy p { max-width: 640px; margin: 0; color: #5d6f89; font-size: 15px; line-height: 1.65; }

.hero-status-strip {
  display: grid;
  grid-template-columns: 130px 130px minmax(180px, 1fr);
  align-items: center;
  flex-shrink: 0;
  width: min(560px, 44vw);
  min-height: 72px;
  padding: 10px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: rgba(255,255,255,.9);
  box-shadow: 0 12px 28px rgba(39,86,166,.08);
}
.status-item {
  min-width: 0;
  min-height: 52px;
  padding: 6px 16px;
  border-right: 1px solid #edf2f9;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.status-item:last-child { border-right: 0; }
.status-item span { display: block; color: #6d7f99; font-size: 12px; font-weight: 800; line-height: 16px; }
.status-item strong { display: block; margin-top: 4px; color: #10213f; font-size: 22px; line-height: 26px; font-weight: 900; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.status-item em { display: block; margin-top: 1px; color: #8a9ab2; font-size: 12px; line-height: 16px; font-style: normal; font-weight: 800; }
.status-item.primary strong { color: #1769f6; }
.status-item.strategy strong { font-size: 17px; }

/* ── 三栏 ── */
.main-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 300px minmax(560px, 1fr) 480px;
  gap: 20px;
  height: auto;
  min-height: 0;
  align-items: stretch;
  overflow: hidden;
}
.left-col {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}
.left-col :deep(.profile-card) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.left-col :deep(.generate-section) {
  flex-shrink: 0;
}
.center-col,
.right-col {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.center-col {
  display: flex;
  flex-direction: column;
}
.right-col { position: static; }

.ai-workflow-strip {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.workflow-step {
  min-width: 0;
  min-height: 64px;
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 9px 10px;
  border: 1px solid #dce7f6;
  border-radius: 8px;
  background: rgba(255, 255, 255, .86);
  transition: border-color .18s ease, background .18s ease;
}

.workflow-step.active {
  border-color: #9dc4ff;
  background: #f4f8ff;
}

.workflow-step.done .workflow-index {
  border-color: #9ee3c0;
  background: #ecfdf5;
  color: #087443;
}

.workflow-index {
  flex: none;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #d7e6fb;
  border-radius: 50%;
  color: #1769f6;
  background: #fff;
  font-size: 11px;
  font-weight: 900;
}

.workflow-step strong,
.workflow-step em {
  display: block;
  min-width: 0;
}

.workflow-step strong {
  color: #10213f;
  font-size: 13px;
  line-height: 18px;
}

.workflow-step em {
  margin-top: 3px;
  color: #71829a;
  font-size: 12px;
  line-height: 17px;
  font-style: normal;
}

.chat-panel-frame {
  flex: 1;
  height: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39,86,166,.07);
  overflow: hidden;
}

.chat-frame-head {
  min-height: 62px;
  padding: 12px 16px;
  border-bottom: 1px solid #edf2f9;
  background: #fbfdff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.chat-frame-head h2 { margin: 0; color: #10213f; font-size: 17px; line-height: 23px; }
.chat-frame-head p { margin: 3px 0 0; color: #6d7f99; font-size: 13px; line-height: 18px; }
.chat-state {
  flex-shrink: 0;
  min-height: 24px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 800;
}
.chat-state.active { color: #0b6b43; background: #e8f8f0; }
.chat-state.idle { color: #607592; background: #eef4fb; }

@media (max-width: 1200px) {
  .main-grid { grid-template-columns: 280px minmax(520px, 1fr) 430px; gap: 16px; }
  .hero-status-strip { width: min(520px, 44vw); grid-template-columns: 112px 112px minmax(160px, 1fr); }
  .ai-workflow-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 960px) {
  .prototype-page { height: auto; min-height: 100vh; overflow: visible; }
  .v2-wrap { overflow: visible; }
  .hero { align-items: flex-start; flex-direction: column; }
  .hero-status-strip { width: 100%; grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .main-grid { grid-template-columns: 1fr; height: auto; overflow: visible; }
  .left-col { order: 1; }
  .right-col { order: 2; position: static; }
  .center-col { order: 3; }
}
</style>
