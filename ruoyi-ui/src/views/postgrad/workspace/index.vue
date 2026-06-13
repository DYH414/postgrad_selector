<template>
  <div class="app-container workspace-page">
    <div class="workspace-shell">
      <div class="workspace-header">
        <div>
          <div class="workspace-eyebrow">N诺 Data Ops</div>
          <h2>学校数据工作台</h2>
          <p>按学校统一查看 N诺来源的学院、专业方向、年份数据完整度和待审核状态。</p>
        </div>
        <div class="workspace-header__actions">
          <el-button size="mini" icon="el-icon-refresh" @click="loadWorkspace">刷新</el-button>
          <el-button size="mini" type="primary" icon="el-icon-s-data" @click="jumpToCrud('programYearDataQuality')">数据体检</el-button>
        </div>
      </div>

      <el-form class="workspace-filter" :model="filters" size="small" :inline="true">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索学校 / 学院 / 专业方向"
            prefix-icon="el-icon-search"
            @keyup.enter.native="handleSearch"
          />
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="filters.province" clearable placeholder="如 江苏" @keyup.enter.native="handleSearch" />
        </el-form-item>
        <el-form-item label="层次">
          <el-select v-model="filters.tier" clearable placeholder="全部层次">
            <el-option v-for="item in tierOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="年份">
          <el-select v-model="filters.year" placeholder="年份">
            <el-option v-for="year in yearOptions" :key="year" :label="year" :value="year" />
          </el-select>
        </el-form-item>
        <el-form-item label="408">
          <el-select v-model="filters.is408" clearable placeholder="全部">
            <el-option label="只看 408" value="1" />
            <el-option label="不限 408" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="完整度">
          <el-select v-model="filters.completeness" clearable placeholder="A/B/C/D">
            <el-option v-for="item in completenessOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh-left" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="kpi-grid">
        <div v-for="item in kpiItems" :key="item.key" class="kpi-item" :class="'kpi-item--' + item.tone">
          <div class="kpi-item__label">{{ item.label }}</div>
          <div class="kpi-item__value">{{ item.value }}</div>
          <div class="kpi-item__hint">{{ item.hint }}</div>
        </div>
      </div>

      <el-alert
        v-if="statsNotice"
        class="workspace-alert"
        :title="statsNotice"
        type="info"
        show-icon
        :closable="false"
      />

      <div class="viz-grid">
        <section class="workspace-panel viz-panel">
          <div class="panel-heading">
            <div>
              <h3>N诺完整度分布</h3>
              <span>A/B/C/D 专业方向占比</span>
            </div>
          </div>
          <div ref="qualityChart" class="viz-chart"></div>
        </section>
        <section class="workspace-panel viz-panel">
          <div class="panel-heading">
            <div>
              <h3>N诺覆盖率</h3>
              <span>复试线 / 招生计划 / 拟录取</span>
            </div>
          </div>
          <div ref="coverageChart" class="viz-chart"></div>
        </section>
      </div>

      <div v-if="selectedSchool" class="school-summary">
        <div class="school-summary__main">
          <strong>{{ selectedSchool.name }}</strong>
          <span>{{ selectedSchool.province || '-' }} / {{ selectedSchool.city || '-' }} · {{ formatTier(selectedSchool.tier) }}</span>
        </div>
        <div class="school-summary__metrics">
          <span>学院 <b>{{ formatStat(currentStats.collegeCount, selectedSchool.collegeCount) }}</b></span>
          <span>专业 <b>{{ formatStat(currentStats.programCount, selectedSchool.programCount) }}</b></span>
          <span>408 <b>{{ formatStat(currentStats.program408Count, selectedSchool.program408Count) }}</b></span>
          <span>待审 <b>{{ formatStat(currentStats.pendingReviewCount, selectedSchool.pendingReviewCount) }}</b></span>
        </div>
      </div>

      <div class="workspace-grid">
        <section class="workspace-panel school-panel">
          <div class="panel-heading">
            <div>
              <h3>学校列表</h3>
              <span>{{ schoolList.length }} 所</span>
            </div>
            <el-button size="mini" type="text" icon="el-icon-school" @click="jumpToCrud('school')">学校管理</el-button>
          </div>
          <div v-loading="schoolsLoading" class="school-list">
            <button
              v-for="school in schoolList"
              :key="school.id"
              type="button"
              class="school-item"
              :class="{ 'is-active': selectedSchool && selectedSchool.id === school.id }"
              @click="selectSchool(school)"
            >
              <div class="school-item__main">
                <strong>{{ school.name }}</strong>
                <span>{{ school.province || '-' }} / {{ school.city || '-' }}</span>
              </div>
              <div class="school-item__meta">
                <el-tag size="mini" effect="plain">{{ formatTier(school.tier) }}</el-tag>
                <el-tag v-if="school.is985 || school.is211 || school.isDoubleFirst" size="mini" type="success" effect="plain">
                  {{ schoolTag(school) }}
                </el-tag>
                <el-tag v-if="school.programCount !== undefined" size="mini" type="info" effect="plain">
                  专业 {{ school.programCount }}
                </el-tag>
                <el-tag v-if="school.pendingReviewCount" size="mini" type="warning" effect="plain">
                  待审 {{ school.pendingReviewCount }}
                </el-tag>
              </div>
            </button>
            <div v-if="!schoolsLoading && !schoolList.length" class="empty-block">
              <i class="el-icon-school"></i>
              <p>暂无学校数据</p>
              <span>可先从学校管理维护基础数据。</span>
            </div>
          </div>
        </section>

        <section class="workspace-panel matrix-panel">
          <div class="panel-heading">
            <div>
              <h3>学院 / 专业方向矩阵</h3>
              <span>{{ selectedSchool ? selectedSchool.name : '请选择学校' }} · {{ filteredProgramRows.length }} 个专业方向</span>
            </div>
            <div class="panel-actions">
              <el-select v-model="activeCollegeId" size="mini" clearable placeholder="全部学院" class="college-filter">
                <el-option v-for="college in collegeOptions" :key="college.id" :label="college.name" :value="college.id" />
              </el-select>
              <el-button size="mini" type="text" icon="el-icon-plus" @click="jumpToCrud('program')">维护专业</el-button>
            </div>
          </div>
          <div class="quality-legend">
            <span v-for="item in completenessOptions" :key="item.value">
              <i :class="'quality-dot quality-dot--' + item.value"></i>{{ item.label }}
            </span>
          </div>

          <div v-loading="workspaceLoading" class="matrix-body">
            <el-table
              v-if="filteredProgramRows.length"
              :data="filteredProgramRows"
              size="small"
              height="420"
              highlight-current-row
              @current-change="selectProgram"
            >
              <el-table-column label="学院" prop="collegeName" min-width="150" show-overflow-tooltip />
              <el-table-column label="专业代码" prop="programCode" width="100" align="center" />
              <el-table-column label="专业方向" prop="programName" min-width="180" show-overflow-tooltip />
              <el-table-column label="数据" width="86" align="center">
                <template slot-scope="scope">
                  <div class="data-flags">
                    <span :class="{ 'is-ready': scope.row.hasScore }">线</span>
                    <span :class="{ 'is-ready': scope.row.hasPlan }">计</span>
                    <span :class="{ 'is-ready': scope.row.hasResult }">录</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="408" prop="is408" width="70" align="center">
                <template slot-scope="scope">
                  <el-tag v-if="Number(scope.row.is408) === 1" type="success" size="mini">408</el-tag>
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="年份状态" min-width="210">
                <template slot-scope="scope">
                  <div class="year-cells">
                    <span
                      v-for="year in yearOptions"
                      :key="year"
                      class="year-cell"
                      :class="'year-cell--' + yearQuality(scope.row.id, year)"
                    >
                      {{ year }} {{ yearQuality(scope.row.id, year) }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="完整度" width="90" align="center">
                <template slot-scope="scope">
                  <el-tag :type="qualityType(scope.row.completenessLevel)" size="mini">
                    {{ scope.row.completenessLevel || '待接入' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>

            <div v-else-if="!workspaceLoading" class="empty-block empty-block--large">
              <i class="el-icon-data-board"></i>
              <p>{{ matrixEmptyTitle }}</p>
              <span>{{ matrixEmptyTip }}</span>
              <div class="empty-actions">
                <el-button size="mini" type="primary" @click="jumpToCrud('program')">维护专业方向</el-button>
                <el-button size="mini" @click="loadSelectedSchool">重新加载</el-button>
              </div>
            </div>
          </div>
        </section>

        <workspace-editor-panel
          :selected-school="selectedSchool"
          :selected-program="selectedProgram"
          :year="editorYear"
          :year-options="yearOptions"
          :program-years="selectedProgramYears"
          @saved="handleEditorSaved"
          @year-change="handleEditorYearChange"
        />
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
require('echarts/theme/macarons')
import { listSchool } from '@/api/postgrad/school'
import { listWorkspaceStats, listWorkspaceSchools, getSchoolWorkspace } from '@/api/postgrad/workspace'
import WorkspaceEditorPanel from './components/WorkspaceEditorPanel'

export default {
  name: 'PostgradWorkspace',
  components: { WorkspaceEditorPanel },
  data() {
    return {
      filters: {
        keyword: '',
        province: '',
        tier: '',
        year: 2025,
        is408: '1',
        completeness: ''
      },
      yearOptions: [2023, 2024, 2025],
      tierOptions: [
        { label: '985', value: '985' },
        { label: '211', value: '211' },
        { label: '双一流', value: 'DOUBLE_FIRST' },
        { label: '普通公办', value: 'PUBLIC_REGULAR' },
        { label: '民办', value: 'PRIVATE' },
        { label: '科研院所', value: 'RESEARCH_INSTITUTE' },
        { label: '其他', value: 'OTHER' }
      ],
      completenessOptions: [
        { label: 'A 完整', value: 'A' },
        { label: 'B 基本完整', value: 'B' },
        { label: 'C 缺关键项', value: 'C' },
        { label: 'D 严重缺失', value: 'D' }
      ],
      statsLoading: false,
      schoolsLoading: false,
      workspaceLoading: false,
      stats: null,
      statsNotice: '',
      schoolList: [],
      selectedSchool: null,
      workspace: null,
      selectedProgram: null,
      activeCollegeId: '',
      editorYear: 2025,
      schoolWorkspaceRequestSeq: 0,
      charts: {
        quality: null,
        coverage: null,
        trend: null
      }
    }
  },
  computed: {
    kpiItems() {
      const stats = this.stats || {}
      return [
        { key: 'school', label: 'N诺学校数', value: this.formatStat(stats.schoolCount, this.schoolList.length), hint: '当前筛选范围', tone: 'blue' },
        { key: 'college', label: '学院数', value: this.formatStat(stats.collegeCount), hint: '按学校聚合', tone: 'slate' },
        { key: 'program', label: '专业方向', value: this.formatStat(stats.programCount), hint: '按学院汇总', tone: 'slate' },
        { key: 'quality', label: 'N诺 A 级完整度', value: this.formatPercent(stats.aLevelRate), hint: 'N诺完整度分布', tone: 'green' },
        { key: 'review', label: '待审核', value: this.formatStat(stats.pendingReviewCount), hint: '审核中心联动', tone: 'amber' },
        { key: 'task', label: '缺失任务', value: this.formatStat(stats.missingTaskCount), hint: '采集任务联动', tone: 'red' }
      ]
    },
    currentStats() {
      return this.workspace && this.workspace.stats ? this.workspace.stats : {}
    },
    collegeOptions() {
      return this.workspace && this.workspace.colleges ? this.workspace.colleges : []
    },
    programRows() {
      if (!this.workspace || !this.workspace.programs) return []
      return this.workspace.programs
    },
    filteredProgramRows() {
      if (!this.activeCollegeId) return this.programRows
      return this.programRows.filter(item => String(item.collegeId) === String(this.activeCollegeId))
    },
    programYearMap() {
      const result = {}
      const rows = this.workspace && this.workspace.programYears ? this.workspace.programYears : []
      rows.forEach(item => {
        const key = item.programId + '-' + item.year
        result[key] = item
      })
      return result
    },
    selectedProgramYears() {
      if (!this.selectedProgram) return []
      return this.yearOptions.map(year => {
        const item = this.programYearMap[this.selectedProgram.id + '-' + year] || {}
        return Object.assign({ year }, item)
      })
    },
    matrixEmptyTitle() {
      if (!this.selectedSchool) return '请选择学校'
      return '暂无专业方向数据'
    },
    matrixEmptyTip() {
      if (!this.selectedSchool) return '从左侧选择学校后查看学院和专业方向矩阵。'
      return '当前筛选条件下没有可展示的专业方向，或接口返回为空。'
    }
  },
  created() {
    this.loadWorkspace()
  },
  mounted() {
    window.addEventListener('resize', this.resizeCharts)
    this.$nextTick(this.renderCharts)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeCharts)
    Object.keys(this.charts).forEach(key => {
      if (this.charts[key]) {
        this.charts[key].dispose()
        this.charts[key] = null
      }
    })
  },
  watch: {
    activeCollegeId() {
      this.selectedProgram = this.filteredProgramRows.length ? this.filteredProgramRows[0] : null
      this.$nextTick(this.renderCharts)
    }
  },
  methods: {
    loadWorkspace(options = {}) {
      this.loadStats()
      this.loadSchools(options)
    },
    loadStats() {
      this.statsLoading = true
      this.statsNotice = ''
      listWorkspaceStats(this.requestParams()).then(response => {
        this.stats = response.data || {}
        this.$nextTick(this.renderCharts)
      }).catch(() => {
        this.stats = null
        this.statsNotice = '工作台聚合统计请求失败，当前页面使用学校列表兜底展示。'
      }).finally(() => {
        this.statsLoading = false
      })
    },
    loadSchools(options = {}) {
      const reloadSelected = options.reloadSelected !== false
      this.schoolsLoading = true
      listWorkspaceSchools(this.requestParams()).then(response => {
        this.schoolList = this.normalizeSchoolRows(response.data || response.rows || [])
        this.ensureSelectedSchool(reloadSelected)
      }).catch(() => {
        listSchool({
          pageNum: 1,
          pageSize: 20,
          name: this.filters.keyword || undefined,
          province: this.filters.province || undefined,
          tier: this.filters.tier || undefined,
          status: 'active'
        }).then(response => {
          this.schoolList = this.normalizeSchoolRows(response.rows || [])
          this.ensureSelectedSchool(reloadSelected)
        }).catch(() => {
          this.schoolList = []
          this.selectedSchool = null
          this.workspace = null
        })
      }).finally(() => {
        this.schoolsLoading = false
      })
    },
    loadSelectedSchool() {
      if (!this.selectedSchool) return
      const requestSeq = ++this.schoolWorkspaceRequestSeq
      const selectedProgramId = this.selectedProgram ? this.selectedProgram.id : null
      this.workspaceLoading = true
      getSchoolWorkspace(this.selectedSchool.id, this.requestParams()).then(response => {
        if (requestSeq !== this.schoolWorkspaceRequestSeq) return
        this.workspace = response.data || {}
        if (this.activeCollegeId && !this.collegeOptions.some(item => String(item.id) === String(this.activeCollegeId))) {
          this.activeCollegeId = ''
        }
        if (this.filteredProgramRows.length) {
          this.selectedProgram = this.filteredProgramRows.find(item => String(item.id) === String(selectedProgramId)) || this.filteredProgramRows[0]
        } else {
          this.selectedProgram = null
        }
        this.$nextTick(this.renderCharts)
      }).catch(() => {
        if (requestSeq !== this.schoolWorkspaceRequestSeq) return
        this.workspace = null
        this.selectedProgram = null
      }).finally(() => {
        if (requestSeq === this.schoolWorkspaceRequestSeq) {
          this.workspaceLoading = false
        }
      })
    },
    selectSchool(school) {
      this.selectedSchool = school
      this.activeCollegeId = ''
      this.loadSelectedSchool()
    },
    selectProgram(row) {
      this.selectedProgram = row
      this.$nextTick(this.renderCharts)
    },
    handleEditorSaved() {
      this.loadSelectedSchool()
      this.loadStats()
    },
    handleEditorYearChange(year) {
      if (!year || Number(year) === Number(this.editorYear)) return
      this.editorYear = Number(year)
    },
    ensureSelectedSchool(reloadSelected = true) {
      if (!this.schoolList.length) {
        this.selectedSchool = null
        this.workspace = null
        this.selectedProgram = null
        return
      }
      const stillExists = this.selectedSchool && this.schoolList.some(item => item.id === this.selectedSchool.id)
      if (!stillExists) {
        this.selectedSchool = this.schoolList[0]
      }
      if (reloadSelected) {
        this.loadSelectedSchool()
      }
    },
    handleSearch() {
      this.editorYear = Number(this.filters.year)
      this.loadWorkspace()
    },
    resetFilters() {
      this.filters = {
        keyword: '',
        province: '',
        tier: '',
        year: 2025,
        is408: '1',
        completeness: ''
      }
      this.editorYear = Number(this.filters.year)
      this.loadWorkspace()
    },
    requestParams() {
      return {
        keyword: this.filters.keyword || undefined,
        province: this.filters.province || undefined,
        tier: this.filters.tier || undefined,
        year: this.filters.year,
        is408: this.filters.is408 || undefined,
        completeness: this.filters.completeness || undefined
      }
    },
    normalizeSchoolRows(rows) {
      return rows.map(item => ({
        id: item.id,
        name: item.name || item.schoolName,
        province: item.province,
        city: item.city,
        tier: item.tier || item.schoolTier,
        is985: item.is985 || item.is_985,
        is211: item.is211 || item.is_211,
        isDoubleFirst: item.isDoubleFirst || item.is_double_first,
        collegeCount: item.collegeCount,
        programCount: item.programCount,
        program408Count: item.program408Count,
        pendingReviewCount: item.pendingReviewCount,
        missingTaskCount: item.missingTaskCount,
        completenessLevel: item.completenessLevel
      })).filter(item => item.id && item.name)
    },
    yearQuality(programId, year) {
      const item = this.programYearMap[programId + '-' + year]
      return item && item.completenessLevel ? item.completenessLevel : 'D'
    },
    formatStat(value, fallback) {
      const target = value !== undefined && value !== null ? value : fallback
      return target !== undefined && target !== null ? target : '-'
    },
    formatPercent(value) {
      if (value === undefined || value === null || value === '') return '-'
      const number = Number(value)
      if (Number.isNaN(number)) return value
      return number > 1 ? number.toFixed(0) + '%' : (number * 100).toFixed(0) + '%'
    },
    formatTier(value) {
      const item = this.tierOptions.find(option => option.value === value)
      return item ? item.label : (value || '未分层')
    },
    schoolTag(school) {
      if (Number(school.is985) === 1) return '985'
      if (Number(school.is211) === 1) return '211'
      if (Number(school.isDoubleFirst) === 1) return '双一流'
      return '重点'
    },
    qualityType(value) {
      if (value === 'A') return 'success'
      if (value === 'B') return ''
      if (value === 'C') return 'warning'
      if (value === 'D') return 'danger'
      return 'info'
    },
    missingFieldsText(value) {
      if (!value) return '无'
      if (Array.isArray(value)) return value.length ? value.join('、') : '无'
      if (typeof value === 'string') {
        try {
          const parsed = JSON.parse(value)
          if (Array.isArray(parsed)) return parsed.length ? parsed.join('、') : '无'
        } catch (e) {
          return value
        }
      }
      return String(value)
    },
    chartPercent(value, total) {
      const number = Number(value || 0)
      const base = Number(total || 0)
      if (!base) return 0
      return Math.max(0, Math.min(100, Math.round(number * 100 / base)))
    },
    ensureChart(key, refName) {
      const el = this.$refs[refName]
      if (!el) return null
      if (!this.charts[key]) {
        this.charts[key] = echarts.init(el, 'macarons')
      }
      return this.charts[key]
    },
    renderCharts() {
      this.renderQualityChart()
      this.renderCoverageChart()
      this.renderTrendChart()
    },
    renderQualityChart() {
      const chart = this.ensureChart('quality', 'qualityChart')
      if (!chart) return
      const stats = this.stats || {}
      chart.setOption({
        color: ['#16a34a', '#3b82f6', '#f59e0b', '#dc2626'],
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
        series: [{
          name: '完整度',
          type: 'pie',
          radius: ['48%', '72%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}\\n{d}%' },
          data: [
            { name: 'A 完整', value: Number(stats.aLevelCount || 0) },
            { name: 'B 基本完整', value: Number(stats.bLevelCount || 0) },
            { name: 'C 缺关键项', value: Number(stats.cLevelCount || 0) },
            { name: 'D 严重缺失', value: Number(stats.dLevelCount || 0) }
          ]
        }]
      })
    },
    renderCoverageChart() {
      const chart = this.ensureChart('coverage', 'coverageChart')
      if (!chart) return
      const stats = this.stats || {}
      const total = Number(stats.programCount || 0)
      chart.setOption({
        color: ['#1e40af'],
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          formatter: params => {
            const item = params && params[0]
            return item ? item.name + ': ' + item.value + '%' : ''
          }
        },
        grid: { top: 22, left: 28, right: 16, bottom: 28, containLabel: true },
        xAxis: {
          type: 'category',
          data: ['复试线', '招生计划', '拟录取'],
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 100,
          axisLabel: { formatter: '{value}%' }
        },
        series: [{
          type: 'bar',
          barWidth: 28,
          data: [
            this.chartPercent(stats.scoreReadyCount, total),
            this.chartPercent(stats.planReadyCount, total),
            this.chartPercent(stats.resultReadyCount, total)
          ],
          label: { show: true, position: 'top', formatter: '{c}%' }
        }]
      })
    },
    renderTrendChart() {
      const chart = this.ensureChart('trend', 'trendChart')
      if (!chart) return
      const rows = this.selectedProgramYears
      chart.setOption({
        color: ['#1e40af', '#16a34a'],
        tooltip: { trigger: 'axis' },
        grid: { top: 24, left: 8, right: 8, bottom: 24, containLabel: true },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: rows.map(item => item.year)
        },
        yAxis: {
          type: 'value',
          min: value => Math.max(0, value.min - 10)
        },
        series: [{
          name: '复试线',
          type: 'line',
          smooth: true,
          data: rows.map(item => item.scoreLine || null),
          connectNulls: false,
          symbolSize: 7
        }, {
          name: '拟录取最低',
          type: 'line',
          smooth: true,
          data: rows.map(item => item.minAdmittedScore || null),
          connectNulls: false,
          symbolSize: 7
        }]
      })
    },
    resizeCharts() {
      Object.keys(this.charts).forEach(key => {
        if (this.charts[key]) this.charts[key].resize()
      })
    },
    jumpToCrud(module) {
      this.$router.push({
        path: '/postgrad/' + module,
        query: {
          schoolId: this.selectedSchool ? this.selectedSchool.id : undefined,
          collegeId: this.selectedProgram ? this.selectedProgram.collegeId : undefined,
          programId: this.selectedProgram ? this.selectedProgram.id : undefined,
          year: this.filters.year
        }
      })
    },
    jumpReview() {
      this.$router.push({
        path: '/postgrad/review',
        query: {
          schoolName: this.selectedSchool ? this.selectedSchool.name : undefined,
          programCode: this.selectedProgram ? this.selectedProgram.programCode : undefined,
          year: this.filters.year,
          status: 'pending'
        }
      })
    }
  }
}
</script>

