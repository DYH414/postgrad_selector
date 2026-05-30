<template>
  <div class="ai-report-page">
    <AppHeader current-page="ai" />

    <div class="report-container" v-if="report">
      <!-- PENDING: Terminal loading + tier card skeleton -->
      <div v-if="report.status === 'PENDING'" class="loading-layout">
        <!-- LEFT: Terminal window -->
        <div class="terminal-panel">
          <div class="terminal-chrome">
            <span class="chrome-dot red" />
            <span class="chrome-dot yellow" />
            <span class="chrome-dot green" />
            <span class="chrome-title">408-RECRUIT-ENGINE</span>
            <span class="chrome-pulse" />
          </div>
          <div class="terminal-body">
            <div v-for="(line, idx) in displayLogs" :key="idx" class="terminal-line"
              :class="{ latest: idx === displayLogs.length - 1 && !allDone }">
              <span class="terminal-prompt">❯</span>
              <span>{{ line }}</span>
            </div>
            <div v-if="!allDone" class="terminal-line active">
              <span class="terminal-prompt">❯</span>
              <span>{{ typingLine }}</span>
              <span class="cursor-blink" />
            </div>
          </div>
          <div class="terminal-footer">
            <span>MODEL: AI-RECOMMEND-V2</span>
            <span>STATUS: {{ allDone ? 'COMPLETED' : 'PROCESSING...' }}</span>
          </div>
        </div>

        <!-- RIGHT: Tier card skeletons -->
        <div class="skeleton-cards">
          <div class="skeleton-card reach">
            <span class="sk-badge red">冲刺档</span>
            <div class="sk-bar" />
            <div class="sk-bar short" />
            <div class="sk-bar short" />
          </div>
          <div class="skeleton-card steady">
            <span class="sk-badge blue">稳妥档</span>
            <div class="sk-bar" />
            <div class="sk-bar short" />
            <div class="sk-bar short" />
          </div>
          <div class="skeleton-card safe">
            <span class="sk-badge green">保底档</span>
            <div class="sk-bar" />
            <div class="sk-bar short" />
            <div class="sk-bar short" />
          </div>
        </div>
      </div>

      <!-- COMPLETED: Full report -->
      <template v-else>
        <div class="report-header">
          <h2>你的 AI 择校推荐报告</h2>
          <p class="summary">{{ result.summary }}</p>
        </div>

        <div v-for="tier in result.tiers" :key="tier.level" class="tier-section">
          <h3 class="tier-label" :class="tier.level">
            {{ tier.label }} ({{ tier.schools.length }}所)
          </h3>
          <el-row :gutter="16">
            <el-col :span="8" v-for="school in tier.schools" :key="school.programId">
              <el-card class="school-card" shadow="hover">
                <div class="card-header">
                  <strong>{{ school.schoolName }}</strong>
                  <el-tag :type="riskType(school.risk)" size="mini">{{ riskLabel(school.risk) }}</el-tag>
                </div>
                <p class="program-name">{{ school.programName }}</p>
                <el-divider />
                <p class="reason">{{ school.reason }}</p>
                <div class="pros-cons">
                  <div v-if="school.pros && school.pros.length">
                    <strong>优势：</strong>
                    <span v-for="p in school.pros" :key="p" class="tag green">{{ p }}</span>
                  </div>
                  <div v-if="school.cons && school.cons.length" style="margin-top:4px">
                    <strong>注意：</strong>
                    <span v-for="c in school.cons" :key="c" class="tag orange">{{ c }}</span>
                  </div>
                </div>
                <div class="match-bar">
                  <span>匹配度</span>
                  <el-progress :percentage="school.matchScore || 0"
                    :color="matchColor(school.matchScore)" />
                </div>

                <div v-if="school.scoreLine || school.avgAdmittedScore" class="stats-grid">
                  <div class="stat-item" v-if="school.scoreLine">
                    <span class="stat-label">复试线</span>
                    <span class="stat-val">{{ school.scoreLine }}</span>
                  </div>
                  <div class="stat-item" v-if="school.avgAdmittedScore">
                    <span class="stat-label">录取均分</span>
                    <span class="stat-val">{{ school.avgAdmittedScore }}</span>
                  </div>
                  <div class="stat-item" v-if="school.admissionLow">
                    <span class="stat-label">最低分</span>
                    <span class="stat-val">{{ school.admissionLow }}</span>
                  </div>
                  <div class="stat-item" v-if="school.admissionHigh">
                    <span class="stat-label">最高分</span>
                    <span class="stat-val">{{ school.admissionHigh }}</span>
                  </div>
                  <div class="stat-item" v-if="school.planCount">
                    <span class="stat-label">招生计划</span>
                    <span class="stat-val">{{ school.planCount }}</span>
                  </div>
                  <div class="stat-item" v-if="school.admittedCount">
                    <span class="stat-label">录取人数</span>
                    <span class="stat-val">{{ school.admittedCount }}</span>
                  </div>
                </div>

                <div v-if="school.dataYear || school.dataCompleteness" class="data-meta">
                  <span v-if="school.dataYear">{{ school.dataYear }}年数据</span>
                  <span v-if="school.dataCompleteness" class="completeness-tag"
                    :class="'completeness-' + school.dataCompleteness">
                    完整度 {{ school.dataCompleteness }}
                  </span>
                </div>

                <div class="school-actions">
                  <el-button size="small" @click.stop="goDetail(school)">查看详情</el-button>
                  <el-button size="small" @click.stop="addCompare(school)">加入对比</el-button>
                  <el-button size="small" type="primary" @click.stop="favoriteSchool(school)">收藏</el-button>
                </div>

                <a v-if="school.sourceUrl" class="source-link"
                  :href="school.sourceUrl" target="_blank" @click.stop>
                  数据来源: {{ school.sourceOwner || 'N诺' }} →
                </a>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <div class="report-actions">
          <el-button type="primary" @click="router.back()">返回</el-button>
          <el-button @click="restartRecommend">重新推荐</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addFavorite } from '@/api/favorites'
