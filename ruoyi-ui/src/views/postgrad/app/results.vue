<template>
  <div class="prototype-page">
    <AppHeader :current-page="currentHeader" />

    <main class="result-layout">
      <aside class="filter-sidebar">
        <div class="sidebar-title">
          <strong>筛选条件</strong>
          <button type="button" @click="resetFilters">清空</button>
        </div>
        <div class="filter-group">
          <label>地区</label>
          <el-select v-model="filterForm.region" clearable filterable placeholder="不限">
            <el-option label="不限" value="" />
            <el-option v-for="region in regionOptions" :key="region" :label="region" :value="region" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>学校层次</label>
          <el-select v-model="filterForm.tier" placeholder="全部层次">
            <el-option label="全部层次" value="" />
            <el-option label="985" value="985" />
            <el-option label="211" value="211" />
            <el-option label="双一流" value="double_first" />
            <el-option label="普通院校" value="ordinary" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>考试组合</label>
          <el-select v-model="filterForm.exam" clearable placeholder="不限">
            <el-option label="不限" value="" />
            <el-option label="11408" value="11408" />
            <el-option label="22408" value="22408" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>是否有拟录取区间</label>
          <el-select v-model="filterForm.hasAdmissionRange" placeholder="不限">
            <el-option label="不限" value="" />
            <el-option label="有拟录取区间" value="yes" />
            <el-option label="暂无拟录取区间" value="no" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>N诺数据完整度</label>
          <el-select v-model="filterForm.confidence" placeholder="全部">
            <el-option label="全部" value="" />
            <el-option label="完整度 A" value="A" />
            <el-option label="完整度 B" value="B" />
            <el-option label="完整度 C" value="C" />
          </el-select>
        </div>
        <el-button class="filter-button" type="primary" @click="applyFilters">应用筛选</el-button>
        <div class="tip-box">
          <i class="el-icon-info"></i>
          <strong>小贴士</strong>
          <p>推荐结果不是唯一可报范围，复试线也不是最低录取分；N诺数据可能遗漏或错误，请结合院校官网复核。</p>
        </div>
      </aside>

      <section class="result-main" v-loading="loading">
        <div class="result-head">
          <div>
            <h1>{{ activeTab === 'compare' ? '对比与备选' : '推荐结果' }}</h1>
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
          <el-button plain type="primary" icon="el-icon-download">导出结果</el-button>
        </div>

        <div class="data-alert">
          <i class="el-icon-warning"></i>
          复试线不是最低录取分；推荐学校不代表只有这些学校可以报。当前数据主要来源于 N诺（第三方整理），可能存在遗漏或错误，请以院校官网为准。
          <button type="button" @click="activeTab = 'compare'">查看说明 <i class="el-icon-info"></i></button>
        </div>

        <template v-if="activeTab === 'compare'">
          <section class="compare-panel">
            <div class="tabs">
              <button class="active">院校对比</button>
              <button>我的备选</button>
            </div>
            <div class="compare-actions">
              <span>已选择 4 个项目</span>
              <div>
                <el-button icon="el-icon-download" size="small">导出清单</el-button>
                <el-button type="primary" icon="el-icon-data-line" size="small">一键对比</el-button>
                <el-button icon="el-icon-setting" size="small">调整列</el-button>
              </div>
            </div>
            <table class="compare-table">
              <tbody>
                <tr v-for="row in compareRows" :key="row.label">
                  <th>{{ row.label }}</th>
                  <td v-for="school in compareSchools" :key="school.name + row.label">
                    <template v-if="row.type === 'name'"><strong>{{ school.name }}</strong></template>
                    <template v-else-if="row.type === 'fit'">
                      <span class="fit-tag" :class="'fit-' + school.fitLevelClass">{{ school.fitLevel }}</span>
                      <span class="fit-stars" aria-hidden="true">
                        <span v-for="n in 5" :key="n" class="star" :class="{ light: n <= school.star }">★</span>
                      </span>
                    </template>
                    <template v-else-if="row.type === 'diff'">
                      <span class="score-diff" :class="{ positive: school[row.key] >= 0, negative: school[row.key] < 0 }">
                        {{ school[row.key] > 0 ? '+' : '' }}{{ school[row.key] }}
                      </span>
                    </template>
                    <template v-else-if="row.type === 'confidence'">
                      <span class="grade" :class="'grade-' + school.confidence.toLowerCase()">完整度 {{ school.confidence }}</span>
                      <small class="completeness-note">{{ dataCompletenessText(school.confidence) }}</small>
                    </template>
                    <template v-else-if="row.type === 'action'"><button class="detail-link" type="button" @click="openDetail(school.programId)">查看详情</button></template>
                    <template v-else>{{ school[row.key] }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
            <p class="table-note">注：拟录取区间为近三年拟录取总分范围，仅供参考；招生人数含推免，具体以院校当年公告为准。</p>
          </section>

          <section class="backup-panel">
            <div class="section-title">
              <strong>我的备选</strong>
              <span>共 11 个项目</span>
            </div>
            <div class="backup-grid">
              <div v-for="group in backupGroups" :key="group.name" class="backup-card" :class="group.theme">
                <div class="backup-title">
                  <div>
                    <strong>{{ group.name }}（{{ group.items.length }}）</strong>
                    <p>{{ group.desc }}</p>
                  </div>
                  <i class="el-icon-plus"></i>
                </div>
                <ul>
                  <li v-for="item in group.items" :key="item.name">
                    <span>{{ item.name }}</span>
                    <em>{{ item.grade }}</em>
                  </li>
                </ul>
                <button type="button">查看全部 {{ group.items.length }} 个项目 <i class="el-icon-right"></i></button>
              </div>
            </div>
          </section>
        </template>

        <div v-if="result.aiAnalysis" class="ai-analysis-card">
          <div class="ai-card-header">
            <span class="ai-icon"><i class="el-icon-cpu"></i></span>
            <strong>AI 推荐解读</strong>
            <em>由 DeepSeek 生成，仅供参考</em>
          </div>
          <div class="ai-card-body">
            <p v-for="(para, idx) in aiParagraphs" :key="idx">{{ para }}</p>
          </div>
        </div>

        <template v-else>
          <section v-for="group in filteredGroups" :key="group.name" class="school-section">
            <div class="section-title">
              <strong>{{ group.name }}</strong>
              <span>（{{ group.schools.length }} 所） {{ group.desc }}</span>
              <button type="button">展开全部 <i class="el-icon-right"></i></button>
            </div>

            <div v-if="group.schools.length === 0" class="empty-group">暂无该分组结果</div>
            <div v-else class="school-grid">
              <article v-for="school in group.schools" :key="school.schoolName" class="school-card">
                <div class="card-top">
                  <div class="school-seal">{{ school.schoolName.slice(0, 1) }}</div>
                  <div>
                    <h3>{{ school.schoolName }} <small>{{ school.badge }}</small></h3>
                    <p>{{ school.collegeName }} / {{ school.programName }}</p>
                    <span>{{ school.exam }} | {{ school.province }}</span>
                  </div>
                  <button
                    class="star-btn"
                    :class="{ favorited: isFavorited(school), loading: favoriteLoadingIds.includes(favoriteKey(school)) }"
                    type="button"
                    @click="handleFavorite(school)"
                    :aria-label="isFavorited(school) ? '取消收藏' : '加入收藏'">
                    <i :class="isFavorited(school) ? 'el-icon-star-on' : 'el-icon-star-off'"></i>
                  </button>
                </div>

                <div class="score-line">
                  <div><span>复试线</span><strong>{{ school.scoreLine }}</strong></div>
                  <div><span>最低录取分</span><strong>{{ school.admissionLow || '-' }}</strong></div>
                  <div><span>录取分差距</span><strong :class="{ positive: school.admissionLowGap >= 0, negative: school.admissionLowGap < 0 }">{{ formatDiff(school.admissionLowGap) }}</strong></div>
                  <div><span>拟录取区间</span><strong>{{ school.range }}</strong></div>
                </div>

                <div class="tags">
                  <em :class="'grade-' + school.confidence.toLowerCase()">完整度{{ school.confidence }}</em>
                  <em class="risk-tag">{{ school.tag }}</em>
                </div>

                <p v-if="school.note" class="card-note">{{ school.note }}</p>
                <button class="detail-link card-detail" type="button" @click="openDetail(school.programId)">查看详情</button>
              </article>
            </div>
          </section>
        </template>
      </section>
    </main>

    <el-drawer
      title="院校专业详情"
      :visible.sync="detailVisible"
      size="520px"
      append-to-body>
      <div class="detail-drawer" v-loading="detailLoading">
        <template v-if="detail">
          <h2>{{ detail.basic.schoolName }}</h2>
          <p>{{ detail.basic.collegeName }} / {{ detail.basic.programName }}</p>
          <div class="detail-tags">
            <span>{{ detail.basic.examCombo }}：{{ detail.basic.examSubjectsLabel }}</span>
            <span>{{ detail.basic.studyModeLabel }}</span>
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

<script>
import AppHeader from './components/AppHeader'
import { getRecommendationResult } from '@/api/postgrad/appRecommendation'
import { comparePrograms, getProgramDetail } from '@/api/postgrad/appPrograms'
import { addFavorite, listFavorites, removeFavorite } from '@/api/postgrad/appFavorites'
import { getAppToken } from '@/utils/appAuth'

const fallbackResult = {
  score: 300,
  exam: '22408',
  region: '福建',
  studyMode: '全日制',
  groups: [
    {
      name: '冲刺',
      desc: '录取概率较低，但仍有机会',
      schools: [
        { schoolName: '厦门大学', badge: '985 211 双一流', collegeName: '信息学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 320, range: '315-336', confidence: 'B', tag: '冲刺', note: '近年复试线较高，报考热度大，300分有机会进入复试。', star: 4 },
        { schoolName: '福州大学', badge: '211 双一流', collegeName: '计算机与大数据学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 305, range: '295-322', confidence: 'B', tag: '冲刺', note: '专业实力较强，竞争激烈，建议冲刺尝试。', star: 4 },
        { schoolName: '华侨大学', badge: '中央部属', collegeName: '信息科学与工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 300, range: '290-316', confidence: 'B', tag: '冲刺', note: '复试线接近300分，存在机会，需关注复试表现。', star: 4 }
      ]
    },
    {
      name: '稳中偏冲',
      desc: '有一定机会，需合理评估',
      schools: [
        { schoolName: '福建师范大学', badge: '省属重点', collegeName: '计算机与网络空间安全学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 285, range: '276-304', confidence: 'B', tag: '稳中偏冲', note: '复试线与分数匹配，近年录取较稳，建议作为稳中偏冲选择。', star: 4 },
        { schoolName: '福建农林大学', badge: '省属重点', collegeName: '计算机与信息学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 280, range: '270-298', confidence: 'B', tag: '稳中偏冲', note: '录取区间与分数匹配，机会较大，建议重点关注。', star: 4 },
        { schoolName: '集美大学', badge: '省属重点', collegeName: '信息工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 275, range: '265-290', confidence: 'C', tag: '稳中偏冲', note: '复试线偏低，录取较稳定，建议作为稳中偏冲志愿。', star: 3 }
      ]
    },
    {
      name: '稳妥候选',
      desc: '录取概率较高，适合作为保底',
      schools: [
        { schoolName: '福建工程学院', badge: '普通本科', collegeName: '计算机与数学学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 260, range: '250-275', confidence: 'C', tag: '稳妥候选', note: '录取门槛较低，录取机会较大，适合作为保底选择。', star: 3 },
        { schoolName: '闽江学院', badge: '普通本科', collegeName: '计算机与大数据学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 252, range: '242-268', confidence: 'C', tag: '稳妥候选', note: '近年录取较稳定，分数匹配度高，建议保底。', star: 3 },
        { schoolName: '莆田学院', badge: '普通本科', collegeName: '机电与信息工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 248, range: '238-262', confidence: 'C', tag: '稳妥候选', note: '录取门槛较低，录取机会较大，建议保底。', star: 3 }
      ]
    }
  ]
}

export default {
  name: 'AppResults',
  components: { AppHeader },
  data() {
    return {
      loading: false,
      detailVisible: false,
      detailLoading: false,
      detail: null,
      activeTab: this.$route.query.tab === 'compare' ? 'compare' : 'result',
      result: fallbackResult,
      favoriteProgramIds: [],
      favoriteLoadingIds: [],
      filterForm: {
        region: '',
        tier: '',
        exam: '',
        hasAdmissionRange: '',
        confidence: ''
      },
      appliedFilters: {
        region: '',
        tier: '',
        exam: '',
        hasAdmissionRange: '',
        confidence: ''
      },
      compareRows: [
        { label: '学校', type: 'name' },
        { label: '专业', key: 'program' },
        { label: '考试组合', key: 'exam' },
        { label: '复试线（2025）', key: 'score' },
        { label: '最低录取分', key: 'admissionLow' },
        { label: '与最低录取分差距', type: 'diff', key: 'admissionLowGap' },
        { label: '拟录取区间（总分）', key: 'range' },
        { label: '拟录取均分', key: 'avgScore' },
        { label: '与拟录取均分差距', type: 'diff', key: 'avgScoreGap' },
        { label: '招生人数（含推免）', key: 'quota' },
        { label: 'N诺数据完整度', type: 'confidence' },
        { label: '推荐等级', type: 'fit' },
        { label: '操作', type: 'action' }
      ],
      compareSchools: [
        { name: '复旦大学', program: '计算机科学与技术', exam: '11408：政治 + 英语一 + 数学一 + 408', score: 355, admissionLow: 355, admissionLowGap: -55, range: '355-405', avgScore: 382, avgScoreGap: -82, quota: '120（含推免）', confidence: 'A', fitLevel: '冲刺', fitLevelClass: 'sprint', star: 5 },
        { name: '上海交通大学', program: '计算机科学与技术', exam: '11408：政治 + 英语一 + 数学一 + 408', score: 350, admissionLow: 350, admissionLowGap: -50, range: '350-402', avgScore: 376, avgScoreGap: -76, quota: '160（含推免）', confidence: 'A', fitLevel: '冲刺', fitLevelClass: 'sprint', star: 5 },
        { name: '华东师范大学', program: '计算机科学与技术', exam: '22408：政治 + 英语二 + 数学二 + 408', score: 330, admissionLow: 330, admissionLowGap: -30, range: '330-380', avgScore: 352, avgScoreGap: -52, quota: '85（含推免）', confidence: 'B', fitLevel: '稳中偏冲', fitLevelClass: 'balanced', star: 4 },
        { name: '华中科技大学', program: '计算机科学与技术', exam: '22408：政治 + 英语二 + 数学二 + 408', score: 325, admissionLow: 325, admissionLowGap: -25, range: '325-375', avgScore: 346, avgScoreGap: -46, quota: '110（含推免）', confidence: 'B', fitLevel: '稳中偏冲', fitLevelClass: 'balanced', star: 4 }
      ],
      backupGroups: []
    }
  },
  computed: {
    currentHeader() {
      return this.activeTab === 'compare' ? 'compare' : 'results'
    },
    headerChips() {
      const chips = [
        { key: 'score', label: `分数 ${this.result.score}` },
        { key: 'exam', label: `考试组合 ${this.appliedFilters.exam || this.result.exam}`, clearKey: this.appliedFilters.exam ? 'exam' : '' },
        { key: 'region', label: `地区 ${this.appliedFilters.region || this.result.region}`, clearKey: this.appliedFilters.region ? 'region' : '' },
        { key: 'studyMode', label: `学习方式 ${this.result.studyMode}` }
      ]
      if (this.appliedFilters.tier) {
        chips.push({ key: 'tier', label: `学校层次 ${this.tierLabel(this.appliedFilters.tier)}`, clearKey: 'tier' })
      }
      if (this.appliedFilters.hasAdmissionRange) {
        chips.push({ key: 'hasAdmissionRange', label: `拟录取区间 ${this.admissionRangeLabel(this.appliedFilters.hasAdmissionRange)}`, clearKey: 'hasAdmissionRange' })
      }
      if (this.appliedFilters.confidence) {
        chips.push({ key: 'confidence', label: `完整度 ${this.appliedFilters.confidence}`, clearKey: 'confidence' })
      }
      return chips
    },
    regionOptions() {
      const regions = new Set()
      ;(this.result.groups || []).forEach(group => {
        ;(group.schools || []).forEach(school => {
          if (school.province) regions.add(school.province)
        })
      })
      return Array.from(regions).sort()
    },
    filteredGroups() {
      const filters = this.appliedFilters
      return (this.result.groups || []).map(group => ({
        ...group,
        schools: (group.schools || []).filter(school => this.matchSchoolFilters(school, filters))
      }))
    },
    filteredTotal() {
      return this.filteredGroups.reduce((sum, group) => sum + group.schools.length, 0)
    },
    aiParagraphs() {
      const text = this.result.aiAnalysis
      if (!text) return []
      return text.split('\n').filter(p => p.trim()).map(p => p.replace(/^#+\s*/, ''))
    }
  },
  created() {
    this.loadResult()
    this.loadFavoriteIds()
  },
  watch: {
    '$route.query.tab'(tab) {
      this.activeTab = tab === 'compare' ? 'compare' : 'result'
      if (this.activeTab === 'compare') this.loadCompare()
    },
    '$route.query.programIds'() {
      if (this.activeTab === 'compare') this.loadCompare()
    },
    '$route.query.id'() {
      this.loadResult()
    }
  },
  methods: {
    loadResult() {
      const id = this.$route.query.id || window.sessionStorage.getItem('app-recommend-id')
      const cached = window.sessionStorage.getItem('app-recommend-result')
      if (cached) {
        try {
          const parsed = JSON.parse(cached)
          this.result = this.normalizeResult(parsed)
          if (this.activeTab === 'compare') {
            this.loadCompare()
            this.loadBackupGroups()
          }
        } catch (e) {}
      }
      if (!id) return
      this.loading = true
      getRecommendationResult(id).then(res => {
        this.result = this.normalizeResult(res.data || {})
        window.sessionStorage.setItem('app-recommend-result', JSON.stringify(res.data || {}))
        if (this.activeTab === 'compare') {
          this.loadCompare()
          this.loadBackupGroups()
        }
      }).finally(() => { this.loading = false })
    },
    normalizeResult(data) {
      if (!data || !data.groups) return fallbackResult
      const request = data.request || {}
      const groups = (data.groups || []).map(group => ({
        name: group.groupName || group.name,
        desc: group.description || group.desc,
        schools: (group.items || group.schools || []).map(this.normalizeSchool)
      }))
      return {
        recommendationId: data.recommendationId,
        totalCandidates: data.summary ? data.summary.totalCandidates : groups.reduce((sum, group) => sum + group.schools.length, 0),
        score: request.estimatedScore || data.score || 300,
        exam: request.examCombo || data.exam || '22408',
        region: request.targetRegions && request.targetRegions.length ? request.targetRegions.join('、') : '不限',
        studyMode: this.studyModeText(request.studyMode || data.studyMode),
        aiAnalysis: data.aiAnalysis || null,
        summary: data.summary || null,
        globalWarnings: data.globalWarnings || [],
        groups
      }
    },
    normalizeSchool(item) {
      return {
        programId: item.programId,
        schoolName: item.schoolName,
        badge: this.schoolBadge(item),
        tier: item.schoolTier,
        is985: item.is985,
        is211: item.is211,
        isDoubleFirst: item.isDoubleFirst,
        collegeName: item.collegeName,
        programName: item.programName,
        examCombo: item.examCombo,
        exam: item.examCombo + '：' + item.examSubjectsLabel,
        province: item.province,
        scoreLine: item.scoreLine,
        scoreLineGap: item.scoreLineGap,
        admissionLow: item.admissionLow,
        admissionLowGap: item.admissionLowGap,
        range: item.admissionRangeLabel || '-',
        avgScore: item.avgAdmittedScore || '-',
        avgScoreGap: item.avgScoreGap,
        confidence: item.dataCompleteness || 'C',
        tag: item.fitLevelLabel || '数据不足',
        fitLevelClass: item.fitLevel || 'insufficient_data',
        hasAdmissionRange: item.admissionLow !== null && item.admissionLow !== undefined && item.admissionHigh !== null && item.admissionHigh !== undefined,
        note: this.cardNote(item),
        star: item.fitLevel === 'safe' ? 3 : item.fitLevel === 'steady' ? 4 : 5
      }
    },
    schoolBadge(item) {
      const badges = []
      if (item.is985) badges.push('985')
      if (item.is211) badges.push('211')
      if (item.isDoubleFirst) badges.push('双一流')
      return badges.join(' ')
    },
    studyModeText(value) {
      const map = { any: '不限', full_time: '全日制', part_time: '非全日制' }
      return map[value] || value || '不限'
    },
    tierLabel(value) {
      const map = {
        985: '985',
        211: '211',
        double_first: '双一流',
        ordinary: '普通院校'
      }
      return map[value] || '全部层次'
    },
    admissionRangeLabel(value) {
      const map = {
        yes: '有',
        no: '暂无'
      }
      return map[value] || '不限'
    },
    cardNote(item) {
      const repeatedWarnings = [
        '复试线不是最低录取分。',
        '推荐学校不代表只有这些学校可以报。'
      ]
      const warnings = (item.warnings || []).filter(text => !repeatedWarnings.includes(text))
      if (warnings.length) return warnings.join(' ')
      return item.dataCompletenessText || ''
    },
    applyFilters() {
      this.appliedFilters = { ...this.filterForm }
    },
    clearHeaderChip(key) {
      if (!Object.prototype.hasOwnProperty.call(this.filterForm, key)) return
      this.filterForm[key] = ''
      this.applyFilters()
    },
    resetFilters() {
      this.filterForm = {
        region: '',
        tier: '',
        exam: '',
        hasAdmissionRange: '',
        confidence: ''
      }
      this.applyFilters()
    },
    matchSchoolFilters(school, filters) {
      if (filters.region && school.province !== filters.region) return false
      if (filters.exam && school.examCombo !== filters.exam) return false
      if (filters.confidence && school.confidence !== filters.confidence) return false
      if (filters.hasAdmissionRange === 'yes' && !school.hasAdmissionRange) return false
      if (filters.hasAdmissionRange === 'no' && school.hasAdmissionRange) return false
      if (filters.tier === '985' && !school.is985) return false
      if (filters.tier === '211' && !school.is211) return false
      if (filters.tier === 'double_first' && !school.isDoubleFirst) return false
      if (filters.tier === 'ordinary' && (school.is985 || school.is211 || school.isDoubleFirst)) return false
      return true
    },
    loadCompare() {
      const ids = this.$route.query.programIds
        ? String(this.$route.query.programIds).split(',').filter(Boolean).slice(0, 8)
        : this.result.groups.flatMap(group => group.schools).map(item => item.programId).filter(Boolean).slice(0, 4)
      if (!ids.length) return
      const estimatedScore = this.$route.query.score || this.result.score
      comparePrograms({ programIds: ids.join(','), estimatedScore }).then(res => {
        const items = (res.data && res.data.items) || []
        this.compareSchools = items.map(item => {
          const school = this.normalizeSchool(item)
          return {
            programId: school.programId,
            name: school.schoolName,
            program: school.programName,
            exam: school.exam,
            score: school.scoreLine,
            scoreLineGap: school.scoreLineGap,
            admissionLow: school.admissionLow,
            admissionLowGap: school.admissionLowGap,
            range: school.range,
            avgScore: school.avgScore,
            avgScoreGap: school.avgScoreGap,
            quota: item.planCount || '-',
            confidence: school.confidence,
            fitLevel: school.tag,
            fitLevelClass: school.fitLevelClass,
            star: school.star
          }
        })
      })
    },
    loadBackupGroups() {
      listFavorites().then(res => {
        this.syncFavoriteIds(res.data || [])
        const items = (res.data || []).map(item => ({
          name: item.school_name + ' · ' + item.program_name,
          grade: item.program_code || '已收藏',
          programId: item.program_id
        }))
        this.backupGroups = items.length
          ? [{ name: '我的收藏', desc: '从推荐结果中收藏的院校专业', theme: 'blue', items }]
          : []
      }).catch(() => {
        this.backupGroups = []
      })
    },
    loadFavoriteIds() {
      listFavorites().then(res => {
        this.syncFavoriteIds(res.data || [])
      }).catch(() => {})
    },
    syncFavoriteIds(items) {
      this.favoriteProgramIds = Array.from(new Set((items || [])
        .map(item => Number(item.program_id || item.programId))
        .filter(Boolean)
        .map(id => `program:${id}`)))
    },
    favoriteKey(school) {
      if (!school) return ''
      return school.programId ? `program:${school.programId}` : `local:${school.schoolName}:${school.programName}`
    },
    isFavorited(school) {
      return this.favoriteProgramIds.includes(this.favoriteKey(school))
    },
    handleFavorite(school) {
      const key = this.favoriteKey(school)
      if (!key || this.favoriteLoadingIds.includes(key)) return
      const programId = Number(school.programId)
      const favorited = this.isFavorited(school)
      if (!getAppToken() || !programId) {
        this.favoriteProgramIds = favorited
          ? this.favoriteProgramIds.filter(id => id !== key)
          : [...this.favoriteProgramIds, key]
        this.$message({
          message: favorited ? '已取消收藏' : '已收藏，登录后可同步到我的收藏',
          type: favorited ? 'success' : 'warning'
        })
        return
      }
      this.favoriteLoadingIds = [...this.favoriteLoadingIds, key]
      if (favorited) {
        this.favoriteProgramIds = this.favoriteProgramIds.filter(id => id !== key)
        removeFavorite(programId).then(res => {
          this.$message.success(res.msg || '已取消收藏')
          if (this.activeTab === 'compare') this.loadBackupGroups()
        }).catch(() => {
          this.favoriteProgramIds = [...this.favoriteProgramIds, key]
        }).finally(() => {
          this.favoriteLoadingIds = this.favoriteLoadingIds.filter(id => id !== key)
        })
      } else {
        this.favoriteProgramIds = [...this.favoriteProgramIds, key]
        addFavorite(programId).then(res => {
          this.$message.success(res.msg || '已加入收藏')
          if (this.activeTab === 'compare') this.loadBackupGroups()
        }).catch(() => {
          this.favoriteProgramIds = this.favoriteProgramIds.filter(id => id !== key)
        }).finally(() => {
          this.favoriteLoadingIds = this.favoriteLoadingIds.filter(id => id !== key)
        })
      }
    },
    openDetail(programId) {
      if (!programId) return
      this.detailVisible = true
      this.detailLoading = true
      this.detail = null
      getProgramDetail(programId, { estimatedScore: this.result.score }).then(res => {
        this.detail = res.data
      }).finally(() => { this.detailLoading = false })
    },
    formatDiff(value) {
      if (value === null || value === undefined) return '-'
      return (value > 0 ? '+' : '') + value
    },
    dataCompletenessText(level) {
      const map = {
        A: '含复试线、拟录取区间、人数等字段',
        B: '含主要分数字段，部分字段缺失',
        C: '仅有复试线或基础字段'
      }
      return map[level] || '字段完整度待核验'
    }
  }
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

.score-line div {
  display: flex;
  justify-content: space-between;
  padding-right: 18px;
}

.score-line strong {
  font-size: 17px;
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

.completeness-note {
  display: block;
  margin-top: 5px;
  color: #6b778a;
  font-size: 12px;
  line-height: 1.35;
}

.risk-tag {
  color: #ef4444;
  background: #fff0f0;
}

.card-note {
  font-size: 14px;
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

.backup-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.backup-card li {
  display: flex;
  justify-content: space-between;
  min-height: 36px;
  align-items: center;
}

.backup-card li em {
  font-style: normal;
  color: #1769f6;
  background: #eef4ff;
  border-radius: 4px;
  padding: 2px 8px;
}

.backup-card button {
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

.card-detail {
  margin-top: 10px;
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
