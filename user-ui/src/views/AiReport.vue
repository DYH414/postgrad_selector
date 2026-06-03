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
        <div class="report-hero">
          <div>
            <p class="eyebrow">AI SCHOOL SELECTION REPORT</p>
            <h2>你的 AI 择校推荐报告</h2>
            <p class="summary">{{ result.summary }}</p>
          </div>
          <div class="hero-meta">
            <span>{{ candidatePoolLabel }}</span>
            <span>{{ verificationProviderLabel(result.metadata?.verificationProvider) }}</span>
          </div>
        </div>

        <div class="report-overview">
          <div v-for="item in overviewCards" :key="item.key" class="overview-card" :class="item.key">
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </div>

        <el-alert
          v-if="result.legacy"
          class="report-notice"
          title="这是一份旧版匹配度报告，系统已按新报告格式做兼容展示。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="result.metadata && result.metadata.toolTraceIncompleteCount"
          class="report-notice"
          :title="`有 ${result.metadata.toolTraceIncompleteCount} 所学校因数据核验不完整被移出推荐。`"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="result.metadata && result.metadata.explorationLimited"
          class="report-notice"
          title="本次报告已达到工具调用预算，后续建议优先核验已展示学校。"
          type="warning"
          show-icon
          :closable="false"
        />

        <div v-for="tier in result.tiers" :key="tier.level" class="tier-section">
          <div class="tier-heading" :class="tier.level">
            <div>
              <h3>{{ tier.label }} <span>{{ tier.schools.length }} 所</span></h3>
              <p>{{ tierHint(tier.level) }}</p>
            </div>
          </div>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :lg="8" v-for="school in tier.schools" :key="school.programId">
              <el-card class="school-card" :class="'card-' + tier.level" shadow="hover">
                <div class="card-header">
                  <div>
                    <strong>{{ school.schoolName }}</strong>
                    <p class="program-name">{{ school.programName }}</p>
                  </div>
                  <span class="judgement-pill" :class="'judgement-' + school.judgement">
                    {{ displayJudgement(school, tier.level) }}
                  </span>
                </div>

                <div class="quick-stats">
                  <div>
                    <span>录取均分</span>
                    <strong>{{ valueOrDash(school.avgAdmittedScore) }}</strong>
                  </div>
                  <div>
                    <span>分数差</span>
                    <strong :class="gapClass(school.avgScoreGap ?? school.gap)">
                      {{ formatGap(school.avgScoreGap ?? school.gap) }}
                    </strong>
                  </div>
                  <div>
                    <span>最低录取</span>
                    <strong>{{ valueOrDash(school.admissionLow) }}</strong>
                  </div>
                </div>

                <div v-if="school.evidence && school.evidence.length" class="evidence-list">
                  <strong>推荐依据</strong>
                  <p v-for="item in school.evidence" :key="item">{{ item }}</p>
                </div>
                <div v-if="school.risks && school.risks.length" class="risk-list">
                  <strong>需要注意</strong>
                  <p v-for="item in school.risks" :key="item">{{ item }}</p>
                </div>
                <div class="school-actions">
                  <el-button size="small" @click.stop="goDetail(school)">查看详情</el-button>
                  <el-button size="small" @click.stop="addCompare(school)">加入对比</el-button>
                  <el-button
                    size="small"
                    :type="isBackupSchool(school) ? 'success' : 'primary'"
                    :loading="isAddingBackup(school)"
                    @click.stop="favoriteSchool(school)">
                    {{ isBackupSchool(school) ? '取消备选' : '加入备选' }}
                  </el-button>
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

        <el-drawer title="学校详情" v-model="detailVisible" size="560px" append-to-body>
          <div class="detail-drawer" v-loading="detailLoading">
            <el-alert
              v-if="detailError"
              class="detail-alert"
              type="warning"
              :title="detailError"
              show-icon
              :closable="false"
            />
            <template v-if="detailSchool">
              <h2>{{ detailTitle.schoolName }}</h2>
              <p class="detail-subtitle">{{ detailTitle.collegeName }} / {{ detailTitle.programName }}</p>
              <div class="detail-tags">
                <span v-if="detailTitle.examCombo">{{ detailTitle.examCombo }}</span>
                <span v-if="detailTitle.studyModeLabel">{{ detailTitle.studyModeLabel }}</span>
                <span>{{ detailSchool.judgementLabel || '-' }}</span>
                <span>{{ detailSchool.verificationStatusLabel || '待核验' }}</span>
              </div>

              <div class="detail-stats">
                <div class="d-stat"><small>复试线</small><strong>{{ valueOrDash(detailOverview.scoreLine) }}</strong></div>
                <div class="d-stat"><small>最低录取</small><strong>{{ valueOrDash(detailOverview.admissionLow) }}</strong></div>
                <div class="d-stat"><small>拟录取区间</small><strong>{{ detailOverview.admissionRangeLabel || '-' }}</strong></div>
                <div class="d-stat"><small>均分差距</small><strong :class="gapClass(detailOverview.avgScoreGap)">{{ formatGap(detailOverview.avgScoreGap) }}</strong></div>
                <div class="d-stat"><small>招生计划</small><strong>{{ valueOrDash(detailSnapshot.planCount) }}</strong></div>
                <div class="d-stat"><small>录取人数</small><strong>{{ valueOrDash(detailSnapshot.admittedCount) }}</strong></div>
                <div class="d-stat"><small>复试人数</small><strong>{{ valueOrDash(detailSnapshot.retestCount) }}</strong></div>
                <div class="d-stat"><small>数据完整度</small><strong>{{ detailCompleteness.label || valueOrDash(detailSnapshot.dataCompleteness) }}</strong></div>
              </div>

              <section v-if="detailSchool.evidence && detailSchool.evidence.length" class="detail-section">
                <h4>AI 推荐依据</h4>
                <p v-for="item in detailSchool.evidence" :key="item">{{ item }}</p>
              </section>
              <section v-if="detailSchool.risks && detailSchool.risks.length" class="detail-section warning">
                <h4>需要注意</h4>
                <p v-for="item in detailSchool.risks" :key="item">{{ item }}</p>
              </section>
              <section v-if="detailData && detailData.trends && detailData.trends.length" class="detail-section">
                <h4>近年趋势</h4>
                <div class="trend-list">
                  <div v-for="item in detailData.trends.slice(0, 3)" :key="item.year">
                    <span>{{ item.year }}</span>
                    <strong>均分 {{ valueOrDash(item.avgAdmittedScore) }}</strong>
                    <small>复试线 {{ valueOrDash(item.scoreLine) }}</small>
                  </div>
                </div>
              </section>
              <a v-if="detailSource.sourceUrl" class="detail-source" :href="detailSource.sourceUrl" target="_blank">
                查看数据来源 →
              </a>
            </template>
            <div v-else-if="!detailLoading" class="detail-empty">
              暂无详情数据
            </div>
          </div>
        </el-drawer>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addFavorite, listFavorites, removeFavorite } from '@/api/favorites'
