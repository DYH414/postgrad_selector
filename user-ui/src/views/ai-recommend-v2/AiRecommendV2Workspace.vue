<template>
  <div class="ai-v2-workspace">
    <AppHeader current-page="ai" />

    <main class="workspace-shell">
      <!-- 顶部 Hero -->
      <section class="workspace-hero">
        <div class="hero-copy">
          <p class="hero-kicker">AI 推荐</p>
          <h1>AI 择校工作台</h1>
          <p>基于你的画像一键生成冲刺、稳妥、保底推荐草稿，调整后生成最终报告。</p>
        </div>
        <div class="hero-stats">
          <div class="stat-card">
            <small>候选池</small>
            <strong>{{ poolCount }} 所</strong>
          </div>
          <div class="stat-card">
            <small>草稿中</small>
            <strong>{{ draftCount }} 所</strong>
          </div>
          <div class="stat-card">
            <small>当前策略</small>
            <strong>{{ currentStrategy }}</strong>
          </div>
        </div>
      </section>

      <!-- 三栏布局 -->
      <section class="workspace-grid">
        <!-- 左栏：画像 -->
        <aside class="workspace-sidebar">
          <ProfileSummaryCard
            :profile="profile"
            :loading="loadingProfile"
            @edit-profile="goToProfile"
          />
          <GenerateDraftButton
            :generating="generating"
            :progress="progressState"
            @generate="handleGenerate"
          />
        </aside>

        <!-- 中栏：对话（可收起） -->
        <section class="workspace-main">
          <AiChatMiniPanel
            :visible="chatVisible"
            :messages="chatMessages"
            :streaming="chatStreaming"
            @send="handleChatSend"
            @toggle="chatVisible = !chatVisible"
          />
        </section>

        <!-- 右栏：草稿面板 -->
        <aside class="workspace-candidates">
          <DraftPanel
            :draft="draft"
            :loading="generating"
            @remove="handleRemove"
            @replace="handleReplace"
            @add-back="handleAddBack"
            @generate-report="handleGenerateReport"
          />
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getDraft, generateDraft, removeCandidate, replaceCandidate,
  addBackCandidate, generateReport, sendChatMessage, startChat
} from '@/api/recommend-v2'
import { getProfile } from '@/api/profile'
import AppHeader from '@/components/AppHeader.vue'
import ProfileSummaryCard from './components/ProfileSummaryCard.vue'
import GenerateDraftButton from './components/GenerateDraftButton.vue'
import AiChatMiniPanel from './components/AiChatMiniPanel.vue'
import DraftPanel from './components/DraftPanel.vue'

const router = useRouter()

// ── 画像 ──
const loadingProfile = ref(false)
const profile = ref({})

// ── 草稿 ──
const draft = ref(null)
const generating = ref(false)
const progressState = ref({ phase: '', message: '' })

// ── 对话 ──
const chatVisible = ref(false)
const chatMessages = ref([])
const chatStreaming = ref(false)

// ── 计算属性 ──
const poolCount = computed(() => {
  // TODO: 从 draft.profileBasis 或单独 API 获取候选池大小
  return draft.value?.profileBasis?.candidateCount || 0
})

const draftCount = computed(() => {
  if (!draft.value?.tiers) return 0
  return draft.value.tiers.reduce((sum, t) => sum + (t.candidates?.length || 0), 0)
})

const currentStrategy = computed(() => {
  // TODO: 从画像计算策略标签
  return '冲稳保均衡'
})

// ── 方法 ──

async function loadDraft() {
  try {
    const res = await getDraft()
    draft.value = res.data
  } catch (e) {
    // 草稿不存在时忽略
  }
}

async function loadProfile() {
  loadingProfile.value = true
  try {
    const res = await getProfile()
    profile.value = res.data || {}
  } finally {
    loadingProfile.value = false
  }
}

function goToProfile() {
  router.push('/profile')
}

async function handleGenerate() {
  generating.value = true
  progressState.value = { phase: '', message: '正在准备...' }

  try {
    const response = await generateDraft()
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop()

      for (const line of lines) {
        if (line.startsWith('event: ')) {
          currentEvent = line.slice(7).trim()
        } else if (line.startsWith('data: ')) {
          const data = JSON.parse(line.slice(6))
          handleSseEvent(currentEvent, data)
        }
      }
    }
  } catch (e) {
    ElMessage.error('生成草稿失败：' + (e.message || '网络错误'))
  } finally {
    generating.value = false
    progressState.value = { phase: '', message: '' }
  }
}

function handleSseEvent(event, data) {
  switch (event) {
    case 'progress':
      progressState.value = { phase: data.phase, message: data.message }
      break
    case 'done':
      draft.value = data.draft
      // TODO: 更新 profileBasis
      ElMessage.success('草稿生成完成')
      break
    case 'error':
      ElMessage.error(data.message || '生成失败')
      break
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
    const res = await replaceCandidate(removeProgramId, tier, preference)
    draft.value = res.data.draft
    ElMessage.success('已替换')
  } catch (e) {
    ElMessage.error('替换失败')
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

async function handleGenerateReport() {
  try {
    const res = await generateReport()
    const reportId = res.data?.reportId
    if (reportId) {
      router.push(`/ai-recommend-v2/report/${reportId}`)
    }
  } catch (e) {
    ElMessage.error('生成报告失败')
  }
}

async function handleChatSend(message) {
  chatMessages.value.push({ role: 'user', content: message })
  chatStreaming.value = true

  try {
    const response = await sendChatMessage(message)
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
      buffer = lines.pop()

      for (const line of lines) {
        if (line.startsWith('event: ')) {
          currentEvent = line.slice(7).trim()
        } else if (line.startsWith('data: ')) {
          const data = JSON.parse(line.slice(6))
          if (currentEvent === 'token') {
            assistantText += data.text
            // TODO: 更新最后一条消息（流式渲染）
          } else if (currentEvent === 'done') {
            chatMessages.value.push({ role: 'assistant', content: data.message })
            // 处理 draftAction
            if (data.draftAction && data.draftAction.type !== 'none') {
              handleDraftAction(data.draftAction)
            }
          }
        }
      }
    }
  } catch (e) {
    ElMessage.error('对话失败：' + (e.message || '网络错误'))
  } finally {
    chatStreaming.value = false
  }
}

function handleDraftAction(action) {
  // TODO: 根据 action.type 执行对应操作
  // remove → handleRemove(action.programId)
  // replace → handleReplace({...})
  // analyze → 仅展示消息
}

// ── 初始化 ──
onMounted(() => {
  loadProfile()
  loadDraft()
})
</script>

<style scoped>
.ai-v2-workspace {
  min-height: 100vh;
  background: var(--bg-page);
}
.workspace-shell {
  padding: 24px;
}
.workspace-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.workspace-grid {
  display: grid;
  grid-template-columns: 280px 1fr 420px;
  gap: 16px;
  min-height: calc(100vh - 200px);
}
</style>
