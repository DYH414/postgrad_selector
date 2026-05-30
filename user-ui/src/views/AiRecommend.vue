<template>
  <div class="ai-recommend-page">
    <AppHeader current-page="ai" />

    <main class="ai-shell">
      <section class="intro-band">
        <div>
          <p class="eyebrow">独立 AI 择校</p>
          <h1>AI 会基于你的考研画像推荐学校</h1>
          <p class="intro-copy">
            这里不会读取筛选页或对比页的临时条件。AI 的初始判断来自你的预计分数、目标地区、本科背景、跨考情况和系统自动候选池。
          </p>
        </div>
        <div class="intro-actions">
          <el-button type="primary" size="large" :disabled="!canStart || analyzing" :loading="analyzing" @click="startAnalyze">
            快速推荐
          </el-button>
          <el-button size="large" :disabled="!canStart || panelOpen" :loading="starting" @click="startAi">
            AI 对话推荐
          </el-button>
          <el-button size="large" @click="router.push('/profile')">
            编辑画像
          </el-button>
        </div>
        <div class="intro-modes">
          <span class="mode-hint fast">快速推荐：基于画像+录取数据，一键生成完整报告（约10-30秒）</span>
          <span class="mode-hint chat">AI 对话推荐：与AI多轮交流，逐步细化需求和偏好</span>
        </div>
      </section>

      <section class="profile-section">
        <div class="section-title">
          <h2>AI 推荐依据</h2>
          <span v-if="missingFields.length === 0" class="status good">画像完整</span>
          <span v-else class="status warn">缺少 {{ missingFields.length }} 项</span>
        </div>

        <div v-loading="loadingProfile" class="basis-grid">
          <div class="basis-item strong">
            <small>预计初试总分</small>
            <strong>{{ profile.estimatedScore || '-' }}</strong>
          </div>
          <div class="basis-item">
            <small>目标地区</small>
            <strong>{{ targetRegionsLabel }}</strong>
          </div>
          <div class="basis-item">
            <small>本科层次</small>
            <strong>{{ tierLabel(profile.undergradTier) }}</strong>
          </div>
          <div class="basis-item">
            <small>本科专业</small>
            <strong>{{ profile.undergraduateMajor || '-' }}</strong>
          </div>
          <div class="basis-item">
            <small>跨考情况</small>
            <strong>{{ profile.isCrossMajor ? '跨考' : '非跨考' }}</strong>
          </div>
          <div class="basis-item">
            <small>学位偏好</small>
            <strong>{{ profile.acceptAcademic ? '接受学硕' : '偏专硕' }}</strong>
          </div>
        </div>

        <div v-if="missingFields.length" class="missing-box">
          <strong>建议先完善画像：</strong>
          <span>{{ missingFields.join('、') }}</span>
          <el-button type="primary" link @click="router.push('/profile')">去完善</el-button>
        </div>
      </section>

      <section class="explain-section">
        <h2>AI 会怎么推荐</h2>
        <div class="explain-grid">
          <div>
            <strong>先看画像</strong>
            <p>用预计分、地区、本科背景和跨考情况确定初始范围。</p>
          </div>
          <div>
            <strong>再查硬数据</strong>
            <p>优先使用有复试线、拟录取分和招生计划的数据，避免无依据推荐。</p>
          </div>
          <div>
            <strong>最后给建议</strong>
            <p>输出冲刺、稳妥、保底，并解释为什么适合或不适合。</p>
          </div>
        </div>
      </section>
    </main>

    <AiChatPanel :visible="panelOpen" :candidateIds="[]" @close="panelOpen = false" @fallback="handleFallback" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { postAiAnalyze } from '@/api/ai'
import AppHeader from '@/components/AppHeader.vue'
import AiChatPanel from '@/components/AiChatPanel.vue'
import { getProfile } from '@/api/profile'

const router = useRouter()
const loadingProfile = ref(false)
const analyzing = ref(false)
const starting = ref(false)
const panelOpen = ref(false)
const profile = reactive({
  estimatedScore: null,
  targetRegions: [],
  acceptAcademic: false,
  undergradTier: '',
  undergraduateMajor: '',
  isCrossMajor: false
})

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

function tierLabel(tier) {
  const map = { '985': '985', '211': '211', double_first: '双一流', ordinary: '双非' }
  return map[tier] || tier || '-'
}

function normalizeRegions(raw) {
  if (Array.isArray(raw)) return raw
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return String(raw).split(/[、,，]/).map(item => item.trim()).filter(Boolean)
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

function handleFallback() {
  panelOpen.value = false
  ElMessage.warning('AI 对话暂不可用，请稍后再试')
}

onMounted(loadProfile)
</script>

<style scoped>
.ai-recommend-page { min-height: 100vh; background: #f4f7fb; color: #172033; }
.ai-shell { max-width: 1180px; margin: 0 auto; padding: 28px 20px 48px; }
.intro-band {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 36px;
  background: #ffffff;
  border: 1px solid #e4ebf5;
  border-radius: 8px;
  box-shadow: 0 14px 36px rgba(31, 64, 122, 0.08);
}
.eyebrow { margin: 0 0 8px; color: #2f6fef; font-weight: 700; }
.intro-band h1 { margin: 0; font-size: 34px; line-height: 1.2; letter-spacing: 0; }
.intro-copy { max-width: 680px; margin: 14px 0 0; color: #5b6b82; line-height: 1.8; }
.intro-actions { display: flex; gap: 12px; flex-wrap: wrap; justify-content: flex-end; }
.profile-section,
.explain-section {
  margin-top: 20px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e4ebf5;
  border-radius: 8px;
}
.section-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.section-title h2,
.explain-section h2 { margin: 0; font-size: 20px; }
.status { padding: 5px 10px; border-radius: 999px; font-size: 13px; font-weight: 700; }
.status.good { color: #137a4b; background: #e8f8ef; }
.status.warn { color: #a45b00; background: #fff4dc; }
.basis-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; min-height: 120px; }
.basis-item { padding: 16px; border: 1px solid #edf1f7; border-radius: 8px; background: #fbfcff; }
.basis-item small { display: block; color: #75859b; margin-bottom: 8px; }
.basis-item strong { display: block; font-size: 18px; color: #172033; word-break: break-word; }
.basis-item.strong strong { color: #1769f6; font-size: 24px; }
.missing-box {
  margin-top: 16px;
  padding: 12px 14px;
  background: #fff8eb;
  border: 1px solid #f6d7a8;
  border-radius: 8px;
  color: #875100;
}
.explain-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-top: 16px; }
.explain-grid div { padding: 16px; background: #f8fbff; border-radius: 8px; }
.explain-grid strong { display: block; margin-bottom: 8px; }
.explain-grid p { margin: 0; color: #5b6b82; line-height: 1.7; }
@media (max-width: 860px) {
  .intro-band { align-items: flex-start; flex-direction: column; padding: 24px; }
  .intro-band h1 { font-size: 26px; }
  .intro-actions { justify-content: flex-start; }
  .basis-grid,
  .explain-grid { grid-template-columns: 1fr; }
}

.intro-modes {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.mode-hint {
  font-size: 13px;
  color: #7a8aa4;
  display: flex;
  align-items: center;
  gap: 4px;
}
.mode-hint.fast::before { content: "⚡"; }
.mode-hint.chat::before { content: "💬"; }
</style>
