<template>
  <div class="ai-workspace-page">
    <AppHeader current-page="ai" />

    <main class="workspace-shell">
      <section class="workspace-hero">
        <div class="hero-copy">
          <p class="hero-kicker">AI 推荐工作流</p>
          <h1>AI 择校工作台</h1>
          <p>从画像出发生成候选池，在对话中确认冲刺、稳妥、保底学校，最后沉淀成可交付报告。</p>
        </div>
        <div class="hero-status-strip">
          <div class="status-item primary">
            <span>候选池</span>
            <strong>{{ candidateCount }}<small>所</small></strong>
          </div>
          <div class="status-item">
            <span>草稿中</span>
            <strong>{{ reportCount }}<small>/ 10 所</small></strong>
          </div>
          <div class="status-item strategy">
            <span>当前策略</span>
            <strong>{{ currentStrategy }}</strong>
          </div>
          <div class="status-action">
            <el-button type="primary" size="small" plain @click="handleNewConversation">新对话</el-button>
          </div>
        </div>
      </section>

      <section class="workspace-grid ai-workspace-main">
        <aside class="workspace-sidebar profile-column">
          <div class="profile-scroll">
            <ProfileSummaryCard
              :profile="profile"
              :loading="loadingProfile"
              :missing-fields="missingFields"
              :target-regions-label="targetRegionsLabel"
              :risk-preference-labels="riskPreferenceLabels"
              :school-tier-preference-labels="schoolTierPreferenceLabels"
              :region-strategy-labels="regionStrategyLabels"
              :tier-label="tierLabel"
              :can-start="canStart"
              :starting="starting"
              @edit-profile="router.push('/profile')"
              @start-ai="startAi"
            />
            <RecommendationLogicCard />
          </div>
        </aside>

        <section class="workspace-main chat-column">
          <div class="chat-scroll">
            <div class="chat-panel-frame">
              <div class="chat-frame-head">
                <div>
                  <h2>候选分析与追问</h2>
                  <p>AI 会解释候选来源、风险和纳入草稿的理由。</p>
                </div>
                <span :class="['chat-state', panelOpen ? 'active' : 'idle']">
                  {{ panelOpen ? '分析中' : '未开始' }}
                </span>
              </div>
              <AiChatPanel
                ref="chatPanelRef"
                :visible="panelOpen"
                :candidateIds="[]"
                @close="panelOpen = false"
                @fallback="handleFallback"
                @conversation-started="handleConversationStarted"
                @bookmarks-updated="refreshBookmarks"
              />
            </div>
          </div>
        </section>

        <aside class="workspace-candidates candidate-column">
          <div class="candidate-scroll">
            <ReportCandidatePanel
              :active="panelOpen"
              :analyzing="starting || bookmarkPolling"
              :bookmarks="bookmarks"
              :bookmark-groups="bookmarkGroups"
              :summary="bookmarkSummary"
              @remove="removeBookmark"
              @ask-about="askAboutBookmark"
              @generate="generateReport"
            />
          </div>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { deleteBookmark as deleteAiBookmark, getBookmarks, postAiAnalyze, postAiGenerateReport } from '@/api/ai'
import { getProfile } from '@/api/profile'
import AppHeader from '@/components/AppHeader.vue'
import AiChatPanel from '@/components/AiChatPanel.vue'
import ProfileSummaryCard from './components/ProfileSummaryCard.vue'
import RecommendationLogicCard from './components/RecommendationLogicCard.vue'
import ReportCandidatePanel from './components/ReportCandidatePanel.vue'
import { judgementKey } from './utils/display'

const router = useRouter()
const AI_RECENT_CONVERSATION_KEY = 'ai_recent_conversation_id'
const loadingProfile = ref(false)
const analyzing = ref(false)
const starting = ref(false)
const panelOpen = ref(false)
const chatPanelRef = ref(null)
const conversationId = ref(null)
const bookmarks = ref([])
const bookmarkPolling = ref(false)
let bookmarkPollTimer = null
const profile = reactive({
  estimatedScore: null,
  targetRegions: [],
  acceptAcademic: false,
  undergradTier: '',
  undergraduateMajor: '',
  isCrossMajor: false,
  riskPreference: 'balanced',
  schoolTierPreference: 'no_strict_requirement',
  regionStrategy: 'no_strict_requirement'
})

