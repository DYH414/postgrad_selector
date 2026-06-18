<template>
  <div class="prototype-page">
    <AppHeader :current-page="currentHeader" />

    <main class="result-layout" :class="{ 'result-layout--compare': activeTab === 'compare' }">
      <aside class="filter-sidebar" :class="{ 'shortlist-sidebar': activeTab === 'compare' }">
        <div class="sidebar-title">
          <strong>筛选条件</strong>
          <button type="button" @click="resetFilters">清空</button>
        </div>
        <div class="filter-group">
          <label>搜索关键词</label>
          <el-input v-model="filterForm.keyword" placeholder="学校/专业/学院名称" clearable @clear="applyFilters" @keyup.enter="applyFilters"/>
        </div>
        <div class="filter-group">
          <label>预计初试总分</label>
          <el-input v-model.number="filterForm.score" placeholder="请输入分数">
            <template #append>分</template>
          </el-input>
        </div>
        <div class="filter-group">
          <label>考试组合</label>
          <el-select v-model="filterForm.exam" placeholder="请选择考试组合">
            <el-option label="11408（数学一 + 英语一 + 408）" value="11408" />
            <el-option label="22408（数学二 + 英语二 + 408）" value="22408" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>地区</label>
          <el-select v-model="filterForm.regions" multiple collapse-tags clearable filterable placeholder="不限地区">
            <el-option v-for="region in regionOptions" :key="region" :label="region" :value="region" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>专业方向</label>
          <el-select v-model="filterForm.majorDirections" multiple collapse-tags clearable filterable placeholder="不限专业方向">
            <el-option label="计算机科学与技术（081200）" value="081200" />
            <el-option label="软件工程（083500）" value="083500" />
            <el-option label="电子信息-计算机方向（085404）" value="085404" />
            <el-option label="电子信息-软件工程方向（085405）" value="085405" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>筛选范围</label>
          <div class="range-buttons">
            <button :class="{ active: filterForm.scoreRange === 5 }" type="button" @click="filterForm.scoreRange = 5">均分+5</button>
            <button :class="{ active: filterForm.scoreRange === 10 }" type="button" @click="filterForm.scoreRange = 10">均分+10</button>
            <button :class="{ active: filterForm.scoreRange === 15 }" type="button" @click="filterForm.scoreRange = 15">均分+15</button>
            <button :class="{ active: filterForm.scoreRange === 20 }" type="button" @click="filterForm.scoreRange = 20">均分+20</button>
            <button :class="{ active: filterForm.scoreRange === null }" type="button" @click="filterForm.scoreRange = null">不限</button>
          </div>
        </div>
        <el-button class="filter-button" type="primary" :loading="filtering" @click="applyFilters">应用筛选</el-button>
        <div class="tip-box">
          <i class="el-icon-info"></i>
          <strong>小贴士</strong>
          <p>筛选结果不是唯一可报范围，复试线也不是最低录取分；N诺数据可能遗漏或错误，请结合院校官网复核。</p>
        </div>
      </aside>

      <section class="result-main" v-loading="loading">
        <div class="result-head">
          <div>
            <h1>{{ activeTab === 'compare' ? '对比与备选' : '筛选结果' }}</h1>
            <span v-if="activeTab !== 'compare'">共 {{ filteredTotal }} 个候选</span>
          </div>
          <div class="chips">
            <span v-for="chip in headerChips" :key="chip.key">
              {{ chip.label }}
              <button v-if="chip.clearKey" type="button" @click="clearHeaderChip(chip.clearKey)">
                <i class="el-icon-close"></i>
              </button>
            </span>
          </div>
          <el-button v-if="activeTab !== 'compare'" plain type="primary" icon="el-icon-download">导出结果</el-button>
        </div>

        <div class="data-alert">
          <i class="el-icon-warning"></i>
          复试线不是最低录取分；筛选学校不代表只有这些学校可以报。当前数据主要来源于 N诺（第三方整理），可能存在遗漏或错误，请以院校官网为准。
          <button type="button" @click="showCompareTab">查看说明 <i class="el-icon-info"></i></button>
        </div>

        <template v-if="activeTab === 'compare'">
          <section class="compare-panel">
            <div class="tabs">
              <button
                type="button"
                :class="{ active: activeCompareTab === 'compare' }"
                @click="activeCompareTab = 'compare'">
                院校对比
              </button>
              <button
                type="button"
                :class="{ active: activeCompareTab === 'backup' }"
                @click="activeCompareTab = 'backup'">
                我的备选
              </button>
            </div>
            <div v-if="activeCompareTab === 'compare'" class="compare-actions">
              <span>已选择 {{ compareSchools.length }} 个项目</span>
              <div>
                <el-button v-if="compareSchools.length" size="small" @click="clearCompare">清空对比</el-button>
              </div>
            </div>
            <table v-if="activeCompareTab === 'compare' && compareSchools.length" class="compare-table">
              <tbody>
                <tr v-for="row in compareRows" :key="row.label">
                  <th>{{ row.label }}</th>
                  <td v-for="school in compareSchools" :key="school.compareKey + row.label">
                    <template v-if="row.type === 'name'"><strong>{{ school.name }}</strong></template>
                    <template v-else-if="row.type === 'diff'">
                      <span class="score-diff" :class="{ positive: school[row.key] >= 0, negative: school[row.key] < 0 }">
                        {{ school[row.key] > 0 ? '+' : '' }}{{ school[row.key] }}
                      </span>
                    </template>
                    <template v-else-if="row.type === 'confidence'">
                      <span
                        class="grade completeness-tip"
                        :class="'grade-' + school.confidence.toLowerCase()"
                        tabindex="0"
                        :data-tip="dataCompletenessText(school.confidence)">
                        完整度 {{ school.confidence }}
                      </span>
                    </template>
                    <template v-else-if="row.type === 'source'">
                      <a v-if="school.sourceUrl" class="source-link compare-source" :href="school.sourceUrl" target="_blank" rel="noopener noreferrer">N诺来源</a>
                      <span v-else>-</span>
                    </template>
                    <template v-else-if="row.type === 'action'"><button class="detail-link" type="button" @click="openDetail(school.programId)">查看详情</button></template>
                    <template v-else>{{ school[row.key] }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else-if="activeCompareTab === 'compare'" class="empty-group compare-empty">
              <template v-if="backupPreviewItems.length">
                <strong>从我的备选加入对比</strong>
                <p>多选刚刚加入备选的学校，再一起加入对比列表。</p>
                <div class="backup-quick-list">
                  <button
                    v-for="item in backupPreviewItems"
                    :key="item.programId"
                    type="button"
                    :class="{ selected: isBackupSelectedForCompare(item.programId) }"
                    @click="toggleBackupCompareSelection(item.programId)">
                    <i :class="isBackupSelectedForCompare(item.programId) ? 'el-icon-check' : 'el-icon-plus'"></i>
                    <span>{{ item.name }}</span>
                  </button>
                </div>
                <el-button
                  class="backup-batch-button"
                  type="primary"
                  size="small"
                  :disabled="selectedBackupCompareIds.length === 0"
                  @click="addSelectedBackupsToCompare">
                  加入对比（{{ selectedBackupCompareIds.length }}）
                </el-button>
              </template>
              <template v-else>
                暂无对比项目，请先在“我的备选”中加入对比。
              </template>
            </div>
            <p v-if="activeCompareTab === 'compare' && compareSchools.length" class="table-note">注：拟录取区间为近三年拟录取总分范围，仅供参考；招生人数含推免，具体以院校当年公告为准。</p>

            <div v-if="activeCompareTab === 'backup'" class="backup-overview">
              <div>
                <span>备选池</span>
                <strong>{{ backupTotal }}</strong>
                <small>已加入的院校专业</small>
              </div>
              <p>把备选方案加入对比后，可以横向查看分数、名额、数据完整度和来源。</p>
            </div>
            <div v-if="activeCompareTab === 'backup' && backupGroups.length" class="backup-grid">
              <div v-for="group in backupGroups" :key="group.name" class="backup-card" :class="group.theme">
                <div class="backup-title">
                  <div>
                    <strong>{{ group.name }}（{{ group.items.length }}）</strong>
                    <p>{{ group.desc }}</p>
                  </div>
                  <i class="el-icon-plus"></i>
                </div>
                <ul class="backup-item-list">
                  <li v-for="item in group.items" :key="item.name" class="backup-item-card">
                    <div>
                      <span>{{ item.name }}</span>
                      <p class="backup-item-meta">
                        <em>{{ item.grade }}</em>
                        <small>{{ item.degreeType }}</small>
                      </p>
                    </div>
                    <div class="backup-actions">
                      <button type="button" @click="addBackupToCompare(item)">
                        {{ isInCompare(item.programId) ? '已加入' : '加入对比' }}
                      </button>
                    </div>
                  </li>
                </ul>
                <button type="button" @click="router.push('/favorites')">管理备选 <i class="el-icon-right"></i></button>
              </div>
            </div>
            <div v-else-if="activeCompareTab === 'backup'" class="empty-group">
              暂无备选项目，请先在推荐结果页加入备选。
            </div>
          </section>
        </template>

        <div v-if="activeTab !== 'compare' && !hasResult" class="empty-result">
          <i class="el-icon-document"></i>
          <strong>还没有筛选结果</strong>
          <p>请先填写报考条件开始筛选；系统不会用演示院校替代你的真实结果。</p>
          <el-button type="primary" @click="router.push('/recommend')">去开始筛选</el-button>
        </div>

        <div v-if="activeTab !== 'compare' && hasResult && result.aiAnalysis" class="ai-analysis-card">
          <div class="ai-card-header">
            <span class="ai-icon"><i class="el-icon-cpu"></i></span>
            <strong>AI 推荐解读</strong>
            <em>由 DeepSeek 生成，仅供参考</em>
          </div>
          <div class="ai-card-body">
            <p v-for="(para, idx) in aiParagraphs" :key="idx">{{ para }}</p>
          </div>
        </div>

        <template v-if="activeTab !== 'compare' && hasResult && !result.aiAnalysis">
          <div v-if="filteredItems.length === 0" class="empty-group">暂无匹配结果</div>
          <div v-else class="school-grid">
            <article v-for="school in filteredItems" :key="school.cardKey" class="school-card">
                <div class="card-top">
                  <div class="school-seal">{{ school.schoolName.slice(0, 1) }}</div>
                  <div>
                    <h3>{{ school.schoolName }} <small v-if="school.badge">{{ school.badge }}</small></h3>
                    <p>
                      <span :class="{ 'pending-field': isPendingCollege(school.collegeName) }">{{ displayCollegeName(school.collegeName) }}</span>
                      / {{ school.programName }}
                    </p>
                    <button
                      v-if="school.directionCount > 1"
                      class="direction-count"
                      type="button"
                      @click="toggleSchoolDirections(school.cardKey)">
                      共 {{ school.directionCount }} 个符合方向
                    </button>
                    <div class="school-meta-line">
                      <span>{{ school.exam }} | {{ school.province || '-' }}</span>
                      <em v-if="school.dataYear" class="data-year-badge">{{ school.dataYear }}年数据</em>
                      <em v-else class="data-year-badge muted">年份待补</em>
                    </div>
                  </div>
                  <button
                    class="star-btn"
                    :class="{ favorited: isFavorited(school), loading: favoriteLoadingIds.includes(favoriteKey(school)) }"
                    type="button"
                    @click="handleFavorite(school)"
                    :aria-label="isFavorited(school) ? '移出备选' : '加入备选'">
                    <i :class="isFavorited(school) ? 'el-icon-star-on' : 'el-icon-star-off'"></i>
                  </button>
                </div>

                <div class="score-line">
                  <div class="score-metric" tabindex="0" data-tip="该专业近年拟录取名单中的平均总分，是本次筛选范围的主依据。">
                    <span>拟录取均分</span><strong>{{ school.avgScore || '-' }}</strong>
                  </div>
                  <div class="score-metric" tabindex="0" data-tip="你的预计初试总分减去拟录取均分，负数代表低于历史均分。">
                    <span>均分差距</span><strong :class="{ positive: school.avgScoreGap >= 0, negative: school.avgScoreGap < 0 }">{{ formatDiff(school.avgScoreGap) }}</strong>
                  </div>
                  <div class="score-metric" tabindex="0" data-tip="该专业近年拟录取名单中的最低总分，仅作风险边界参考。">
                    <span>最低录取分</span><strong>{{ school.admissionLow || '-' }}</strong>
                  </div>
                  <div class="score-metric" tabindex="0" data-tip="该专业近年拟录取最低分到最高分的范围，仅作历史参考。">
                    <span>拟录取区间</span><strong>{{ school.range }}</strong>
                  </div>
                  <div class="score-metric" tabindex="0" data-tip="优先展示该专业统考名额；缺少统考名额时展示计划人数，计划人数可能包含推免。">
                    <span>{{ school.quotaDisplay.label }}</span>
                    <strong>
                      {{ school.quotaDisplay.value }}
                      <small>{{ school.quotaDisplay.hint }}</small>
                    </strong>
                  </div>
                </div>

                <div class="tags">
                  <em :class="'grade-' + school.confidence.toLowerCase()">完整度{{ school.confidence }}</em>
                </div>

                <p v-if="school.note" class="card-note">{{ school.note }}</p>
                <a
                  v-if="school.sourceUrl"
                  class="source-link"
                  :href="school.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer">
                  <i class="el-icon-link"></i> N诺来源
                </a>
                <span v-else class="source-link source-missing">
                  <i class="el-icon-link"></i> N诺数据（来源待补）
                </span>
                <div class="card-actions">
                  <button class="detail-link" type="button" @click="openDetail(school.programId)">查看详情</button>
                  <button
                    class="backup-card-btn"
                    type="button"
                    :disabled="favoriteLoadingIds.includes(favoriteKey(school))"
                    @click="handleFavorite(school)">
                    {{ isFavorited(school) ? '已加入备选' : '加入备选' }}
                  </button>
                </div>
                <div v-if="school.directionCount > 1 && isSchoolExpanded(school.cardKey)" class="direction-list">
                  <div v-for="direction in school.directions" :key="direction.cardKey" class="direction-row">
                    <div class="direction-main">
                      <strong>
                        <span :class="{ 'pending-field': isPendingCollege(direction.collegeName) }">{{ displayCollegeName(direction.collegeName) }}</span>
                        / {{ direction.programName }}
                      </strong>
                      <small>{{ direction.exam }} | {{ direction.dataYear ? `${direction.dataYear}年数据` : '年份待补' }}</small>
                    </div>
                    <div class="direction-stats">
                      <span>均分 {{ direction.avgScore || '-' }}</span>
                      <span :class="{ positive: direction.avgScoreGap >= 0, negative: direction.avgScoreGap < 0 }">差距 {{ formatDiff(direction.avgScoreGap) }}</span>
                      <span>区间 {{ direction.range }}</span>
                      <span>{{ direction.quotaDisplay.label }} {{ direction.quotaDisplay.value }}（{{ direction.quotaDisplay.hint }}）</span>
                    </div>
                    <div class="direction-actions">
                      <button
                        type="button"
                        :disabled="favoriteLoadingIds.includes(favoriteKey(direction))"
                        @click="handleFavorite(direction)">
                        {{ isFavorited(direction) ? '移出备选' : '加入备选' }}
                      </button>
                      <button type="button" @click="openDetail(direction.programId)">详情</button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
        </template>
      </section>
    </main>

    <el-drawer
      title="院校专业详情"
      v-model="detailVisible"
      size="520px"
      append-to-body>
      <div class="detail-drawer" v-loading="detailLoading">
        <template v-if="detail">
          <h2>{{ detail.basic.schoolName }}</h2>
          <p>{{ detail.basic.collegeName }} / {{ detail.basic.programName }}</p>
          <div class="detail-tags">
            <span>{{ detail.basic.examCombo || '-' }}</span>
            <span>{{ detail.dataCompleteness.label }}</span>
          </div>
          <div class="detail-score-grid">
            <div><small>复试线</small><strong>{{ detail.recommendationOverview.scoreLine }}</strong></div>
            <div><small>最低录取分</small><strong>{{ detail.recommendationOverview.admissionLow || '-' }}</strong></div>
            <div><small>与最低录取分差距</small><strong>{{ formatDiff(detail.recommendationOverview.admissionLowGap) }}</strong></div>
            <div><small>拟录取区间</small><strong>{{ detail.recommendationOverview.admissionRangeLabel || '-' }}</strong></div>
            <div><small>与拟录取均分差距</small><strong>{{ formatDiff(detail.recommendationOverview.avgScoreGap) }}</strong></div>
          </div>
          <div class="drawer-warning">
            <p v-for="warning in detail.riskWarnings" :key="warning">· {{ warning }}</p>
          </div>
          <a
            v-if="detail.source && detail.source.sourceUrl"
            class="drawer-source-link"
            :href="detail.source.sourceUrl"
            target="_blank"
            rel="noopener noreferrer">
            <i class="el-icon-link"></i> 查看 N诺来源
          </a>
          <table class="trend-table">
            <thead>
              <tr><th>年份</th><th>复试线</th><th>拟录取均分</th><th>录取区间</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in detail.trends" :key="item.year">
                <td>{{ item.year }}</td>
                <td>{{ item.scoreLine }}</td>
                <td>{{ item.avgAdmittedScore || '-' }}</td>
                <td>{{ item.admissionLow || '-' }} - {{ item.admissionHigh || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </div>
    </el-drawer>

  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { generateRecommendation, getRecommendationOptions, getRecommendationResult } from '@/api/recommendation'
import { comparePrograms, getProgramDetail, searchPrograms } from '@/api/programs'
import { addFavorite, listFavorites, removeFavorite } from '@/api/favorites'
import { COMPARE_STORAGE_KEY, COMPARE_SCORE_KEY, COMPARE_MAX_ITEMS } from '@/api/compare-constants'
import { getToken } from '@/api/request'
import { formatAdmissionQuota } from '@/utils/admissionDisplay.mjs'

const router = useRouter()
const route = useRoute()

const FILTER_STORAGE_PREFIX = 'app-results-filters'
const emptyFilters = () => ({
  keyword: '',
  score: 300,
  regions: [],
  exam: '11408',
  majorDirections: [],
  scoreRange: 15
})

const emptyResult = () => ({
  recommendationId: null,
  totalCandidates: 0,
  score: '-',
  exam: '-',
  region: '不限',
  majorDirections: [],
  studyMode: '不限',
  scoreRange: null,
  aiAnalysis: null,
  summary: null,
  globalWarnings: [],
  requestFilters: emptyFilters(),
  items: []
})

// --- data properties ---
const loading = ref(false)
const filtering = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const activeTab = ref(route.query.tab === 'compare' ? 'compare' : 'result')
const activeCompareTab = ref('compare')
const result = ref(emptyResult())
const favoriteProgramIds = ref([])
const favoriteLoadingIds = ref([])
const filterForm = ref(emptyFilters())
const appliedFilters = ref(emptyFilters())
const optionRegions = ref(['福建', '广东', '浙江', '上海'])
const compareRows = [
  { label: '学校', type: 'name' },
  { label: '专业', key: 'program' },
  { label: '考试组合', key: 'exam' },
  { label: '数据年份', key: 'dataYearLabel' },
  { label: '复试线', key: 'score' },
  { label: '最低录取分', key: 'admissionLow' },
  { label: '与最低录取分差距', type: 'diff', key: 'admissionLowGap' },
  { label: '拟录取区间（总分）', key: 'range' },
  { label: '拟录取均分', key: 'avgScore' },
  { label: '与拟录取均分差距', type: 'diff', key: 'avgScoreGap' },
  { label: '招生人数（含推免）', key: 'quota' },
  { label: 'N诺数据完整度', type: 'confidence' },
  { label: 'N诺来源', type: 'source' },
  { label: '操作', type: 'action' }
]
const compareSchools = ref([])
const backupGroups = ref([])
const backupLoaded = ref(false)
const selectedBackupCompareIds = ref([])
const expandedSchoolKeys = ref([])

// --- computed properties ---
const currentHeader = computed(() => {
  return activeTab.value === 'compare' ? 'compare' : 'results'
})

const hasResult = computed(() => {
  return (result.value.items || []).length > 0
})

const headerChips = computed(() => {
  const chips = [
    { key: 'score', label: `分数 ${result.value.score}` },
    { key: 'exam', label: `考试组合 ${result.value.exam}` },
    { key: 'region', label: `地区 ${regionFilterLabel.value}` },
  ]
  if (result.value.majorDirections && result.value.majorDirections.length) {
    chips.push({ key: 'majorDirections', label: `专业方向 ${result.value.majorDirections.join('、')}` })
  }
  if (filterForm.value.keyword) {
    chips.push({ key: 'keyword', label: `搜索 "${filterForm.value.keyword}"` })
  }
  chips.push({
    key: 'scoreRange',
    label: result.value.scoreRange == null ? '筛选范围 不限' : `筛选范围 均分+${result.value.scoreRange}`
  })
  return chips
})

const regionFilterLabel = computed(() => {
  return appliedFilters.value.regions.length ? appliedFilters.value.regions.join('、') : result.value.region
})

const regionOptions = computed(() => {
  const regions = new Set()
  optionRegions.value.forEach(region => {
    if (region) regions.add(region)
  })
  ;(result.value.requestFilters && result.value.requestFilters.regions || []).forEach(region => {
    if (region) regions.add(region)
  })
  ;(result.value.groups || []).forEach(group => {
    ;(group.schools || []).forEach(school => {
      if (school.province) regions.add(school.province)
    })
  })
  return Array.from(regions).sort()
})

const keywordFilter = computed(() => (filterForm.value.keyword || '').trim().toLowerCase())

const filteredItems = computed(() => {
  const kw = keywordFilter.value
  const items = result.value.items || []
  const filtered = !kw ? items : items.filter(school =>
    (school.schoolName || '').toLowerCase().includes(kw) ||
    (school.collegeName || '').toLowerCase().includes(kw) ||
    (school.programName || '').toLowerCase().includes(kw) ||
    (school.programCode || '').toLowerCase().includes(kw)
  )
  return groupSchools(filtered)
})

const backupTotal = computed(() => {
  return backupGroups.value.reduce((total, group) => total + group.items.length, 0)
})

const backupProgramIds = computed(() => {
  return new Set(backupGroups.value
    .flatMap(group => group.items)
    .map(item => Number(item.programId))
    .filter(Boolean))
})

const backupPreviewItems = computed(() => {
  return backupGroups.value
    .flatMap(group => group.items)
    .filter(item => item.programId && !isInCompare(item.programId))
    .slice(0, 6)
})

const filteredTotal = computed(() => filteredItems.value.length)

const aiParagraphs = computed(() => {
  const text = result.value.aiAnalysis
  if (!text) return []
  return text.split('\n').filter(p => p.trim()).map(p => p.replace(/^#+\s*/, ''))
})

// --- methods ---

function loadOptions() {
  getRecommendationOptions().then(res => {
    const data = res.data || {}
    if (data.regions && data.regions.length) {
      optionRegions.value = data.regions
    }
  }).catch(() => {})
}

function loadResult() {
  const id = route.query.id || window.sessionStorage.getItem('app-recommend-id')
  const cached = window.sessionStorage.getItem('app-recommend-result')
  if (cached) {
    try {
      const parsed = JSON.parse(cached)
      result.value = normalizeResult(parsed)
      restoreFilters(parsed)
      if (activeTab.value === 'compare') {
        loadCompare()
        loadBackupGroups()
      }
    } catch (e) {}
  }
  if (!id) return
  loading.value = true
  getRecommendationResult(id).then(res => {
    result.value = normalizeResult(res.data || {})
    restoreFilters(res.data || {})
    window.sessionStorage.setItem('app-recommend-result', JSON.stringify(res.data || {}))
    if (activeTab.value === 'compare') {
      loadCompare()
      loadBackupGroups()
    }
  }).finally(() => { loading.value = false })
}

function normalizeResult(data) {
  if (!data) return emptyResult()
  const request = data.request || {}
  // 兼容旧 groups 格式和新 items 扁平格式
  let items = []
  if (data.items && data.items.length) {
    items = data.items.map(normalizeSchool)
  } else if (data.groups) {
    items = (data.groups || []).flatMap(group => (group.items || group.schools || []).map(normalizeSchool))
  }
  return {
    recommendationId: data.recommendationId,
    totalCandidates: data.totalCandidates || items.length,
    score: request.estimatedScore || data.score || 300,
    exam: request.examCombo || data.exam || '22408',
    region: request.targetRegions && request.targetRegions.length ? request.targetRegions.join('、') : '不限',
    majorDirections: Array.isArray(request.majorDirections) ? request.majorDirections : [],
    studyMode: studyModeText(request.studyMode || data.studyMode),
    scoreRange: request.scoreRange != null ? request.scoreRange : null,
    aiAnalysis: data.aiAnalysis || null,
    summary: data.summary || null,
    globalWarnings: data.globalWarnings || [],
    requestFilters: defaultFiltersFromRequest(request),
    items
  }
}

function normalizeSchool(item) {
  return {
    programId: item.programId,
    cardKey: item.programId
      ? `program:${item.programId}`
      : `local:${item.schoolName}:${item.collegeName}:${item.programName}:${item.examCombo}`,
    schoolName: item.schoolName,
    badge: schoolBadge(item),
    tier: item.schoolTier,
    is985: item.is985,
    is211: item.is211,
    isDoubleFirst: item.isDoubleFirst,
    collegeName: item.collegeName,
    programName: item.programName,
    examCombo: item.examCombo,
    exam: item.examCombo || '-',
    dataYear: item.dataYear || item.year || null,
    province: item.province,
    scoreLine: item.scoreLine,
    scoreLineGap: item.scoreLineGap,
    unifiedExamQuota: item.unifiedExamQuota,
    planCount: item.planCount,
    quotaDisplay: formatAdmissionQuota(item),
    admissionLow: item.admissionLow,
    admissionLowGap: item.admissionLowGap,
    range: item.admissionRangeLabel || '-',
    avgScore: item.avgAdmittedScore || '-',
    avgScoreGap: item.avgScoreGap,
    sourceUrl: item.sourceUrl,
    sourceTitle: item.sourceTitle,
    sourceOwner: item.sourceOwner,
    confidence: item.dataCompleteness || 'C',
    tag: item.fitLevelLabel || '数据不足',
    fitLevelClass: item.fitLevel || 'insufficient_data',
    hasAdmissionRange: item.admissionLow !== null && item.admissionLow !== undefined && item.admissionHigh !== null && item.admissionHigh !== undefined,
    note: cardNote(item),
    star: item.fitLevel === 'safe' ? 3 : item.fitLevel === 'steady' ? 4 : 5
  }
}

function groupSchools(items) {
  const grouped = new Map()
  items.forEach(item => {
    const key = item.schoolName || item.cardKey
    if (!grouped.has(key)) {
      grouped.set(key, [])
    }
    grouped.get(key).push(item)
  })
  return Array.from(grouped.values()).map(directions => {
    const sorted = [...directions].sort(directionComparator)
    const primary = sorted[0]
    return {
      ...primary,
      cardKey: `school:${primary.schoolName}:${primary.examCombo}:${primary.province || ''}`,
      directionCount: sorted.length,
      directions: sorted
    }
  })
}

function directionComparator(a, b) {
  const gapA = Number.isFinite(Number(a.avgScoreGap)) ? Number(a.avgScoreGap) : 999
  const gapB = Number.isFinite(Number(b.avgScoreGap)) ? Number(b.avgScoreGap) : 999
  if (gapA !== gapB) return gapA - gapB
  return `${a.collegeName || ''}${a.programName || ''}`.localeCompare(`${b.collegeName || ''}${b.programName || ''}`, 'zh-CN')
}

function schoolBadge(item) {
  const badges = []
  if (item.is985) badges.push('985')
  if (item.is211) badges.push('211')
  if (item.isDoubleFirst) badges.push('双一流')
  if (!badges.length && item.schoolTier) badges.push(tierBadgeText(item.schoolTier))
  return badges.join(' ')
}

function tierBadgeText(value) {
  const normalized = String(value || '').trim()
  const map = {
    '985': '985',
    '211': '211',
    'DOUBLE_FIRST': '双一流',
    'double_first': '双一流'
  }
  return map[normalized] || ''
}

function isPendingCollege(name) {
  return !name || name === '未知学院'
}

function displayCollegeName(name) {
  return isPendingCollege(name) ? '学院待核验' : name
}

function studyModeText(value) {
  const map = { any: '不限', full_time: '全日制', part_time: '非全日制' }
  return map[value] || value || '不限'
}

function tierLabel(value) {
  const map = {
    985: '985',
    211: '211',
    double_first: '双一流',
    ordinary: '普通院校'
  }
  return map[value] || '全部层次'
}

function admissionRangeLabel(value) {
  const map = {
    yes: '有',
    no: '暂无'
  }
  return map[value] || '不限'
}

function cardNote(item) {
  const repeatedWarnings = [
    '复试线不是最低录取分。',
    '推荐学校不代表只有这些学校可以报。',
    '筛选学校不代表只有这些学校可以报。'
  ]
  const warnings = (item.warnings || []).filter(text => !repeatedWarnings.includes(text))
  if (warnings.length) return warnings.join(' ')
  return item.dataCompletenessText || ''
}

function filterStorageKey(data = {}) {
  const id = route.query.id || data.recommendationId || result.value.recommendationId || window.sessionStorage.getItem('app-recommend-id') || 'latest'
  return `${FILTER_STORAGE_PREFIX}:${id}`
}

function sanitizeFilters(filters = {}) {
  const normalized = emptyFilters()
  Object.keys(normalized).forEach(key => {
    if (filters[key] !== undefined) {
      if (Array.isArray(normalized[key])) {
        normalized[key] = Array.isArray(filters[key]) ? filters[key].filter(Boolean).map(String) : [String(filters[key])].filter(Boolean)
      } else if (key === 'score') {
        normalized[key] = Number(filters[key]) || normalized[key]
      } else if (key === 'scoreRange') {
        normalized[key] = filters[key] === null ? null : Number(filters[key])
      } else if (filters[key] !== null) {
        normalized[key] = String(filters[key])
      }
    }
  })
  if (!normalized.regions.length && filters.region) {
    normalized.regions = [String(filters.region)]
  }
  return normalized
}

function defaultFiltersFromRequest(request = {}) {
  const filters = emptyFilters()
  if (request.estimatedScore) filters.score = Number(request.estimatedScore)
  if (request.examCombo) filters.exam = request.examCombo
  if (Array.isArray(request.targetRegions) && request.targetRegions.length) {
    filters.regions = request.targetRegions.filter(Boolean).map(String)
  }
  if (Array.isArray(request.majorDirections) && request.majorDirections.length) {
    filters.majorDirections = request.majorDirections.filter(Boolean).map(String)
  }
  if (request.scoreRange !== undefined) filters.scoreRange = request.scoreRange === null ? null : Number(request.scoreRange)
  return filters
}

function restoreFilters(data = {}) {
  const defaults = defaultFiltersFromRequest(data.request || {})
  let filters = defaults
  try {
    const saved = window.localStorage.getItem(filterStorageKey(data))
    if (saved) {
      const parsed = JSON.parse(saved)
      const savedFilters = parsed && parsed.filters ? parsed.filters : parsed
      const isLegacyEmpty = !parsed.version && Object.values(sanitizeFilters(savedFilters)).every(value => Array.isArray(value) ? value.length === 0 : value === '')
      if (!isLegacyEmpty) {
        filters = {
          ...defaults,
          ...sanitizeFilters(savedFilters)
        }
        if (Object.prototype.hasOwnProperty.call(data.request || {}, 'scoreRange')) {
          filters.scoreRange = defaults.scoreRange
        } else {
          // 服务器省略了 scoreRange（原值为 null），保留用户当前选择
          filters.scoreRange = filterForm.value.scoreRange
        }
      }
    }
  } catch (e) {}
  filterForm.value = { ...filters }
  appliedFilters.value = { ...filters }
}

function persistFilters() {
  try {
    window.localStorage.setItem(filterStorageKey(), JSON.stringify({
      version: 3,
      filters: appliedFilters.value,
      updatedAt: Date.now()
    }))
  } catch (e) {}
}

function applyFilters() {
  if (!filterForm.value.score) {
    ElMessage.warning('请输入预计初试总分')
    return
  }
  const filters = sanitizeFilters(filterForm.value)
  filtering.value = true
  loading.value = true
  const payload = {
    estimatedScore: filters.score,
    examCombo: filters.exam,
    targetRegions: filters.regions,
    majorDirections: filters.majorDirections,
    riskPreference: 'balanced',
    scoreRange: filters.scoreRange,
    includeIncompleteData: true,
    pageSizePerGroup: 12
  }
  generateRecommendation(payload).then(res => {
    const data = res.data || {}
    result.value = normalizeResult(data)
    restoreFilters(data)
    appliedFilters.value = filters
    filterForm.value = { ...filters }
    persistFilters()
    window.sessionStorage.setItem('app-recommend-result', JSON.stringify(data))
    if (data.recommendationId) {
      window.sessionStorage.setItem('app-recommend-id', data.recommendationId)
      router.replace({ path: '/results', query: { ...route.query, id: data.recommendationId } }).catch(() => {})
    }
    ElMessage.success('筛选结果已更新')
  }).finally(() => {
    filtering.value = false
    loading.value = false
  })
}

function clearHeaderChip(key) {
  if (!Object.prototype.hasOwnProperty.call(filterForm.value, key)) return
  filterForm.value[key] = Array.isArray(filterForm.value[key]) ? [] : ''
  applyFilters()
}

function resetFilters() {
  filterForm.value = emptyFilters()
  applyFilters()
}

function isSchoolExpanded(cardKey) {
  return expandedSchoolKeys.value.includes(cardKey)
}

function toggleSchoolDirections(cardKey) {
  expandedSchoolKeys.value = isSchoolExpanded(cardKey)
    ? expandedSchoolKeys.value.filter(key => key !== cardKey)
    : [...expandedSchoolKeys.value, cardKey]
}

function matchSchoolFilters(school, filters) {
  if (filters.regions.length && !filters.regions.includes(school.province)) return false
  if (filters.exam && school.examCombo !== filters.exam) return false
  if (filters.confidence && school.confidence !== filters.confidence) return false
  if (filters.hasAdmissionRange === 'yes' && !school.hasAdmissionRange) return false
  if (filters.hasAdmissionRange === 'no' && school.hasAdmissionRange) return false
  if (filters.tier === '985' && !school.is985) return false
  if (filters.tier === '211' && !school.is211) return false
  if (filters.tier === 'double_first' && !school.isDoubleFirst) return false
  if (filters.tier === 'ordinary' && (school.is985 || school.is211 || school.isDoubleFirst)) return false
  return true
}

function loadCompare() {
  if (!backupLoaded.value) {
    compareSchools.value = []
    return
  }
  let ids = []
  if (route.query.programIds) {
    ids = String(route.query.programIds).split(',').filter(Boolean).map(Number).slice(0, COMPARE_MAX_ITEMS)
  } else {
    try {
      const stored = JSON.parse(localStorage.getItem(COMPARE_STORAGE_KEY) || '[]')
      ids = stored.filter(Boolean).slice(0, COMPARE_MAX_ITEMS)
    } catch (e) {
      ids = []
    }
  }
  ids = ids.filter(id => backupProgramIds.value.has(Number(id)))
  localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(ids))
  if (!ids.length) {
    compareSchools.value = []
    return
  }
  const rawScore = route.query.score
    || localStorage.getItem(COMPARE_SCORE_KEY)
    || result.value.score
  const estimatedScore = (rawScore && rawScore !== '-' && !isNaN(Number(rawScore))) ? Number(rawScore) : 300
  comparePrograms({ programIds: ids.join(','), estimatedScore }).then(res => {
    const items = (res.data && res.data.items) || []
    compareSchools.value = items.map(item => {
      const school = normalizeSchool(item)
      return {
        programId: school.programId,
        compareKey: school.programId
          ? `program:${school.programId}`
          : `local:${school.schoolName}:${school.collegeName}:${school.programName}:${school.examCombo}`,
        name: school.schoolName,
        program: school.programName,
        exam: school.exam,
        dataYear: school.dataYear,
        dataYearLabel: school.dataYear ? `${school.dataYear}年` : '年份待补',
        score: school.scoreLine,
        scoreLineGap: school.scoreLineGap,
        admissionLow: school.admissionLow,
        admissionLowGap: school.admissionLowGap,
        range: school.range,
        avgScore: school.avgScore,
        avgScoreGap: school.avgScoreGap,
        quota: item.planCount || '-',
        sourceUrl: item.sourceUrl,
        confidence: school.confidence,
        star: school.star
      }
    })
  })
}

function clearCompare() {
  localStorage.removeItem(COMPARE_STORAGE_KEY)
  localStorage.removeItem(COMPARE_SCORE_KEY)
  compareSchools.value = []
  selectedBackupCompareIds.value = []
  if (route.query.programIds) {
    const nextQuery = { ...route.query }
    delete nextQuery.programIds
    router.replace({ path: route.path, query: nextQuery })
  }
  ElMessage.success('已清空对比列表')
}

function isInCompare(programId) {
  const id = Number(programId)
  return !!id && compareSchools.value.some(school => Number(school.programId) === id)
}

function isBackupSelectedForCompare(programId) {
  const id = Number(programId)
  return !!id && selectedBackupCompareIds.value.includes(id)
}

function toggleBackupCompareSelection(programId) {
  const id = Number(programId)
  if (!id || isInCompare(id)) return
  selectedBackupCompareIds.value = isBackupSelectedForCompare(id)
    ? selectedBackupCompareIds.value.filter(item => item !== id)
    : [...selectedBackupCompareIds.value, id]
}

function addSelectedBackupsToCompare() {
  const selected = selectedBackupCompareIds.value.filter(id => backupProgramIds.value.has(Number(id)) && !isInCompare(id))
  if (!selected.length) return
  const current = compareSchools.value.map(school => Number(school.programId)).filter(Boolean)
  const available = Math.max(0, COMPARE_MAX_ITEMS - current.length)
  if (available === 0) {
    ElMessage.warning(`最多选择 ${COMPARE_MAX_ITEMS} 个备选项目对比`)
    return
  }
  const adding = selected.slice(0, available)
  if (selected.length > available) {
    ElMessage.warning(`最多选择 ${COMPARE_MAX_ITEMS} 个备选项目对比，已加入前 ${available} 个`)
  }
  const next = Array.from(new Set([...current, ...adding]))
  localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(next))
  selectedBackupCompareIds.value = []
  const nextQuery = { ...route.query }
  delete nextQuery.programIds
  router.replace({ path: route.path, query: nextQuery })
  loadCompare()
  activeCompareTab.value = 'compare'
}

function addBackupToCompare(item) {
  const programId = Number(item && item.programId)
  if (!programId) return
  if (isInCompare(programId)) {
    activeCompareTab.value = 'compare'
    return
  }
  const current = compareSchools.value.map(school => Number(school.programId)).filter(Boolean)
  if (current.length >= COMPARE_MAX_ITEMS) {
    ElMessage.warning(`最多选择 ${COMPARE_MAX_ITEMS} 个备选项目对比`)
    return
  }
  const next = [...current, programId]
  localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(next))
  const nextQuery = { ...route.query }
  delete nextQuery.programIds
  router.replace({ path: route.path, query: nextQuery })
  loadCompare()
  activeCompareTab.value = 'compare'
}

function showCompareTab() {
  activeTab.value = 'compare'
  loadCompare()
  loadBackupGroups()
}

function loadBackupGroups() {
  backupLoaded.value = false
  listFavorites().then(res => {
    syncFavoriteIds(res.data || [])
    const items = (res.data || []).map(item => ({
      name: `${item.schoolName || item.school_name || '-'} · ${item.programName || item.program_name || '-'}`,
      grade: item.programCode || item.program_code || '已备选',
      degreeType: (item.degreeType || item.degree_type) === 'professional' ? '专硕' : '学硕',
      programId: item.programId || item.program_id
    }))
    backupGroups.value = items.length
      ? [{ name: '备选池', desc: '从筛选结果中加入备选的院校专业', theme: 'blue', items }]
      : []
    selectedBackupCompareIds.value = selectedBackupCompareIds.value
      .filter(id => backupProgramIds.value.has(Number(id)) && !isInCompare(id))
    backupLoaded.value = true
    loadCompare()
  }).catch(() => {
    backupGroups.value = []
    backupLoaded.value = true
  })
}

function loadFavoriteIds() {
  listFavorites().then(res => {
    syncFavoriteIds(res.data || [])
  }).catch(() => {})
}

function syncFavoriteIds(items) {
  favoriteProgramIds.value = Array.from(new Set((items || [])
    .map(item => Number(item.program_id || item.programId))
    .filter(Boolean)
    .map(id => `program:${id}`)))
}

function favoriteKey(school) {
  if (!school) return ''
  return school.programId ? `program:${school.programId}` : `local:${school.schoolName}:${school.programName}`
}

function isFavorited(school) {
  return favoriteProgramIds.value.includes(favoriteKey(school))
}

function handleFavorite(school) {
  const key = favoriteKey(school)
  if (!key || favoriteLoadingIds.value.includes(key)) return
  const programId = Number(school.programId)
  const favorited = isFavorited(school)
  if (!getToken() || !programId) {
    favoriteProgramIds.value = favorited
      ? favoriteProgramIds.value.filter(id => id !== key)
      : [...favoriteProgramIds.value, key]
    ElMessage({
      message: favorited ? '已移出备选' : '已加入备选，登录后可同步到我的备选',
      type: favorited ? 'success' : 'warning'
    })
    return
  }
  favoriteLoadingIds.value = [...favoriteLoadingIds.value, key]
  if (favorited) {
    favoriteProgramIds.value = favoriteProgramIds.value.filter(id => id !== key)
    removeFavorite(programId).then(res => {
      ElMessage.success(res.msg || '已移出备选')
      if (activeTab.value === 'compare') loadBackupGroups()
    }).catch(() => {
      favoriteProgramIds.value = [...favoriteProgramIds.value, key]
    }).finally(() => {
      favoriteLoadingIds.value = favoriteLoadingIds.value.filter(id => id !== key)
    })
  } else {
    favoriteProgramIds.value = [...favoriteProgramIds.value, key]
    addFavorite(programId).then(res => {
      ElMessage.success(res.msg || '已加入备选')
      if (activeTab.value === 'compare') loadBackupGroups()
    }).catch(() => {
      favoriteProgramIds.value = favoriteProgramIds.value.filter(id => id !== key)
    }).finally(() => {
      favoriteLoadingIds.value = favoriteLoadingIds.value.filter(id => id !== key)
    })
  }
}

function openDetail(programId) {
  if (!programId) return
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  const rawScore = result.value.score
  const score = (rawScore && rawScore !== '-' && !isNaN(Number(rawScore))) ? Number(rawScore) : undefined
  getProgramDetail(programId, score ? { estimatedScore: score } : {}).then(res => {
    detail.value = res.data
  }).catch(() => {
    detail.value = null
  }).finally(() => { detailLoading.value = false })
}

function formatDiff(value) {
  if (value === null || value === undefined) return '-'
  return (value > 0 ? '+' : '') + value
}

function dataCompletenessText(level) {
  const map = {
    A: '含复试线、拟录取区间、人数等字段',
    B: '含主要分数字段，部分字段缺失',
    C: '仅有复试线或基础字段'
  }
  return map[level] || '字段完整度待核验'
}

// --- watchers ---
watch(() => route.query.tab, (tab) => {
  activeTab.value = tab === 'compare' ? 'compare' : 'result'
  if (activeTab.value === 'compare') {
    loadCompare()
    loadBackupGroups()
  }
})

watch(() => route.query.programIds, () => {
  if (activeTab.value === 'compare') loadCompare()
})

watch(() => route.query.id, () => {
  loadResult()
})

watch(() => route.query.keyword, (newKw) => {
  filterForm.value.keyword = newKw || ''
  // Header 搜索跳转：无筛选结果时自动调搜索 API
  if (newKw && !hasResult.value) {
    searchPrograms(newKw, 50).then(res => {
      const data = res.data || {}
      const items = Array.isArray(data.items) ? data.items : (Array.isArray(data) ? data : [])
      const schools = items.map(normalizeSchool)
      if (schools.length > 0) {
        result.value = {
          ...emptyResult(),
          score: '-',
          exam: '-',
          region: '不限',
          scoreRange: null,
          totalCandidates: schools.length,
          items: schools
        }
      }
    }).catch(() => {})
  }
}, { immediate: true })

// --- created (top-level execution) ---
loadOptions()
loadResult()
loadFavoriteIds()
if (activeTab.value === 'compare') {
  loadCompare()
  loadBackupGroups()
}
</script>

<style scoped>
.prototype-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f6f9ff 0, #f8fbff 100%);
  color: #111827;
}

.result-layout {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: 24px;
}

.result-layout--compare {
  grid-template-columns: 300px minmax(0, 1fr);
}

.filter-sidebar {
  min-height: calc(100vh - 68px);
  background: #fff;
  border-right: 1px solid #e5edf8;
  padding: 26px 20px;
}

.sidebar-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.sidebar-title strong {
  font-size: 20px;
}

.sidebar-title button,
.section-title button,
.data-alert button,
.detail-link {
  border: 0;
  background: transparent;
  color: #1769f6;
  cursor: pointer;
}

.filter-group {
  margin-bottom: 20px;
}

.filter-group label {
  display: block;
  margin-bottom: 8px;
  color: #273247;
  font-weight: 600;
}

.filter-group :deep(.el-select),
.filter-group :deep(.el-input) {
  width: 100%;
}

.filter-select {
  height: 44px;
  border: 1px solid #dce5f3;
  border-radius: 6px;
  background: #fbfdff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
}

.range-buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.range-buttons button {
  min-height: 36px;
  border: 1px solid #d8e5f6;
  border-radius: 6px;
  background: #fbfdff;
  color: #284263;
  font-weight: 600;
  cursor: pointer;
}

.range-buttons button.active {
  border-color: #1f7aff;
  background: #eaf3ff;
  color: #1769f6;
  box-shadow: 0 0 0 2px rgba(31, 122, 255, 0.08);
}

.filter-button {
  width: 100%;
  height: 40px;
  margin: 10px 0 34px;
}

.tip-box {
  background: #f4f8ff;
  border-radius: 8px;
  padding: 16px;
  color: #627089;
  line-height: 1.7;
}

.tip-box i,
.tip-box strong {
  color: #1769f6;
}

.tip-box p {
  margin: 8px 0 0;
}

.result-main {
  padding: 28px 28px 34px 0;
}

.result-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 24px;
  align-items: center;
  margin-bottom: 20px;
}

