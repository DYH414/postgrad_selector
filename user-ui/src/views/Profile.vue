<template>
  <div class="mine-page">
    <AppHeader current-page="profile" />

    <main class="mine-body">
      <section class="mine-hero">
        <div>
          <span class="eyebrow">我的</span>
          <h1>个人择校中心</h1>
          <p>统一管理你的考研画像、备选学校和 AI 推荐记录。画像只展示摘要，具体内容放到 Tab 里，避免重复打扰。</p>
        </div>
        <div class="summary-strip">
          <div>
            <span>预估分</span>
            <strong>{{ profile.estimatedScore || '-' }}</strong>
          </div>
          <div>
            <span>目标地区</span>
            <strong>{{ regionCountText }}</strong>
          </div>
          <div>
            <span>画像完整度</span>
            <strong>{{ completionPercent }}%</strong>
          </div>
        </div>
      </section>

      <el-tabs v-model="activeTab" class="mine-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="我的画像" name="profile">
          <section class="tab-panel profile-panel">
            <div class="panel-head">
              <div>
                <h2>我的考研画像</h2>
                <p>这些信息会作为规则筛选和 AI 推荐的基础。只保留必要字段，目标变化时再更新。</p>
              </div>
              <el-button type="primary" icon="el-icon-edit" @click="startEdit">
                {{ isEmpty ? '填写画像' : '编辑画像' }}
              </el-button>
            </div>

            <div v-if="!editing" v-loading="profileLoading">
              <div v-if="isEmpty" class="empty-state compact">
                <h3>还没有填写画像</h3>
                <p>先填预估分和目标地区，系统才能生成更可解释的候选学校。</p>
                <el-button type="primary" @click="startEdit">开始填写</el-button>
              </div>

              <div v-else class="profile-summary-grid">
                <div class="score-card">
                  <span>预估初试总分</span>
                  <strong>{{ profile.estimatedScore }} 分</strong>
                  <em>满分 500</em>
                </div>
                <div class="profile-fields">
                  <div v-for="item in profileRows" :key="item.label" class="field-card">
                    <span>{{ item.label }}</span>
                    <strong :class="{ muted: item.muted }">{{ item.value }}</strong>
                  </div>
                </div>
              </div>
            </div>

            <el-form v-else ref="formRef" :model="form" label-position="top" class="profile-form">
              <el-form-item label="预估总分" required>
                <el-input-number v-model="form.estimatedScore" :min="100" :max="500" />
                <span class="field-tip">满分 500</span>
              </el-form-item>
              <el-form-item label="目标省份">
                <el-select v-model="form.targetRegions" multiple filterable placeholder="不限（默认全国）" style="width:100%">
                  <el-option v-for="p in provinces" :key="p" :label="p" :value="p" />
                </el-select>
              </el-form-item>
              <section class="preference-form-band">
                <h3>择校偏好</h3>
                <el-form-item label="整体择校策略">
                  <el-radio-group v-model="form.riskPreference">
                    <el-radio-button label="conservative">稳妥优先</el-radio-button>
                    <el-radio-button label="balanced">冲稳保均衡</el-radio-button>
                    <el-radio-button label="aggressive">愿意冲刺</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="院校层次取舍">
                  <el-radio-group v-model="form.schoolTierPreference">
                    <el-radio-button label="must_211_or_better">高层次优先</el-radio-button>
                    <el-radio-button label="prefer_211_or_better">优先 211+</el-radio-button>
                    <el-radio-button label="no_strict_requirement">层次不强求</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="地区取舍">
                  <el-radio-group v-model="form.regionStrategy">
                    <el-radio-button label="developed_priority">发达地区优先</el-radio-button>
                    <el-radio-button label="developed_balanced">发达地区稳妥</el-radio-button>
                    <el-radio-button label="no_strict_requirement">地区不强求</el-radio-button>
                    <el-radio-button label="target_regions_only">只看目标省份</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </section>
              <el-form-item label="接受学硕">
                <el-switch v-model="form.acceptAcademic" />
                <span class="field-tip">关闭则只看专硕</span>
              </el-form-item>
              <el-form-item label="本科层次">
                <el-select v-model="form.undergradTier" clearable placeholder="请选择" style="width:100%">
                  <el-option label="985" value="985" />
                  <el-option label="211" value="211" />
                  <el-option label="双一流" value="DOUBLE_FIRST" />
                  <el-option label="普通一本" value="PUBLIC_REGULAR" />
                  <el-option label="二本/民办" value="PRIVATE" />
                  <el-option label="其他" value="OTHER" />
                </el-select>
              </el-form-item>
              <el-form-item label="本科专业">
                <el-input v-model="form.undergraduateMajor" placeholder="如 软件工程" />
              </el-form-item>
              <el-form-item label="跨考">
                <el-switch v-model="form.isCrossMajor" />
              </el-form-item>
              <el-form-item class="form-actions">
                <el-button type="primary" :loading="saving" @click="handleSave">保存画像</el-button>
                <el-button @click="editing = false">取消</el-button>
              </el-form-item>
            </el-form>
          </section>
        </el-tab-pane>

        <el-tab-pane label="我的备选" name="favorites">
          <section class="tab-panel shortlist-panel">
            <div class="panel-head shortlist-head">
              <div>
                <span class="section-kicker">备选池</span>
                <h2>我的备选方案</h2>
                <p>把感兴趣的学校和专业放进备选池，勾选 2 个以上即可进入对比，后续再按风险和匹配度筛掉。</p>
              </div>
              <div class="panel-actions">
                <span>已选择 {{ shortlistSelectedIds.length }} 项</span>
                <el-button type="primary" :disabled="selectedRows.length < 2" @click="compareSelected">对比所选</el-button>
                <el-button v-if="compareIds.length > 0" type="success" size="small" plain @click="goToCompare">
                  查看对比 ({{ compareIds.length }})
                </el-button>
              </div>
            </div>

            <div class="shortlist-overview">
              <div>
                <span>备选总数</span>
                <strong>{{ shortlistOverview.total }}</strong>
                <small>候选学校 / 专业</small>
              </div>
              <div>
                <span>已选对比</span>
                <strong>{{ shortlistSelectedIds.length }}</strong>
                <small>至少选择 2 项</small>
              </div>
              <div>
                <span>专硕</span>
                <strong>{{ shortlistOverview.professional }}</strong>
                <small>应用型方向</small>
              </div>
              <div>
                <span>学硕</span>
                <strong>{{ shortlistOverview.academic }}</strong>
                <small>科研型方向</small>
              </div>
            </div>

            <div v-loading="favoritesLoading" class="shortlist-grid">
              <article
                v-for="item in favorites"
                :key="item.programId"
                class="shortlist-card"
                :class="{ selected: shortlistSelectedIds.includes(item.programId), comparing: compareIds.includes(item.programId) }"
              >
                <div class="shortlist-card-main">
                  <el-checkbox
                    :model-value="shortlistSelectedIds.includes(item.programId)"
                    @change="toggleShortlistSelection(item)"
                  />
                  <div class="shortlist-card-copy">
                    <div class="shortlist-title-row">
                      <h3>{{ item.schoolName }}</h3>
                      <span class="tier-pill">{{ item.tier || '未标注' }}</span>
                    </div>
                    <p>{{ item.collegeName || '学院待补充' }} / {{ item.programName || '专业待补充' }}</p>
                    <div class="shortlist-meta">
                      <span>{{ item.programCode || '暂无代码' }}</span>
                      <span>{{ degreeTypeLabel(item.degreeType) }}</span>
                      <span v-if="compareIds.includes(item.programId)">已在对比</span>
                    </div>
                  </div>
                </div>
                <div class="shortlist-actions">
                  <el-button size="small" @click="openDetail(item)">查看详情</el-button>
                  <el-button size="small" type="primary" plain @click="addToCompare(item)">
                    {{ compareIds.includes(item.programId) ? '移出对比' : '加入对比' }}
                  </el-button>
                  <el-button size="small" type="danger" plain @click="handleRemove(item)">移出备选</el-button>
                </div>
              </article>
            </div>

            <div v-if="!favoritesLoading && favorites.length === 0" class="empty-state">
              <h3>暂无备选方案</h3>
              <p>加入备选后，学校和专业会集中出现在这里，方便你做最终对比。</p>
              <el-button type="primary" @click="router.push('/')">去筛选学校</el-button>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane label="AI 推荐记录" name="ai">
          <section class="tab-panel">
            <div class="panel-head">
              <div>
                <h2>AI 推荐记录</h2>
                <p>保存每次 AI 择校分析，便于回看推荐理由和冲稳保分层。</p>
              </div>
              <el-button type="primary" icon="el-icon-plus" @click="router.push('/ai-recommend')">新建推荐</el-button>
            </div>

            <div v-loading="reportsLoading" class="report-list">
              <div v-if="!reportsLoading && reports.length === 0" class="empty-state">
                <h3>暂无 AI 推荐报告</h3>
                <p>完成画像后，可以让 AI 基于你的情况生成择校分析。</p>
                <el-button type="primary" @click="router.push('/ai-recommend')">去生成推荐</el-button>
              </div>

              <button v-for="report in reports" :key="report.id" type="button" class="report-card" @click="router.push('/ai-report/' + report.id)">
                <span>
                  <strong>{{ report.summary || 'AI 择校推荐报告' }}</strong>
                  <em>{{ report.createdAt || '-' }}</em>
                </span>
                <small v-if="report.tierSummary">{{ report.tierSummary }}</small>
                <i class="el-icon-arrow-right"></i>
              </button>
            </div>
          </section>
        </el-tab-pane>
      </el-tabs>
    </main>

    <el-drawer title="备选专业详情" v-model="detailVisible" size="520px" append-to-body>
      <div class="detail-drawer" v-loading="detailLoading">
        <template v-if="detail">
          <h2>{{ detail.basic.schoolName }}</h2>
          <p>{{ detail.basic.collegeName }} / {{ detail.basic.programName }}</p>
          <div class="detail-tags">
            <span>{{ detail.basic.examCombo || '-' }}</span>
            <span>{{ detail.basic.studyModeLabel }}</span>
            <span>{{ detail.dataCompleteness.label }}</span>
          </div>
          <div class="detail-score-grid">
            <div><small>复试线</small><strong>{{ detail.recommendationOverview.scoreLine }}</strong></div>
            <div><small>拟录取区间</small><strong>{{ detail.recommendationOverview.admissionRangeLabel || '-' }}</strong></div>
          </div>
          <div class="drawer-warning">
            <p v-for="warning in detail.riskWarnings" :key="warning">· {{ warning }}</p>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { useUserStore } from '@/stores/user'