import AppHeader from '@/components/AppHeader.vue'
import { getAiReport } from '@/api/ai'

const router = useRouter()
const route = useRoute()

const LOGS = [
  '[SYS_INIT] 正在连接 408 统考主数据池...',
  '[DATA_LOAD] 读取历年高校 408 复试线与录取数据...',
  '[FILTERS] 自适应匹配省份与学位类型偏好...',
  '[ALIGNMENT] 用户预估分与历史录取库对齐...',
  '[WEIGHTS] 启动研招知识图谱加权决策引擎...',
  '[AI_COGNITIVE] AI 综合研判冲/稳/保三档分类...',
  '[SECURITY] 数据加密沙盒隔离，已阻断敏感泄露...',
  '[COMPLETED] 冲顶/稳妥/保底三档推荐装载就绪！'
]

const report = ref(null)
const pollTimer = ref(null)
const typeTimer = ref(null)
const displayLogs = ref([])
const logIdx = ref(0)
const charIdx = ref(0)
const typingLine = ref('')
const allDone = ref(false)

const result = computed(() => {
  if (!report.value) return { summary: '', tiers: [] }
  const data = report.value.result || report.value
  if (typeof data === 'string') {
    try { return JSON.parse(data) } catch (e) { return { summary: '', tiers: [] } }
  }
  return data
})

async function fetchReport() {
  try {
    const res = await getAiReport(route.params.id)
    report.value = res.data
    if (res.data.status === 'PENDING') {
      startTypewriter()
      pollTimer.value = setInterval(async () => {
        const r = await getAiReport(route.params.id)
        report.value = r.data
        if (r.data.status !== 'PENDING') {
          finishTypewriter()
          clearInterval(pollTimer.value)
        }
      }, 3000)
    }
  } catch (e) {
    ElMessage.error('加载报告失败')
  }
}

function startTypewriter() {
  displayLogs.value = []
  logIdx.value = 0
  charIdx.value = 0
  typingLine.value = ''
  allDone.value = false
  tickType()
}