<style scoped>
.workspace-page {
  min-height: calc(100vh - 84px);
  background: #f8fafc;
}

.workspace-shell {
  color: #0f172a;
}

.workspace-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.workspace-eyebrow {
  margin-bottom: 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.workspace-header h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
}

.workspace-header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.workspace-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.workspace-filter,
.workspace-panel,
.kpi-item {
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.04);
}

.workspace-filter {
  margin-bottom: 12px;
  padding: 14px 14px 2px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.kpi-item {
  padding: 12px;
  min-height: 86px;
}

.kpi-item__label {
  color: #64748b;
  font-size: 12px;
}

.kpi-item__value {
  margin-top: 6px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

.kpi-item__hint {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.kpi-item--blue {
  border-top: 3px solid #1e40af;
}

.kpi-item--green {
  border-top: 3px solid #16a34a;
}

.kpi-item--amber {
  border-top: 3px solid #f59e0b;
}

.kpi-item--red {
  border-top: 3px solid #dc2626;
}

.kpi-item--slate {
  border-top: 3px solid #64748b;
}

.workspace-alert {
  margin-bottom: 12px;
}

.viz-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.viz-panel {
  min-height: 260px;
}

.viz-chart {
  height: 198px;
  padding: 8px 10px 12px;
}

.school-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
}

.school-summary__main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.school-summary__main strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.school-summary__main span {
  color: #475569;
  font-size: 12px;
}

.school-summary__metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
  color: #475569;
  font-size: 12px;
}

.school-summary__metrics span {
  padding: 4px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.75);
}