import { getProfile, saveProfile } from '@/api/profile'
import { listFavorites, removeFavorite } from '@/api/favorites'
import { getProgramDetail } from '@/api/programs'
import { getAiReports } from '@/api/ai'
import { COMPARE_STORAGE_KEY, COMPARE_SCORE_KEY, COMPARE_MAX_ITEMS } from '@/api/compare-constants'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const Tiers = { '985':'985', '211':'211', 'DOUBLE_FIRST':'双一流',
  'PUBLIC_REGULAR':'普通一本', 'PRIVATE':'二本/民办', 'OTHER':'其他' }

const provinces = ['北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江','上海','江苏',
  '浙江','安徽','福建','江西','山东','河南','湖北','湖南','广东','广西','海南','重庆','四川',
  '贵州','云南','西藏','陕西','甘肃','青海','宁夏','新疆']

const activeTab = ref(route.query.tab || 'profile')
const profileLoading = ref(false)
const favoritesLoading = ref(false)
const reportsLoading = ref(false)
const saving = ref(false)
const editing = ref(false)
const favoritesLoaded = ref(false)
const reportsLoaded = ref(false)

const profile = reactive({
  estimatedScore: null, targetRegions: [], undergradTier: null,
  undergraduateMajor: '', isCrossMajor: false, acceptAcademic: false,
  riskPreference: 'balanced',
  schoolTierPreference: 'no_strict_requirement',
  regionStrategy: 'no_strict_requirement'
})