.result-head h1 {
  display: inline-block;
  margin: 0 12px 0 0;
  font-size: 28px;
}

.result-head span {
  color: #6b778a;
}

.chips {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.chips span {
  height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #eef4ff;
  color: #253044;
  border-radius: 6px;
  padding: 0 12px;
}

.chips button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  margin-left: 2px;
  line-height: 1;
}

.data-alert {
  min-height: 42px;
  border: 1px solid #ffdca8;
  border-radius: 6px;
  background: #fff8e8;
  color: #bf6814;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  margin-bottom: 24px;
  line-height: 1.5;
}

.data-alert button {
  margin-left: auto;
  color: #bf6814;
}

.ai-analysis-card {
  border: 1px solid #c4d6ff;
  border-radius: 10px;
  background: linear-gradient(135deg, #f5f8ff 0%, #eef4ff 100%);
  margin-bottom: 24px;
  overflow: hidden;
}

.ai-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid #dbe5ff;
  background: rgba(255, 255, 255, 0.6);
}

.ai-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #6650d8, #7c5ce7);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.ai-card-header strong { font-size: 16px; color: #1e2740; }
.ai-card-header em { margin-left: auto; font-style: normal; color: #8b97b0; font-size: 12px; }

.ai-card-body { padding: 16px 20px; line-height: 1.85; color: #37445a; font-size: 14px; }
.ai-card-body p { margin: 0 0 10px; }
.ai-card-body p:last-child { margin-bottom: 0; }

.school-section {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 14px;
}

.section-title strong {
  color: #1769f6;
  font-size: 23px;
}

.section-title span {
  color: #6b778a;
}

.section-title button {
  margin-left: auto;
}

.school-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.school-card,
.compare-panel,
.backup-panel {
  background: #fff;
  border: 1px solid #dbe5f4;
  border-radius: 8px;
  box-shadow: 0 10px 28px rgba(34, 73, 135, 0.06);
}

.school-card {
  padding: 16px;
}

.card-top {
  display: grid;
  grid-template-columns: 48px 1fr 28px;
  gap: 12px;
  align-items: start;
}

.school-seal {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 2px solid #80aaff;
  color: #1769f6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
}

.school-card h3 {
  margin: 0 0 7px;
  font-size: 18px;
}

.school-card h3 small {
  margin-left: 8px;
  color: #1769f6;
  background: #edf4ff;
  border-radius: 4px;
  padding: 2px 7px;
  font-size: 12px;
}

.school-card p,
.school-card span {
  margin: 0;
  color: #6b778a;
  line-height: 1.6;
}

.school-meta-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  color: #607089;
  font-size: 14px;
}

.data-year-badge {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #eef6ff;
  color: #1769f6;
  font-style: normal;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.direction-count {
  border: 0;
  background: #eef4ff;
  color: #1769f6;
  border-radius: 999px;
  padding: 3px 9px;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.direction-list {
  margin-top: 14px;
  border-top: 1px solid #eef2f7;
  padding-top: 10px;
}

.direction-row {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid #f1f5fb;
}

.direction-row:last-child {
  border-bottom: 0;
}

.direction-main strong,
.direction-main small,
.direction-stats span {
  display: block;
}

.direction-main strong {
  color: #1f2937;
  font-size: 13px;
  line-height: 1.45;
}

.direction-main small {
  color: #7a879a;
  margin-top: 2px;
}

.direction-stats {
  color: #5f6f85;
  font-size: 12px;
  line-height: 1.7;
}

.direction-actions {
  display: flex;
  gap: 6px;
}

.direction-actions button {
  border: 1px solid #d7e4f5;
  border-radius: 5px;
  background: #fff;
  color: #1769f6;
  cursor: pointer;
  font-weight: 700;
  padding: 4px 7px;
  white-space: nowrap;
}

.direction-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.data-year-badge.muted {
  background: #f3f6fa;
  color: #8a97aa;
}

.school-card .pending-field {
  color: #d46b08;
  font-weight: 600;
}

.star-btn {
  border: 0;
  background: transparent;
  color: #42526b;
  cursor: pointer;
  font-size: 22px;
  transition: color 0.18s ease, transform 0.18s ease;
}

.star-btn:hover {
  color: #f5a400;
  transform: scale(1.08);
}

.star-btn.favorited,
.star-btn.favorited i {
  color: #f5a400;
}

.star-btn.loading {
  pointer-events: none;
  opacity: 0.72;
}

.score-line {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid #eef2f7;
  border-bottom: 1px solid #eef2f7;
  margin: 16px 0 12px;
  padding: 10px 0;
}

.score-metric {
  position: relative;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding-right: 18px;
  cursor: help;
  border-radius: 6px;
  outline: none;
}

.score-metric:hover,
.score-metric:focus,
.score-metric:focus-visible {
  background: #f5f8ff;
}

.score-metric::after {
  content: attr(data-tip);
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  z-index: 8;
  width: max-content;
  max-width: min(280px, 78vw);
  padding: 7px 9px;
  border: 1px solid #dbe7ff;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 22px rgba(15, 37, 76, 0.14);
  color: #526278;
  font-size: 12px;
  line-height: 1.45;
  font-weight: 400;
  white-space: normal;
  opacity: 0;
  visibility: hidden;
  transform: translateY(4px);
  transition: opacity 0.16s ease, transform 0.16s ease, visibility 0.16s ease;
  pointer-events: none;
}

.score-metric:nth-child(2n)::after {
  right: 18px;
  left: auto;
}

.score-metric:hover::after,
.score-metric:focus::after,
.score-metric:focus-visible::after {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.score-line strong {
  font-size: 17px;
}

.score-line strong small {
  display: block;
  margin-top: 2px;
  color: #7a879a;
  font-size: 11px;
  font-weight: 600;
  text-align: right;
}

.tags {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.tags em,
.grade {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  font-style: normal;
  font-weight: 700;
}

.grade-a {
  color: #15803d;
  background: #e7f7ed;
  border: 1px solid #a9e5bf;
}

.grade-b {
  color: #1769f6;
  background: #eaf2ff;
  border: 1px solid #b7d1ff;
}

.grade-c {
  color: #d46b08;
  background: #fff3dd;
}

.completeness-tip {
  position: relative;
  cursor: help;
  outline: none;
}

.completeness-tip::after {
  content: attr(data-tip);
  position: absolute;
  left: 50%;
  bottom: calc(100% + 8px);
  z-index: 10;
  width: max-content;
  max-width: min(260px, 72vw);
  padding: 7px 9px;
  border: 1px solid #dbe7ff;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 22px rgba(15, 37, 76, 0.14);
  color: #526278;
  font-size: 12px;
  line-height: 1.45;
  font-weight: 400;
  white-space: normal;
  opacity: 0;
  visibility: hidden;
  transform: translate(-50%, 4px);
  transition: opacity 0.16s ease, transform 0.16s ease, visibility 0.16s ease;
  pointer-events: none;
}

.completeness-tip:hover::after,
.completeness-tip:focus::after,
.completeness-tip:focus-visible::after {
  opacity: 1;
  visibility: visible;
  transform: translate(-50%, 0);
}

.card-note {
  font-size: 14px;
}

.source-link,
.drawer-source-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #1769f6;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}

.source-link {
  margin-right: 14px;
}

.source-missing {
  color: #8a97aa;
  font-weight: 600;
}

.compare-source {
  margin-right: 0;
  justify-content: center;
}

.source-link:hover,
.drawer-source-link:hover {
  color: #0f4fd1;
}

.compare-panel,
.backup-panel {
  padding: 16px;
  margin-bottom: 18px;
}

.tabs {
  display: flex;
  border-bottom: 1px solid #e6edf8;
  margin: -16px -16px 14px;
}

.tabs button {
  height: 50px;
  min-width: 126px;
  border: 0;
  background: transparent;
  font-weight: 700;
  cursor: pointer;
}

.tabs button.active {
  color: #1769f6;
  border-bottom: 3px solid #1769f6;
  background: #f8fbff;
}

.compare-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.compare-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.compare-table th,
.compare-table td {
  height: 48px;
  border: 1px solid #e0e7f2;
  text-align: center;
  padding: 0 10px;
}

.compare-table th {
  width: 155px;
  background: #f8fbff;
  text-align: left;
  color: #1f2937;
}

.star {
  color: #d5dbe7;
  font-size: 12px;
}

.star.light {
  color: #f5b51b;
}

.fit-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 70px;
  height: 26px;
  padding: 0 10px;
  border-radius: 4px;
  font-weight: 700;
}

.fit-sprint {
  color: #d93025;
  background: #fff0f0;
}

.fit-balanced {
  color: #d97706;
  background: #fff7e6;
}

.fit-balanced_sprint {
  color: #d97706;
  background: #fff7e6;
}

.fit-steady {
  color: #1769f6;
  background: #eaf2ff;
}

.fit-safe {
  color: #15803d;
  background: #e7f7ed;
}

.fit-stars {
  display: block;
  margin-top: 3px;
  line-height: 1;
}

.score-diff {
  font-family: "Cascadia Code", "Consolas", monospace;
  font-weight: 800;
}

.score-diff.positive {
  color: #15803d;
}

.score-diff.negative {
  color: #d93025;
}

.table-note {
  color: #7a879a;
  margin: 12px 0 0;
}

.backup-overview {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #dbe5f4;
  border-radius: 8px;
  background: #f8fbff;
}

.backup-overview span {
  color: #1769f6;
  font-size: 13px;
  font-weight: 800;
}

.backup-overview strong {
  display: block;
  margin: 4px 0 2px;
  color: #10203f;
  font-size: 30px;
  line-height: 1;
}

.backup-overview small,
.backup-overview p {
  margin: 0;
  color: #6b778a;
}

.backup-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
}

.backup-card {
  border: 1px solid;
  border-radius: 8px;
  overflow: hidden;
}

.backup-card.green {
  border-color: #bfe9c9;
}

.backup-card.blue {
  border-color: #bed4ff;
}

.backup-card.orange {
  border-color: #ffd7a8;
}

.backup-title {
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  background: #fbfdff;
}

.backup-title strong {
  font-size: 17px;
}

.backup-card.green strong,
.backup-card.green button,
.backup-card.green i {
  color: #169b47;
}

.backup-card.blue strong,
.backup-card.blue button,
.backup-card.blue i {
  color: #1769f6;
}

.backup-card.orange strong,
.backup-card.orange button,
.backup-card.orange i {
  color: #e07818;
}

.backup-title p {
  margin: 6px 0 0;
  color: #6b778a;
}

.backup-card ul {
  list-style: none;
  padding: 12px 16px 8px;
  margin: 0;
}

.backup-item-list {
  display: grid;
  gap: 10px;
}

.backup-card li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-height: 36px;
  align-items: center;
}

.backup-item-card {
  padding: 12px;
  border: 1px solid #e6edf8;
  border-radius: 8px;
  background: #fff;
}

.backup-card li span {
  display: block;
  min-width: 0;
  color: #1f2937;
  font-weight: 800;
  line-height: 1.45;
}

.backup-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 7px 0 0;
}

.backup-card li em,
.backup-card li small {
  font-style: normal;
  color: #1769f6;
  background: #eef4ff;
  border-radius: 4px;
  padding: 2px 8px;
}

.backup-card li small {
  color: #475569;
  background: #f1f5f9;
}

.backup-actions {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8px;
}

.backup-actions button {
  width: auto;
  height: 28px;
  border: 1px solid #bfd3ff;
  border-radius: 5px;
  background: #fff;
  cursor: pointer;
  font-weight: 700;
  padding: 0 8px;
  white-space: nowrap;
}

.backup-card > button {
  width: 100%;
  height: 42px;
  border: 0;
  border-top: 1px solid #edf2f8;
  background: #fbfdff;
  cursor: pointer;
  font-weight: 700;
}

.empty-group {
  border: 1px dashed #d8e2f0;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  color: #8a96a8;
  background: #fbfdff;
}

.compare-empty strong {
  display: block;
  color: #1f2937;
  font-size: 16px;
  margin-bottom: 6px;
}

.compare-empty p {
  margin: 0 0 14px;
  color: #6b778a;
}

.backup-quick-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  max-width: 760px;
  margin: 0 auto;
}