.school-summary__metrics b {
  color: #1e40af;
}

.workspace-grid {
  display: grid;
  grid-template-columns: 300px minmax(560px, 1fr) 520px;
  gap: 12px;
  align-items: stretch;
}

.workspace-panel {
  min-height: 520px;
  overflow: hidden;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
}

.panel-heading h3 {
  margin: 0;
  font-size: 15px;
  line-height: 1.3;
}

.panel-heading span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.college-filter {
  width: 150px;
}

.quality-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}

.quality-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.quality-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.quality-dot--A {
  background: #16a34a;
}

.quality-dot--B {
  background: #3b82f6;
}

.quality-dot--C {
  background: #f59e0b;
}

.quality-dot--D {
  background: #dc2626;
}

.school-list {
  height: 462px;
  overflow: auto;
  padding: 8px;
}

.school-item {
  width: 100%;
  margin: 0 0 8px;
  padding: 11px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.school-item:hover,
.school-item.is-active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.school-item__main {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.school-item__main strong {
  color: #0f172a;
  font-size: 14px;
}

.school-item__main span {
  color: #64748b;
  font-size: 12px;
}

.school-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.matrix-body {
  min-height: 462px;
  padding: 12px;
}

.year-cells {
  display: flex;
  gap: 6px;
}

.year-cell {
  min-width: 54px;
  padding: 3px 6px;
  border-radius: 6px;
  font-size: 12px;
  text-align: center;
}

.year-cell--A {
  background: #dcfce7;
  color: #166534;
}

.year-cell--B {
  background: #dbeafe;
  color: #1e40af;
}

.year-cell--C {
  background: #fef3c7;
  color: #92400e;
}

.year-cell--D {
  background: #fee2e2;
  color: #991b1b;
}

.data-flags {
  display: inline-flex;
  gap: 4px;
}

.data-flags span {
  width: 20px;
  height: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.data-flags span.is-ready {
  border-color: #bbf7d0;
  background: #dcfce7;
  color: #166534;
}

.muted {
  color: #94a3b8;
}

.detail-body {
  padding: 16px;
}

.detail-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-title strong {
  font-size: 17px;
}

.detail-title span {
  color: #64748b;
  font-size: 13px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.detail-section {
  margin-top: 18px;
}

.detail-section h4 {
  margin: 0 0 10px;
  font-size: 14px;
}

.data-state-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.data-state-grid div {
  min-width: 0;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.data-state-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.data-state-grid strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.2;
}

.issue-list {
  display: grid;
  gap: 8px;
}

.issue-list span {
  display: flex;
  min-height: 30px;
  align-items: center;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
}

.issue-list span.is-warning {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.issue-list span.is-danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.action-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.trend-chart {
  height: 180px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.empty-block {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #64748b;
  text-align: center;
}

.empty-block--large {
  min-height: 420px;
}

.empty-block i {
  margin-bottom: 10px;
  color: #94a3b8;
  font-size: 34px;
}

.empty-block p {
  margin: 0 0 6px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.empty-block span {
  max-width: 320px;
  color: #94a3b8;
  font-size: 12px;
}

.empty-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

@media (max-width: 1500px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .workspace-grid {
    grid-template-columns: 260px minmax(420px, 1fr);
  }

  .detail-panel {
    grid-column: 1 / -1;
    min-height: 360px;
  }
}

@media (max-width: 900px) {
  .workspace-header {
    flex-direction: column;
  }

  .school-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .panel-actions {
    align-items: flex-end;
    flex-direction: column;
  }

  .kpi-grid,
  .viz-grid,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-panel {
    min-height: 360px;
  }
}
</style>