const form = reactive({
  estimatedScore: null, targetRegions: [], undergradTier: null,
  undergraduateMajor: '', isCrossMajor: false, acceptAcademic: false,
  riskPreference: 'balanced',
  schoolTierPreference: 'no_strict_requirement',
  regionStrategy: 'no_strict_requirement'
})

const favorites = ref([])
const selectedRows = ref([])
const compareIds = ref([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const reports = ref([])

const isEmpty = computed(() => !profile.estimatedScore)
const regionText = computed(() => {
  const r = profile.targetRegions
  return (r && r.length) ? r.join('、') : '不限'
})
const regionCountText = computed(() => {
  const count = profile.targetRegions && profile.targetRegions.length
  return count ? `${count} 个` : '不限'
})
const completionPercent = computed(() => {
  const fields = [
    !!profile.estimatedScore,
    !!profile.undergradTier,
    !!profile.undergraduateMajor
  ]
  return Math.round((fields.filter(Boolean).length / fields.length) * 100)
})

const profileRows = computed(() => [
  { label: '目标地区', value: regionText.value },
  { label: '整体策略', value: riskPreferenceLabels[profile.riskPreference] || '稳中求进，冲稳保均衡' },
  { label: '院校层次取舍', value: schoolTierPreferenceLabels[profile.schoolTierPreference] || '不强求层次，有学上更重要' },
  { label: '地区取舍', value: regionStrategyLabels[profile.regionStrategy] || '地区不强求，有学上更重要' },
  { label: '本科层次', value: tierLabel(profile.undergradTier), muted: !profile.undergradTier },
  { label: '本科专业', value: profile.undergraduateMajor || '暂未填写', muted: !profile.undergraduateMajor },
  { label: '跨考情况', value: profile.isCrossMajor ? '跨考' : '非跨考' },
  { label: '学位类型', value: profile.acceptAcademic ? '接受学硕' : '仅专硕' }
])

const shortlistSelectedIds = computed(() => selectedRows.value.map(row => row.programId).filter(Boolean))

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

const shortlistOverview = computed(() => {
  const professional = favorites.value.filter(item => item.degreeType === 'professional').length
  return {
    total: favorites.value.length,
    professional,
    academic: Math.max(0, favorites.value.length - professional)
  }
})

function tierLabel(v) { return Tiers[v] || v || '-' }

function degreeTypeLabel(value) {
  return value === 'professional' ? '专硕' : '学硕'
}

function normalizeRegions(regions) {
  if (typeof regions === 'string') {
    try { return JSON.parse(regions) } catch (e) { return [] }
  }
  return Array.isArray(regions) ? regions : []
}

function fetchProfile() {
  profileLoading.value = true
  getProfile().then(res => {
    if (res.data && res.data.estimatedScore) {
      const p = res.data
      profile.estimatedScore = p.estimatedScore
      profile.targetRegions = normalizeRegions(p.targetRegions)
      profile.acceptAcademic = p.acceptAcademic === 1 || p.acceptAcademic === true
      profile.undergradTier = p.undergradTier
      profile.undergraduateMajor = p.undergraduateMajor || ''
      profile.isCrossMajor = p.isCrossMajor === 1 || p.isCrossMajor === true
      profile.riskPreference = p.riskPreference || 'balanced'
      profile.schoolTierPreference = p.schoolTierPreference || 'no_strict_requirement'
      profile.regionStrategy = p.regionStrategy || 'no_strict_requirement'
    }
  }).finally(() => { profileLoading.value = false })
}

function startEdit() {
  form.estimatedScore = profile.estimatedScore || null
  form.targetRegions = [...(profile.targetRegions || [])]
  form.acceptAcademic = profile.acceptAcademic || false
  form.undergradTier = profile.undergradTier || null
  form.undergraduateMajor = profile.undergraduateMajor || ''
  form.isCrossMajor = profile.isCrossMajor || false
  form.riskPreference = profile.riskPreference || 'balanced'
  form.schoolTierPreference = profile.schoolTierPreference || 'no_strict_requirement'
  form.regionStrategy = profile.regionStrategy || 'no_strict_requirement'
  editing.value = true
}

function handleSave() {
  if (!form.estimatedScore) {
    ElMessage.warning('请输入预估总分')
    return
  }
  saving.value = true
  const data = {
    estimatedScore: form.estimatedScore,
    targetRegions: JSON.stringify(form.targetRegions),
    acceptPartTime: false,
    acceptAcademic: form.acceptAcademic,
    undergradTier: form.undergradTier,
    undergraduateMajor: form.undergraduateMajor,
    isCrossMajor: form.isCrossMajor,
    riskPreference: form.riskPreference,
    schoolTierPreference: form.schoolTierPreference,
    regionStrategy: form.regionStrategy
  }
  saveProfile(data).then(() => {
    ElMessage.success('保存成功')
    Object.assign(profile, { ...form })
    editing.value = false
    userStore.setProfile(data)
  }).finally(() => { saving.value = false })
}

function fetchFavorites() {
  favoritesLoading.value = true
  listFavorites().then(res => {
    favorites.value = res.data || []
    favoritesLoaded.value = true
  }).finally(() => { favoritesLoading.value = false })
}

function handleRemove(row) {
  removeFavorite(row.programId).then(() => {
    ElMessage.success('已移出备选')
    favorites.value = favorites.value.filter(f => f.programId !== row.programId)
    selectedRows.value = selectedRows.value.filter(f => f.programId !== row.programId)
  })
}

function toggleShortlistSelection(row) {
  const exists = selectedRows.value.some(item => item.programId === row.programId)
  selectedRows.value = exists
    ? selectedRows.value.filter(item => item.programId !== row.programId)
    : [...selectedRows.value, row]
}

function loadCompareState() {
  try {
    compareIds.value = JSON.parse(localStorage.getItem(COMPARE_STORAGE_KEY) || '[]')
  } catch (_) { compareIds.value = [] }
}

function addToCompare(row) {
  const id = row.programId
  if (!id) return
  loadCompareState()
  const idx = compareIds.value.indexOf(id)
  if (idx >= 0) {
    // 已加入，移除
    compareIds.value.splice(idx, 1)
    localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(compareIds.value))
    ElMessage.success('已从对比列表移除')
    return
  }
  if (compareIds.value.length >= COMPARE_MAX_ITEMS) {
    ElMessage.warning(`最多对比 ${COMPARE_MAX_ITEMS} 所学校`)
    return
  }
  compareIds.value.push(id)
  localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(compareIds.value))
  if (profile.estimatedScore) {
    localStorage.setItem(COMPARE_SCORE_KEY, String(profile.estimatedScore))
  }
  ElMessage.success(`已加入对比 (${compareIds.value.length}/${COMPARE_MAX_ITEMS})`)
}