import AppHeader from '@/components/AppHeader.vue'
import { getAiReport } from '@/api/ai'
import { getProgramDetail } from '@/api/programs'
import { COMPARE_STORAGE_KEY, COMPARE_SCORE_KEY, COMPARE_MAX_ITEMS } from '@/api/compare-constants'
import { normalizeAiReport } from '@/utils/aiReport'

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
const detailVisible = ref(false)
const detailSchool = ref(null)
const detailData = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const backupProgramIds = ref([])
const addingBackupIds = ref([])

const result = computed(() => {
  return normalizeAiReport(report.value)
})

const overviewCards = computed(() => {
  const byLevel = Object.fromEntries((result.value.tiers || []).map(tier => [tier.level, tier.schools.length]))
  return [
    { key: 'reach', label: '冲刺目标', count: byLevel.reach || 0, hint: '只建议作为上限尝试' },
    { key: 'steady', label: '稳妥候选', count: byLevel.steady || 0, hint: '优先核验招生计划' },
    { key: 'safe', label: '保底备选', count: byLevel.safe || 0, hint: '用于降低整体风险' }
  ]
})

const candidatePoolLabel = computed(() => {
  const count = result.value.metadata?.candidateCount || result.value.metadata?.poolSize
  return count ? `候选池 ${count}` : '本地候选池'
})