const riskPreferenceLabels = {
  conservative: '只要有学上，稳妥优先',
  balanced: '稳中求进，冲稳保均衡',
  aggressive: '愿意冲更好的学校'
}

const schoolTierPreferenceLabels = {
  must_211_or_better: '高层次院校优先，愿意承担风险',
  prefer_211_or_better: '优先 211/双一流，但不能太冒险',
  no_strict_requirement: '不强求层次，有学上更重要'
}

const regionStrategyLabels = {
  developed_priority: '发达地区优先，愿意承担风险',
  developed_balanced: '优先发达地区，但不能太冒险',
  no_strict_requirement: '地区不强求，有学上更重要',
  target_regions_only: '只看我填写的目标省份'
}

const targetRegionsLabel = computed(() => {
  return profile.targetRegions && profile.targetRegions.length ? profile.targetRegions.join('、') : '不限'
})

const missingFields = computed(() => {
  const fields = []
  if (!profile.estimatedScore) fields.push('预计初试总分')
  if (!profile.undergradTier) fields.push('本科层次')
  if (!profile.undergraduateMajor) fields.push('本科专业')
  return fields
})

const canStart = computed(() => !!profile.estimatedScore)
const currentStrategy = computed(() => riskPreferenceLabels[profile.riskPreference] || '稳中求进')
const candidateCount = computed(() => bookmarks.value.length || (panelOpen.value ? 50 : 0))
const reportCount = computed(() => bookmarks.value.length)
const bookmarkSummary = computed(() => {
  const confirmed = bookmarks.value.filter(item => item.userConfirmed || item.status === 'confirmed').length
  const conversation = bookmarks.value.filter(item => item.source === 'conversation_ai').length
  const background = bookmarks.value.filter(item => item.source === 'background_ai').length
  return { confirmed, conversation, background }
})
const bookmarkGroups = computed(() => {
  const groups = [
    { key: 'reach', label: '冲刺', tone: 'reach', items: [] },
    { key: 'steady', label: '稳妥', tone: 'steady', items: [] },
    { key: 'safe', label: '保底', tone: 'safe', items: [] }
  ]
  const fallbackGroup = groups[1]
  bookmarks.value.forEach(item => {
    const target = groups.find(group => group.key === judgementKey(item)) || fallbackGroup
    target.items.push(item)
  })
  return groups.filter(group => group.items.length > 0)
})

function tierLabel(tier) {
  const map = {
    '985': '985',
    '211': '211',
    DOUBLE_FIRST: '双一流',
    PUBLIC_REGULAR: '普通一本',
    PRIVATE: '民办',
    INDEPENDENT: '独立学院',
    RESEARCH_INSTITUTE: '科研院所',
    OTHER: '其他'
  }
  return map[tier] || tier || '-'
}

function normalizeRegions(raw) {
  if (Array.isArray(raw)) return raw
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return String(raw).split(/[、，,]/).map(item => item.trim()).filter(Boolean)
  }
}

function loadProfile() {
  loadingProfile.value = true
  getProfile().then(res => {
    const data = res.data || {}
    profile.estimatedScore = data.estimatedScore || null
    profile.targetRegions = normalizeRegions(data.targetRegions)
    profile.acceptAcademic = data.acceptAcademic === 1 || data.acceptAcademic === true
    profile.undergradTier = data.undergradTier || ''
    profile.undergraduateMajor = data.undergraduateMajor || ''
    profile.isCrossMajor = data.isCrossMajor === 1 || data.isCrossMajor === true
    profile.riskPreference = data.riskPreference || 'balanced'
    profile.schoolTierPreference = data.schoolTierPreference || 'no_strict_requirement'
    profile.regionStrategy = data.regionStrategy || 'no_strict_requirement'
  }).finally(() => {
    loadingProfile.value = false
  })
}

function startAi() {
  if (!canStart.value) {
    ElMessage.warning('请先填写预计初试总分，AI 才能判断分数区间')
    return
  }
  starting.value = true
  panelOpen.value = true
  setTimeout(() => {
    starting.value = false
  }, 300)
}

function handleConversationStarted(id) {
  conversationId.value = id
  startBookmarkPolling()
}