function goToCompare() {
  router.push({ path: '/results', query: { tab: 'compare' } })
}

function compareSelected() {
  const ids = selectedRows.value.map(row => row.programId).filter(Boolean)
  if (ids.length < 2) {
    ElMessage.warning('至少选择 2 个项目进行对比')
    return
  }
  router.push({ path: '/results', query: { tab: 'compare', programIds: ids.join(',') } })
}

function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  getProgramDetail(row.programId).then(res => {
    detail.value = res.data
  }).finally(() => { detailLoading.value = false })
}

function parseReport(raw) {
  const report = { id: raw.id, createdAt: raw.created_at || raw.createdAt || '' }
  try {
    let resultJson = raw.result_json || raw.resultJson || '{}'
    if (typeof resultJson === 'string') resultJson = JSON.parse(resultJson)
    report.summary = resultJson.summary || ''
    if (resultJson.tiers && Array.isArray(resultJson.tiers)) {
      report.tierSummary = resultJson.tiers.map(t => `${t.label} ${t.schools ? t.schools.length : 0}所`).join(' · ')
    }
  } catch (e) {
    report.summary = 'AI 择校推荐报告'
  }
  return report
}

async function fetchReports() {
  reportsLoading.value = true
  try {
    const res = await getAiReports()
    const raw = res.data && res.data.reports ? res.data.reports : (res.data || [])
    reports.value = raw.map(r => parseReport(r))
    reportsLoaded.value = true
  } catch (e) {
    ElMessage.error('加载报告列表失败')
  } finally {
    reportsLoading.value = false
  }
}