function tickType() {
  if (logIdx.value >= LOGS.length) {
    allDone.value = true
    return
  }
  const target = LOGS[logIdx.value]
  if (charIdx.value < target.length) {
    typingLine.value += target.charAt(charIdx.value)
    charIdx.value++
    typeTimer.value = setTimeout(() => tickType(), 25)
  } else {
    displayLogs.value.push(target)
    logIdx.value++
    charIdx.value = 0
    typingLine.value = ''
    typeTimer.value = setTimeout(() => tickType(), 400)
  }
}

function finishTypewriter() {
  if (typeTimer.value) clearTimeout(typeTimer.value)
  displayLogs.value = [...LOGS]
  typingLine.value = ''
  allDone.value = true
}

function riskType(risk) {
  return risk === 'high' ? 'danger' : risk === 'medium' ? 'warning' : 'success'
}

function riskLabel(risk) {
  return risk === 'high' ? '高风险' : risk === 'medium' ? '中等风险' : '低风险'
}

function matchColor(score) {
  return score >= 70 ? '#67c23a' : score >= 50 ? '#e6a23c' : '#f56c6c'
}

function restartRecommend() {
  router.push({ name: 'Recommend' })
}

function goDetail(school) {
  if (!school || !school.programId) {
    ElMessage.warning('该推荐缺少专业 ID，暂时无法查看详情')
    return
  }
  router.push({
    path: '/results',
    query: {
      tab: 'compare',
      programIds: String(school.programId),
      score: result.value.score || ''
    }
  })
}

function addCompare(school) {
  if (!school || !school.programId) {
    ElMessage.warning('该推荐缺少专业 ID，暂时无法加入对比')
    return
  }
  const key = 'app-compare-program-ids'
  const current = JSON.parse(localStorage.getItem(key) || '[]')
  const next = Array.from(new Set([...current, school.programId])).slice(0, 8)
  localStorage.setItem(key, JSON.stringify(next))
  router.push({
    path: '/results',
    query: {
      tab: 'compare',
      programIds: next.join(','),
      score: result.value.score || ''
    }
  })
}

function favoriteSchool(school) {
  if (!school || !school.programId) {
    ElMessage.warning('该推荐缺少专业 ID，暂时无法收藏')
    return
  }
  addFavorite(school.programId).then(() => {
    ElMessage.success('已加入收藏')
  })
}

onMounted(() => {
  fetchReport()
})

onBeforeUnmount(() => {
  if (pollTimer.value) clearInterval(pollTimer.value)
  if (typeTimer.value) clearTimeout(typeTimer.value)
})
</script>

<style scoped>
.ai-report-page { min-height: 100vh; background: #f5f7fa; }
.report-container { max-width: 1100px; margin: 0 auto; padding: 24px; }

/* ===== PENDING: Terminal + Skeleton layout ===== */
.loading-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; align-items: start; }