function restoreConversationContext() {
  try {
    const storedId = localStorage.getItem(AI_RECENT_CONVERSATION_KEY)
    if (!storedId) return
    conversationId.value = storedId
    panelOpen.value = true
    refreshBookmarks(storedId)
    startBookmarkPolling()
  } catch (_) {}
}

async function refreshBookmarks(id = conversationId.value) {
  if (!id) return
  conversationId.value = id
  try {
    const res = await getBookmarks(id)
    bookmarks.value = (res.data && res.data.bookmarks) || []
  } catch (_) {}
}

function startBookmarkPolling() {
  bookmarkPolling.value = true
  if (bookmarkPollTimer) {
    clearTimeout(bookmarkPollTimer)
    bookmarkPollTimer = null
  }
  let attempts = 0
  const maxAttempts = 10
  function poll() {
    if (!conversationId.value || attempts >= maxAttempts) {
      bookmarkPolling.value = false
      return
    }
    attempts++
    getBookmarks(conversationId.value).then(res => {
      const list = (res.data && res.data.bookmarks) || []
      bookmarks.value = list
      if (list.length > 0) {
        bookmarkPolling.value = false
      } else if (attempts < maxAttempts) {
        bookmarkPollTimer = setTimeout(poll, 3000)
      } else {
        bookmarkPolling.value = false
      }
    }).catch(() => {
      if (attempts < maxAttempts) {
        bookmarkPollTimer = setTimeout(poll, 3000)
      } else {
        bookmarkPolling.value = false
      }
    })
  }
  poll()
}

function startAnalyze() {
  if (!canStart.value) {
    ElMessage.warning('请先填写预计初试总分，AI 才能判断分数区间')
    return
  }
  analyzing.value = true
  postAiAnalyze().then(res => {
    const data = res.data || {}
    if (data.reportId) {
      router.push('/ai-report/' + data.reportId)
    } else {
      ElMessage.error('启动推荐失败')
    }
  }).catch(() => {
    ElMessage.error('快速推荐启动失败，请稍后重试')
  }).finally(() => {
    analyzing.value = false
  })
}

async function removeBookmark(bookmark) {
  if (!conversationId.value || !bookmark?.programId) return
  try {
    await deleteAiBookmark(conversationId.value, bookmark.programId)
    bookmarks.value = bookmarks.value.filter(item => item.programId !== bookmark.programId)
    refreshBookmarks()
  } catch (_) {
    ElMessage.error('删除候选失败')
  }
}

function askAboutBookmark(bookmark) {
  if (!panelOpen.value) {
    startAi()
    return
  }
  chatPanelRef.value?.askAboutBookmark?.(bookmark)
}

function trackPendingReport(reportId) {
  try {
    const pending = JSON.parse(sessionStorage.getItem('pending_reports') || '[]')
    pending.push({ id: reportId, ts: Date.now() })
    sessionStorage.setItem('pending_reports', JSON.stringify(pending.slice(-5)))
  } catch (_) {}
}

async function generateReport() {
  if (!conversationId.value) {
    startAi()
    return
  }
  try {
    const res = await postAiGenerateReport({ conversationId: conversationId.value })
    const reportId = res.data.reportId
    trackPendingReport(reportId)
    router.push({ name: 'AiReport', params: { id: reportId } })
  } catch (_) {
    ElMessage.error('生成报告失败')
  }
}

function handleNewConversation() {
  // 清除 localStorage 中的旧对话状态
  const storedId = localStorage.getItem(AI_RECENT_CONVERSATION_KEY)
  if (storedId) {
    localStorage.removeItem('ai_conv_' + storedId)
  }
  localStorage.removeItem(AI_RECENT_CONVERSATION_KEY)
  // 重置工作台状态
  conversationId.value = null
  bookmarks.value = []
  panelOpen.value = true
  // 清除轮询定时器
  if (bookmarkPollTimer) {
    clearTimeout(bookmarkPollTimer)
    bookmarkPollTimer = null
  }
  bookmarkPolling.value = false
  // 触发 AiChatPanel 重置并开始新对话
  chatPanelRef.value?.resetAndStart()
}

function handleFallback() {
  panelOpen.value = false
  ElMessage.warning('AI 对话暂不可用，请稍后再试')
}

onMounted(() => {
  loadProfile()
  restoreConversationContext()
})

onBeforeUnmount(() => {
  if (bookmarkPollTimer) {
    clearTimeout(bookmarkPollTimer)
  }
})
</script>