const detailTitle = computed(() => {
  const basic = detailData.value?.basic || {}
  return {
    schoolName: basic.schoolName || detailSchool.value?.schoolName || '-',
    collegeName: basic.collegeName || detailSchool.value?.collegeName || '',
    programName: basic.programName || detailSchool.value?.programName || '-',
    examCombo: basic.examCombo || '',
    studyModeLabel: basic.studyModeLabel || ''
  }
})

const detailOverview = computed(() => detailData.value?.recommendationOverview || {
  scoreLine: detailSchool.value?.scoreLine,
  admissionLow: detailSchool.value?.admissionLow,
  admissionRangeLabel: detailSchool.value?.admissionLow && detailSchool.value?.admissionHigh
    ? `${detailSchool.value.admissionLow}-${detailSchool.value.admissionHigh}`
    : '',
  avgScoreGap: detailSchool.value?.avgScoreGap ?? detailSchool.value?.gap
})

const detailSnapshot = computed(() => ({
  planCount: detailData.value?.recommendationOverview?.planCount ?? detailSchool.value?.planCount,
  admittedCount: detailData.value?.recommendationOverview?.admittedCount ?? detailSchool.value?.admittedCount,
  retestCount: detailData.value?.recommendationOverview?.retestCount ?? detailSchool.value?.retestCount,
  dataCompleteness: detailSchool.value?.dataCompleteness
}))

const detailSource = computed(() => detailData.value?.source || {
  sourceUrl: detailSchool.value?.sourceUrl,
  sourceOwner: detailSchool.value?.sourceOwner
})

const detailCompleteness = computed(() => detailData.value?.dataCompleteness || {
  label: detailSchool.value?.dataCompleteness ? `完整度 ${detailSchool.value.dataCompleteness}` : '-'
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

function restartRecommend() {
  router.push({ name: 'Recommend' })
}

async function goDetail(school) {
  if (!school) return
  detailSchool.value = school
  detailData.value = null
  detailError.value = ''
  detailVisible.value = true
  if (!school.programId) {
    detailError.value = '该推荐缺少专业 ID，暂时无法加载数据库详情'
    return
  }
  detailLoading.value = true
  try {
    const estimatedScore = estimateScoreFromSchool(school)
    const res = await getProgramDetail(school.programId, estimatedScore ? { estimatedScore } : undefined)
    detailData.value = res.data
  } catch (e) {
    detailError.value = '详情数据加载失败，当前展示报告快照'
  } finally {
    detailLoading.value = false
  }
}

function addCompare(school) {
  if (!school || !school.programId) {
    ElMessage.warning('该推荐缺少专业 ID，暂时无法加入对比')
    return
  }
  const current = JSON.parse(localStorage.getItem(COMPARE_STORAGE_KEY) || '[]')
  if (current.includes(school.programId)) {
    ElMessage.info('该学校已在对比列表中')
    return
  }
  const next = [...current, school.programId].slice(0, COMPARE_MAX_ITEMS)
  localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(next))

  // Derive estimatedScore from school data and persist for compare page
  if (school.avgAdmittedScore != null && school.gap != null) {
    const score = school.avgAdmittedScore + school.gap
    localStorage.setItem(COMPARE_SCORE_KEY, String(score))
  }

  if (next.length > current.length) {
    ElMessage.success(`已加入对比 (${next.length}/${COMPARE_MAX_ITEMS})`)
  } else {
    ElMessage.warning(`对比列表已满 (${COMPARE_MAX_ITEMS}所)`)
  }
}

function isBackupSchool(school) {
  return backupProgramIds.value.includes(Number(school?.programId))
}

function isAddingBackup(school) {
  return addingBackupIds.value.includes(Number(school?.programId))
}

async function loadBackupProgramIds() {
  try {
    const res = await listFavorites()
    backupProgramIds.value = Array.from(new Set((res.data || [])
      .map(item => Number(item.programId || item.program_id))
      .filter(Boolean)))
  } catch (e) {
    backupProgramIds.value = []
  }
}

async function favoriteSchool(school) {
  if (!school || !school.programId) {
    ElMessage.warning('该推荐缺少专业 ID，暂时无法加入备选')
    return
  }
  const programId = Number(school.programId)
  if (addingBackupIds.value.includes(programId)) return

  addingBackupIds.value = [...addingBackupIds.value, programId]
  try {
    if (backupProgramIds.value.includes(programId)) {
      await removeFavorite(programId)
      backupProgramIds.value = backupProgramIds.value.filter(id => id !== programId)
      ElMessage.success('已取消备选')
    } else {
      await addFavorite(programId)
      backupProgramIds.value = Array.from(new Set([...backupProgramIds.value, programId]))
      ElMessage.success('已加入备选')
    }
  } catch (e) {
    ElMessage.error('备选状态更新失败')
  } finally {
    addingBackupIds.value = addingBackupIds.value.filter(id => id !== programId)
  }
}

function estimateScoreFromSchool(school) {
  if (school.avgAdmittedScore != null && (school.avgScoreGap != null || school.gap != null)) {
    return Number(school.avgAdmittedScore) + Number(school.avgScoreGap ?? school.gap)
  }
  return null
}

function valueOrDash(value) {
  return value === null || value === undefined || value === '' ? '-' : value
}

function formatGap(value) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (Number.isNaN(number)) return value
  return number > 0 ? `+${number}` : String(number)
}