.backup-quick-list button {
  min-height: 42px;
  border: 1px solid #d7e5fb;
  border-radius: 6px;
  background: #fff;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  text-align: left;
}

.backup-quick-list button.selected {
  border-color: #1769f6;
  background: #eef5ff;
}

.backup-quick-list i {
  width: 20px;
  height: 20px;
  border: 1px solid #bfd3ff;
  border-radius: 50%;
  color: #1769f6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.backup-quick-list button.selected i {
  border-color: #1769f6;
  background: #1769f6;
  color: #fff;
}

.backup-quick-list span {
  min-width: 0;
  color: #263955;
  font-weight: 700;
  line-height: 1.35;
}

.backup-batch-button {
  margin-top: 14px;
}

.empty-result {
  min-height: 300px;
  border: 1px dashed #cbd8ea;
  border-radius: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #65748b;
}

.empty-result i {
  font-size: 36px;
  color: #9db2d0;
}

.empty-result strong {
  color: #1e293b;
  font-size: 18px;
}

.empty-search-box {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  width: 100%;
  max-width: 400px;
}

.empty-search-box .el-input {
  flex: 1;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}

.backup-card-btn {
  height: 30px;
  border: 1px solid #bfd3ff;
  border-radius: 5px;
  background: #eef5ff;
  color: #1769f6;
  cursor: pointer;
  font-weight: 700;
  padding: 0 12px;
}