/* Terminal panel */
.terminal-panel {
  background: #1e293b; border-radius: 16px; overflow: hidden;
  font-family: 'Courier New', Courier, monospace; box-shadow: 0 8px 32px rgba(0,0,0,.12);
}
.terminal-chrome {
  display: flex; align-items: center; gap: 8px; padding: 12px 16px;
  background: #0f172a; border-bottom: 1px solid #334155;
}
.chrome-dot { width: 10px; height: 10px; border-radius: 50%; }
.chrome-dot.red { background: #f87171; }
.chrome-dot.yellow { background: #fbbf24; }
.chrome-dot.green { background: #34d399; }
.chrome-title { color: #94a3b8; font-size: 11px; letter-spacing: 2px; margin-left: 8px; flex: 1; }
.chrome-pulse { width: 8px; height: 8px; border-radius: 50%; background: #38bdf8; animation: pulse 1.5s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.3} }

.terminal-body { padding: 20px 16px; min-height: 280px; font-size: 12px; line-height: 2; }
.terminal-line { color: #94a3b8; display: flex; gap: 8px; }
.terminal-line.latest { color: #34d399; font-weight: 600; }
.terminal-line.active { color: #38bdf8; display: flex; gap: 8px; }
.terminal-prompt { color: #38bdf8; flex-shrink: 0; }
.cursor-blink { display: inline-block; width: 8px; height: 15px; background: #38bdf8; animation: blink 1s infinite; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }

.terminal-footer {
  display: flex; justify-content: space-between; padding: 10px 16px;
  border-top: 1px solid #334155; color: #64748b; font-size: 10px;
}

/* Skeleton cards */
.skeleton-cards { display: flex; flex-direction: column; gap: 16px; }
.skeleton-card {
  background: #fff; border-radius: 14px; padding: 24px; position: relative;
  overflow: hidden; box-shadow: 0 1px 6px rgba(0,0,0,.06);
}
.skeleton-card.reach { border-left: 4px solid #f56c6c; }
.skeleton-card.steady { border-left: 4px solid #409eff; }
.skeleton-card.safe { border-left: 4px solid #67c23a; }
.sk-badge {
  font-size: 11px; font-weight: 700; padding: 2px 10px; border-radius: 4px;
  display: inline-block; margin-bottom: 16px; letter-spacing: 1px;
}
.sk-badge.red { background: #fef0f0; color: #f56c6c; }
.sk-badge.blue { background: #ecf5ff; color: #409eff; }
.sk-badge.green { background: #f0f9eb; color: #67c23a; }
.sk-bar {
  height: 14px; border-radius: 4px; background: #f0f2f5;
  margin-bottom: 10px; animation: shimmer 1.8s infinite;
}
.sk-bar.short { width: 60%; }
@keyframes shimmer {
  0% { background: #f0f2f5; }
  50% { background: #e4e7ed; }
  100% { background: #f0f2f5; }
}

/* ===== Report (unchanged) ===== */
.report-header { margin-bottom: 32px; }
.report-header h2 { margin-bottom: 8px; }
.summary { color: #606266; font-size: 15px; }
.tier-section { margin-bottom: 32px; }
.tier-label { padding: 6px 0; border-bottom: 2px solid #ebeef5; margin-bottom: 16px; }
.tier-label.reach { color: #f56c6c; }
.tier-label.steady { color: #e6a23c; }
.tier-label.safe { color: #67c23a; }
.school-card { margin-bottom: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.program-name { color: #909399; font-size: 13px; margin: 4px 0; }
.reason { color: #303133; font-size: 13px; line-height: 1.6; }
.tag { display: inline-block; padding: 1px 6px; border-radius: 4px;
  font-size: 12px; margin-right: 4px; }
.tag.green { background: #f0f9eb; color: #67c23a; }
.tag.orange { background: #fdf6ec; color: #e6a23c; }
.match-bar { margin-top: 12px; display: flex; align-items: center; gap: 8px; }
.match-bar span { font-size: 12px; color: #909399; white-space: nowrap; }
.report-actions { text-align: center; padding: 24px 0 48px; }

.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 12px; }
.stat-item { text-align: center; padding: 4px 2px; background: #f8fafc; border-radius: 4px; }
.stat-label { display: block; font-size: 11px; color: #909399; }
.stat-val { font-size: 15px; font-weight: 700; color: #303133; }

.data-meta { margin-top: 8px; font-size: 12px; color: #909399; display: flex; gap: 8px; align-items: center; }
.completeness-tag { display: inline-block; padding: 0 4px; border-radius: 3px; font-size: 11px; }
.completeness-A { background: #f0f9eb; color: #67c23a; }
.completeness-B { background: #fdf6ec; color: #e6a23c; }
.completeness-C { background: #fef0f0; color: #f56c6c; }

.source-link { display: block; margin-top: 8px; font-size: 11px; color: #409eff; text-decoration: none; }
.source-link:hover { text-decoration: underline; }

.school-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

@media (max-width: 768px) {
  .loading-layout { grid-template-columns: 1fr; }
}
</style>