function gapClass(value) {
  const number = Number(value)
  if (Number.isNaN(number)) return ''
  return number >= 0 ? 'positive' : 'negative'
}

function tierHint(level) {
  if (level === 'reach') return '适合作为上限尝试，不建议单独押注。'
  if (level === 'steady') return '适合作为主力候选，建议优先核验官网计划。'
  if (level === 'safe') return '用于降低整体择校风险，仍需复查当年变化。'
  return '以下结果由 AI 综合候选池和本地数据生成。'
}

function displayJudgement(school, tierLevel) {
  if (tierLevel === 'reach' && ['safe', 'steady'].includes(school.judgement)) {
    return school.judgement === 'safe' ? '冲刺档待复核' : '稳妥偏冲'
  }
  return school.judgementLabel || '待核验'
}

function verificationProviderLabel(provider) {
  if (provider === 'local_noop') return '仅本地数据'
  if (provider === 'official') return '官网核验'
  if (provider === 'third_party') return '第三方核验'
  return '本地数据优先'
}

onMounted(() => {
  fetchReport()
  loadBackupProgramIds()
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

/* ===== Report ===== */
.report-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 16px;
  padding: 28px 30px;
  border: 1px solid #dfe8f6;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(31, 45, 61, .06);
}
.eyebrow {
  margin: 0 0 8px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1.2px;
}
.report-hero h2 { margin: 0 0 10px; font-size: 28px; color: #172033; }
.summary { max-width: 760px; margin: 0; color: #56657a; font-size: 15px; line-height: 1.8; }
.hero-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
  flex-shrink: 0;
}
.hero-meta span {
  padding: 6px 10px;
  border-radius: 6px;
  background: #eef4ff;
  color: #1769f6;
  font-size: 12px;
  font-weight: 700;
}
.report-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 22px;
}
.overview-card {
  padding: 18px 20px;
  border: 1px solid #e3ebf6;
  border-radius: 8px;
  background: #fff;
}
.overview-card span { display: block; color: #66758a; font-size: 13px; }
.overview-card strong { display: block; margin: 6px 0 2px; font-size: 30px; color: #172033; }
.overview-card small { color: #7b8798; }
.overview-card.reach { border-top: 3px solid #ef4444; }
.overview-card.steady { border-top: 3px solid #2563eb; }
.overview-card.safe { border-top: 3px solid #16a34a; }
.report-notice { margin: 0 0 16px; }
.tier-section { margin-bottom: 34px; }
.tier-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #fff;
  border-left: 4px solid #94a3b8;
}
.tier-heading h3 { margin: 0; color: #172033; font-size: 20px; }
.tier-heading h3 span { color: #7b8798; font-size: 14px; font-weight: 500; }
.tier-heading p { margin: 4px 0 0; color: #66758a; font-size: 13px; }
.tier-heading.reach { border-left-color: #ef4444; }
.tier-heading.steady { border-left-color: #2563eb; }
.tier-heading.safe { border-left-color: #16a34a; }
.school-card {
  min-height: 360px;
  margin-bottom: 16px;
  border-radius: 8px;
  border: 1px solid #e3ebf6;
}
.school-card :deep(.el-card__body) { padding: 20px; }
.card-reach { border-top: 3px solid #ef4444; }
.card-steady { border-top: 3px solid #2563eb; }
.card-safe { border-top: 3px solid #16a34a; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.card-header strong { display: block; color: #172033; font-size: 18px; }
.program-name { color: #7b8798; font-size: 13px; margin: 6px 0 0; }
.judgement-pill {
  display: inline-flex;
  align-items: center;
  min-width: 74px;
  justify-content: center;
  padding: 5px 9px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}
.judgement-safe { color: #15803d; background: #dcfce7; }
.judgement-steady { color: #1d4ed8; background: #dbeafe; }
.judgement-steady_reach,
.judgement-small_reach { color: #b45309; background: #fef3c7; }
.judgement-high_risk_reach { color: #b91c1c; background: #fee2e2; }
.judgement-data_insufficient_pending { color: #475569; background: #e2e8f0; }
.quick-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 16px 0;
}
.quick-stats div {
  padding: 10px;
  border-radius: 6px;
  background: #f7faff;
}
.quick-stats span { display: block; margin-bottom: 3px; color: #7b8798; font-size: 11px; }
.quick-stats strong { color: #172033; font-size: 16px; }
.positive { color: #16a34a !important; }
.negative { color: #ef4444 !important; }
.evidence-list,
.risk-list {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.6;
}
.evidence-list strong,
.risk-list strong { display: block; margin-bottom: 4px; color: #303133; }
.evidence-list p,
.risk-list p { margin: 0 0 5px; color: #56657a; }
.risk-list p { color: #9f3412; }
.report-actions { text-align: center; padding: 24px 0 48px; }
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

.detail-drawer h2 { margin: 0 0 4px; font-size: 22px; color: #303133; }
.detail-subtitle { color: #909399; margin: 0 0 20px; font-size: 14px; }
.detail-alert { margin-bottom: 14px; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.detail-tags span {
  padding: 5px 9px;
  border-radius: 5px;
  color: #1769f6;
  background: #eef4ff;
  font-weight: 700;
  font-size: 12px;
}
.detail-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 20px; }
.d-stat { background: #f7faff; border-radius: 8px; padding: 12px; border: 1px solid #edf2f8; }
.d-stat small { display: block; color: #909399; font-size: 12px; margin-bottom: 4px; }
.d-stat strong { font-size: 18px; color: #303133; }
.d-stat strong.positive { color: #67c23a; }
.d-stat strong.negative { color: #f56c6c; }
.detail-section {
  margin: 14px 0;
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
}
.detail-section.warning { background: #fff7ed; }
.detail-section h4 { margin: 0 0 8px; color: #172033; }
.detail-section p { margin: 0 0 6px; color: #56657a; line-height: 1.7; }
.trend-list { display: grid; gap: 8px; }
.trend-list div {
  display: grid;
  grid-template-columns: 52px 1fr 1fr;
  gap: 8px;
  align-items: center;
  padding: 8px;
  border-radius: 6px;
  background: #fff;
}
.trend-list span { color: #66758a; }
.trend-list strong { color: #172033; }
.trend-list small { color: #7b8798; }
.detail-source { display: block; color: #409eff; text-decoration: none; font-weight: 600; }
.detail-empty { padding: 32px 0; color: #7b8798; text-align: center; }

@media (max-width: 768px) {
  .loading-layout { grid-template-columns: 1fr; }
  .report-container { padding: 14px; }
  .report-hero { flex-direction: column; padding: 20px; }
  .hero-meta { align-items: flex-start; }
  .report-overview { grid-template-columns: 1fr; }
  .quick-stats { grid-template-columns: 1fr; }
  .detail-stats { grid-template-columns: 1fr; }
}
</style>