.backup-card-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.positive {
  color: #15803d;
}

.negative {
  color: #d93025;
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
  margin-bottom: 16px;
  background: #fff8e8;
}

.drawer-warning p {
  color: #9a4d00;
  margin: 4px 0;
}

.drawer-source-link {
  margin: 0 0 18px;
}

.trend-table {
  width: 100%;
  border-collapse: collapse;
}

.trend-table th,
.trend-table td {
  border-bottom: 1px solid #e5edf8;
  padding: 8px;
  text-align: left;
}

@media (max-width: 1180px) {
  .result-layout {
    grid-template-columns: 1fr;
  }

  .filter-sidebar {
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid #e5edf8;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
  }

  .sidebar-title,
  .filter-button,
  .tip-box {
    grid-column: 1 / -1;
  }

  .result-main {
    padding: 24px;
  }

  .school-grid,
  .backup-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .filter-sidebar {
    grid-template-columns: 1fr;
    padding: 18px 14px;
  }

  .result-main {
    padding: 18px 14px;
  }

  .result-head {
    grid-template-columns: 1fr;
  }

  .data-alert {
    height: auto;
    min-height: 42px;
    align-items: flex-start;
    padding: 12px;
  }

  .school-grid,
  .backup-grid {
    grid-template-columns: 1fr;
  }

  .compare-panel {
    overflow-x: auto;
  }

  .compare-table {
    min-width: 850px;
  }
}
</style>
