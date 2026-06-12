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
  addBackCandidate, generateReport, sendChatMessage, startChat
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
const chatVisible = ref(false)
const chatMessages = ref([])
const chatStreaming = ref(false)

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

// ── 方法 ──

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

function handleAskAbout(programId) {
  chatVisible.value = true
  // 预填充对话消息
  chatMessages.value.push({ role: 'user', content: `分析一下 ${programId}` })
  handleChatSend(`帮我分析一下这所学校为什么被推荐`)
}

async function handleChatSend(message) {
  chatVisible.value = true
  chatMessages.value.push({ role: 'user', content: message })
  chatStreaming.value = true

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
            } else if (currentEvent === 'done') {
              chatMessages.value.push({ role: 'assistant', content: data.message })
              if (data.draftAction && data.draftAction.type !== 'none') {
                executeDraftAction(data.draftAction)
              }
            } else if (currentEvent === 'error') {
              ElMessage.error(data.message || '对话失败')
            }
          } catch (e) { /* skip */ }
        }
      }
    }
  } catch (e) {
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
      router.push(`/ai-report/${reportId}`)
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
})

onBeforeUnmount(() => {
  closeEventSource(draftEventSource)
})
</script>

<style scoped>
.prototype-page {
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f7faff 0%, #f3f6fb 42%, #f6f8fc 100%);
  color: #10213f;
}

.v2-wrap {
  max-width: 1520px;
  margin: 0 auto;
  padding: 0 24px 24px;
}

/* ── Hero ── */
.hero {
  min-height: 128px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 22px 0 16px;
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
  display: grid;
  grid-template-columns: 300px minmax(560px, 1fr) 420px;
  gap: 20px;
  height: calc(100vh - 206px);
  min-height: 620px;
  align-items: stretch;
  overflow: hidden;
}
.left-col { display: flex; flex-direction: column; gap: 12px; }
.center-col,
.right-col {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.right-col { position: static; }

.chat-panel-frame {
  height: 100%;
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
  .main-grid { grid-template-columns: 280px minmax(520px, 1fr) 380px; gap: 16px; }
  .hero-status-strip { width: min(520px, 44vw); grid-template-columns: 112px 112px minmax(160px, 1fr); }
}
@media (max-width: 960px) {
  .hero { align-items: flex-start; flex-direction: column; }
  .hero-status-strip { width: 100%; grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .main-grid { grid-template-columns: 1fr; height: auto; overflow: visible; }
  .left-col { order: 1; }
  .right-col { order: 2; position: static; }
  .center-col { order: 3; }
}
</style>
