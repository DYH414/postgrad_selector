<template>
  <div class="prototype-page">
    <AppHeader current-page="ai" />

    <main class="v2-wrap">
      <!-- Hero -->
      <section class="hero">
        <div class="hero-copy">
          <span class="hero-kicker">AI 推荐</span>
          <h1>AI 择校工作台</h1>
          <p>基于画像一键生成冲刺、稳妥、保底草稿，调整确认后生成最终报告。</p>
        </div>
        <div class="hero-metrics">
          <div class="metric-card">
            <span>候选池</span>
            <strong>{{ poolCount }}</strong>
            <em>所</em>
          </div>
          <div class="metric-card">
            <span>草稿中</span>
            <strong>{{ draftCount }}</strong>
            <em>所</em>
          </div>
          <div class="metric-card">
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
          <AiChatMiniPanel
            :visible="chatVisible"
            :messages="chatMessages"
            :streaming="chatStreaming"
            @send="handleChatSend"
            @toggle="chatVisible = !chatVisible"
          />
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
import { ref, computed, onMounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDraft, generateDraft, removeCandidate, replaceCandidate,
  addBackCandidate, generateReport, sendChatMessage, startChat
} from '@/api/recommend-v2'
import { getProfile } from '@/api/profile'
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
  progressState.value = { phase: '', message: '正在准备...' }
  draft.value = null

  try {
    const response = await generateDraft()
    if (!response.ok) throw new Error('请求失败')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''

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
            if (currentEvent === 'progress') {
              progressState.value = { phase: data.phase, message: data.message }
            } else if (currentEvent === 'done') {
              draft.value = data.draft
              progressState.value = { phase: 'done', message: '草稿生成完成' }
              ElMessage.success('草稿生成完成')
            } else if (currentEvent === 'error') {
              ElMessage.error(data.message || '生成失败')
            }
          } catch (e) { /* skip malformed lines */ }
        }
      }
    }
  } catch (e) {
    ElMessage.error('生成草稿失败：' + (e.message || '网络错误'))
  } finally {
    generating.value = false
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
</script>

<style scoped>
.prototype-page {
  min-height: 100vh;
  background: #f4f7fc;
}

.v2-wrap {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 24px 40px;
}

/* ── Hero ── */
.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  padding: 28px 0 20px;
}
.hero-copy { flex: 1; }
.hero-kicker {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(64, 158, 255, .12);
  color: #409eff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .5px;
  margin-bottom: 8px;
}
.hero-copy h1 { margin: 0 0 6px; font-size: 24px; font-weight: 600; line-height: 32px; }
.hero-copy p { margin: 0; color: #71829a; font-size: 14px; }

.hero-metrics { display: flex; gap: 12px; flex-shrink: 0; }
.metric-card {
  padding: 14px 20px;
  border: 1px solid rgba(215,227,245,.9);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(42,84,153,.06);
  text-align: center;
  min-width: 100px;
}
.metric-card span { display: block; font-size: 12px; color: #71829a; }
.metric-card strong { display: block; font-size: 22px; font-weight: 700; color: #303133; margin: 2px 0; }
.metric-card em { display: block; font-size: 11px; color: #a8b2c1; font-style: normal; }

/* ── 三栏 ── */
.main-grid {
  display: grid;
  grid-template-columns: 260px 1fr 440px;
  gap: 16px;
  align-items: start;
}
.left-col { display: flex; flex-direction: column; gap: 12px; }
.center-col { min-height: 400px; }
.right-col { position: sticky; top: 16px; }

@media (max-width: 1200px) {
  .main-grid { grid-template-columns: 240px 1fr 380px; }
}
@media (max-width: 960px) {
  .main-grid { grid-template-columns: 1fr; }
  .left-col { order: 1; }
  .right-col { order: 2; position: static; }
  .center-col { order: 3; }
}
</style>