function handleTabChange(name) {
  router.replace({ path: '/profile', query: name === 'profile' ? {} : { tab: name } }).catch(() => {})
  if (name === 'favorites' && !favoritesLoaded.value) fetchFavorites()
  if (name === 'favorites') loadCompareState()
  if (name === 'ai' && !reportsLoaded.value) fetchReports()
}

onMounted(() => {
  fetchProfile()
  if (activeTab.value === 'favorites') fetchFavorites()
  if (activeTab.value === 'ai') fetchReports()
})
</script>

<style scoped>
.mine-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3f8ff 0%, #f7faff 48%, #f5f7fb 100%);
}

.mine-body {
  max-width: 1180px;
  margin: 0 auto;
  padding: 28px 24px 56px;
}

.mine-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  gap: 22px;
  align-items: center;
  margin-bottom: 18px;
  padding: 30px;
  border: 1px solid #dce9fb;
  border-radius: 20px;
  background: rgba(255,255,255,0.92);
  box-shadow: 0 18px 48px rgba(45, 87, 143, 0.1);
}

.eyebrow {
  display: inline-flex;
  margin-bottom: 10px;
  padding: 5px 10px;
  border-radius: 999px;
  background: #e9f2ff;
  color: #1769f6;
  font-size: 13px;
  font-weight: 800;
}