<style scoped>
.ai-workspace-page {
  min-height: calc(100vh - 64px);
  background:
    linear-gradient(180deg, #f7faff 0%, #f3f6fb 42%, #f6f8fc 100%);
  color: #10213f;
  overflow: hidden;
}

.workspace-shell {
  width: 100%;
  margin: 0;
  padding: 16px 0 0;
  overflow: hidden;
}

.workspace-hero {
  min-height: 116px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  max-width: 1520px;
  margin: 0 auto 16px;
  padding: 0 24px;
}

.hero-copy {
  min-width: 0;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  margin: 0 0 8px;
  padding: 0 10px;
  border-radius: 999px;
  color: #1769f6;
  background: #eaf2ff;
  font-weight: 800;
  font-size: 13px;
  letter-spacing: 0;
}

.hero-copy h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
  letter-spacing: 0;
}

.hero-copy p:last-child {
  max-width: 640px;
  margin: 8px 0 0;
  color: #5d6f89;
  font-size: 15px;
  line-height: 1.65;
}

.hero-status-strip {
  display: grid;
  grid-template-columns: 130px 130px minmax(180px, 1fr) 92px;
  align-items: center;
  gap: 0;
  width: min(680px, 50vw);
  min-height: 72px;
  padding: 10px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: rgba(255, 255, 255, .9);
  box-shadow: 0 12px 28px rgba(39, 86, 166, .08);
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

.status-item span {
  display: block;
  color: #6d7f99;
  font-size: 12px;
  font-weight: 800;
  line-height: 16px;
}

.status-item strong {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 4px;
  color: #10213f;
  font-size: 22px;
  line-height: 26px;
  font-weight: 900;
  min-width: 0;
}

.status-item strong small {
  color: #8a9ab2;
  font-size: 12px;
  line-height: 16px;
  font-weight: 800;
}

.status-item.primary strong {
  color: #1769f6;
}

.status-item.strategy strong {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 17px;
}

.status-action {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 12px;
}

.status-action :deep(.el-button) {
  min-width: 72px;
  border-radius: 7px;
  font-weight: 800;
}

.workspace-grid {
  height: calc(100vh - 202px);
  display: grid;
  grid-template-columns: 300px minmax(560px, 1fr) 380px;
  gap: 20px;
  max-width: 1520px;
  margin: 0 auto;
  padding: 0 24px 24px;
  overflow: hidden;
}

.workspace-sidebar,
.workspace-candidates {
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.workspace-main {
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.profile-scroll,
.chat-scroll,
.candidate-scroll {
  height: 100%;
  overflow-y: auto;
  scrollbar-width: thin;
}

.profile-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-scroll,
.candidate-scroll {
  overflow: hidden;
}

.chat-panel-frame {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39, 86, 166, .07);
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

.chat-frame-head h2 {
  margin: 0;
  color: #10213f;
  font-size: 17px;
  line-height: 23px;
}

.chat-frame-head p {
  margin: 3px 0 0;
  color: #6d7f99;
  font-size: 13px;
  line-height: 18px;
}

.chat-state {
  flex-shrink: 0;
  min-height: 24px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 800;
}

.chat-state.active {
  color: #0b6b43;
  background: #e8f8f0;
}

.chat-state.idle {
  color: #607592;
  background: #eef4fb;
}

.chat-panel-frame :deep(.ai-chat-panel) {
  flex: 1;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  background: #f7faff;
}

@media (max-width: 1280px) {
  .workspace-grid {
    grid-template-columns: 280px minmax(520px, 1fr) 350px;
    gap: 16px;
  }

  .hero-status-strip {
    width: min(620px, 50vw);
    grid-template-columns: 112px 112px minmax(160px, 1fr) 86px;
  }
}

@media (max-width: 920px) {
  .ai-workspace-page {
    overflow: auto;
  }

  .workspace-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-status-strip,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .hero-status-strip {
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-grid {
    height: auto;
    overflow: visible;
  }

  .profile-scroll,
  .chat-scroll,
  .candidate-scroll {
    height: auto;
    overflow: visible;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .status-item {
    border-right: 0;
    border-bottom: 1px solid #edf2f9;
  }

  .status-action {
    justify-content: flex-start;
    padding: 10px 16px 6px;
  }
}
</style>