.mine-hero h1 {
  margin: 0;
  color: #07152f;
  font-size: 32px;
  line-height: 1.2;
}

.mine-hero p {
  max-width: 640px;
  margin: 12px 0 0;
  color: #65758d;
  font-size: 15px;
  line-height: 1.75;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.summary-strip div {
  min-height: 86px;
  padding: 16px;
  border: 1px solid #e2ecfb;
  border-radius: 14px;
  background: #fbfdff;
}

.summary-strip span,
.field-card span,
.score-card span {
  color: #8793a7;
  font-size: 13px;
}

.summary-strip strong {
  display: block;
  margin-top: 12px;
  color: #10203f;
  font-size: 24px;
}

.mine-tabs {
  padding: 6px 22px 24px;
  border: 1px solid #dce9fb;
  border-radius: 18px;
  background: rgba(255,255,255,0.96);
  box-shadow: 0 16px 42px rgba(45, 87, 143, 0.08);
}

.mine-tabs :deep(.el-tabs__item) {
  height: 52px;
  font-size: 16px;
  font-weight: 800;
}

.tab-panel {
  padding-top: 10px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.panel-head h2 {
  margin: 0;
  color: #10203f;
  font-size: 22px;
}

.panel-head p {
  margin: 7px 0 0;
  color: #73829a;
  font-size: 14px;
  line-height: 1.6;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #8793a7;
  white-space: nowrap;
}

.profile-summary-grid {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 18px;
}

.score-card {
  min-height: 190px;
  padding: 24px;
  border-radius: 16px;
  background: linear-gradient(135deg, #1769f6, #35a1ff);
  color: #fff;
}

.score-card span,
.score-card em {
  color: rgba(255,255,255,0.78);
  font-style: normal;
}

.score-card strong {
  display: block;
  margin: 20px 0 8px;
  font-size: 40px;
  line-height: 1;
}

.profile-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field-card {
  min-height: 82px;
  padding: 16px;
  border: 1px solid #e5edf8;
  border-radius: 14px;
  background: #fbfdff;
}

.field-card strong {
  display: block;
  margin-top: 8px;
  color: #10203f;
  font-size: 16px;
  line-height: 1.45;
}

.field-card strong.muted {
  color: #9aa5b5;
}

.profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 22px;
}

.preference-form-band {
  grid-column: 1 / -1;
  padding: 18px 18px 6px;
  border: 1px solid #e5edf8;
  border-radius: 12px;
  background: #f8fbff;
}

.preference-form-band h3 {
  margin: 0 0 14px;
  color: #10203f;
  font-size: 16px;
}

.preference-form-band :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preference-form-band :deep(.el-radio-button__inner) {
  border-left: 1px solid #dcdfe6;
  border-radius: 6px;
}

.field-tip {
  margin-left: 10px;
  color: #7a879a;
  font-size: 13px;
}

.form-actions {
  grid-column: 1 / -1;
}

.section-kicker {
  display: inline-flex;
  margin-bottom: 7px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 900;
}

.shortlist-panel {
  display: grid;
  gap: 16px;
}

.shortlist-head {
  margin-bottom: 0;
}

.shortlist-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.shortlist-overview div {
  min-height: 92px;
  padding: 16px;
  border: 1px solid #e2ecfb;
  border-radius: 12px;
  background: #f8fbff;
}

.shortlist-overview span {
  color: #73829a;
  font-size: 13px;
  font-weight: 700;
}

.shortlist-overview strong {
  display: block;
  margin: 7px 0 3px;
  color: #10203f;
  font-size: 28px;
  line-height: 1;
}

.shortlist-overview small {
  color: #8793a7;
}

.shortlist-grid {
  display: grid;
  gap: 12px;
  min-height: 120px;
}

.shortlist-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 18px;
  border: 1px solid #e2ecfb;
  border-radius: 12px;
  background: #fff;
  transition: border-color .18s ease, background-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.shortlist-card:hover {
  transform: translateY(-1px);
  border-color: #9ec7ff;
  box-shadow: 0 12px 28px rgba(48, 111, 209, 0.09);
}

.shortlist-card.selected {
  border-color: #1769f6;
  background: #f4f8ff;
}

.shortlist-card.comparing {
  box-shadow: inset 3px 0 0 #10b981;
}

.shortlist-card-main {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.shortlist-card-copy {
  min-width: 0;
}

.shortlist-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.shortlist-title-row h3 {
  overflow: hidden;
  margin: 0;
  color: #10203f;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tier-pill {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  background: #eef4ff;
  color: #1769f6;
  font-size: 12px;
  font-weight: 800;
}

.shortlist-card-copy p {
  margin: 8px 0 0;
  color: #56657a;
  font-size: 14px;
  line-height: 1.55;
}

.shortlist-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.shortlist-meta span {
  padding: 5px 9px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.shortlist-card.comparing .shortlist-meta span:last-child {
  background: #dcfce7;
  color: #15803d;
}

.shortlist-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.empty-state {
  padding: 56px 16px;
  text-align: center;
  color: #7a879a;
}

.empty-state.compact {
  padding: 44px 16px;
  border: 1px dashed #bfd3ff;
  border-radius: 16px;
  background: #f8fbff;
}

.empty-state h3 {
  margin: 0 0 8px;
  color: #10203f;
}

.empty-state p {
  margin: 0 0 18px;
}

.report-list {
  display: grid;
  gap: 12px;
}

.report-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 20px;
  gap: 16px;
  align-items: center;
  width: 100%;
  padding: 18px 20px;
  border: 1px solid #e5edf8;
  border-radius: 14px;
  background: #fbfdff;
  text-align: left;
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.report-card:hover {
  transform: translateY(-1px);
  border-color: #9ec7ff;
  box-shadow: 0 12px 28px rgba(48, 111, 209, 0.1);
}

.report-card strong {
  display: block;
  overflow: hidden;
  color: #10203f;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-card em {
  display: block;
  margin-top: 7px;
  color: #8793a7;
  font-style: normal;
  font-size: 13px;
}

.report-card small {
  color: #1769f6;
  font-weight: 800;
}

.detail-drawer {
  padding: 0 24px 28px;
}

.detail-drawer h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.detail-drawer p {
  margin: 0 0 12px;
  color: #64748b;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 18px;
}

.detail-tags span {
  padding: 5px 9px;
  border-radius: 5px;
  color: #1769f6;
  background: #eef4ff;
  font-weight: 700;
  font-size: 12px;
}

.detail-score-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.detail-score-grid div {
  border: 1px solid #e5edf8;
  border-radius: 8px;
  padding: 12px;
  background: #fbfdff;
}

.detail-score-grid small {
  display: block;
  color: #7a879a;
  margin-bottom: 4px;
}

.detail-score-grid strong {
  font-size: 22px;
}

.drawer-warning {
  border: 1px solid #ffd7a8;
  border-radius: 8px;
  padding: 12px;
  background: #fff8e8;
}

.drawer-warning p {
  color: #9a4d00;
  margin: 4px 0;
}

@media (max-width: 900px) {
  .mine-body { padding: 24px 16px 42px; }
  .mine-hero,
  .profile-summary-grid {
    grid-template-columns: 1fr;
  }
  .summary-strip,
  .profile-fields,
  .profile-form {
    grid-template-columns: 1fr;
  }
  .panel-head {
    flex-direction: column;
  }
  .panel-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .shortlist-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .shortlist-card {
    grid-template-columns: 1fr;
  }
  .shortlist-actions {
    justify-content: flex-start;
    padding-left: 40px;
  }
  .report-card {
    grid-template-columns: 1fr 20px;
  }
  .report-card small {
    grid-column: 1 / -1;
  }
}

@media (max-width: 560px) {
  .shortlist-overview {
    grid-template-columns: 1fr;
  }
  .shortlist-title-row {
    align-items: flex-start;
    flex-direction: column;
  }
  .shortlist-title-row h3 {
    white-space: normal;
  }
  .shortlist-actions {
    padding-left: 0;
  }
}
</style>
