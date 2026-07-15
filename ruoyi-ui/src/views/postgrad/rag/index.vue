<template>
  <div class="app-container rag-page">
    <div class="rag-shell">
      <div class="rag-header">
        <div>
          <h2>知识库管理</h2>
          <p>查看索引状态、检索测试、调用记录和配置参数，监控 RAG 管道运行状态。</p>
        </div>
        <div class="rag-actions">
          <el-button size="mini" icon="el-icon-refresh" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <div class="rag-kpis">
        <div v-for="item in kpis" :key="item.key" class="rag-kpi">
          <div class="rag-kpi__label">{{ item.label }}</div>
          <div class="rag-kpi__value">{{ item.value }}</div>
          <div class="rag-kpi__hint">{{ item.hint }}</div>
        </div>
      </div>

      <el-alert
        v-if="notice"
        :title="notice"
        type="info"
        show-icon
        :closable="false"
        class="rag-alert"
      />

      <!-- 索引管理面板 -->
      <section class="rag-panel" style="margin-bottom: 12px;">
        <div class="panel-heading">
          <h3>索引管理</h3>
        </div>
        <div style="padding: 16px;">
          <div class="index-status-line">
            <span class="index-stat">状态: <el-tag :type="statusTag" size="mini">{{ statusText }}</el-tag></span>
            <span class="index-stat">文档: {{ status.documentCount || 0 }}</span>
            <span class="index-stat">切片: {{ status.chunkCount || 0 }}</span>
          </div>
          <div class="index-version-line">
            活动版本: <code>{{ displayCollection }}</code>
          </div>

          <el-form :inline="true" :model="buildForm" size="mini" class="index-build-form">
            <el-form-item label="起始年份">
              <el-input-number v-model="buildForm.startYear" :min="2000" :max="2100" controls-position="right" />
            </el-form-item>
            <el-form-item label="结束年份">
              <el-input-number v-model="buildForm.endYear" :min="2000" :max="2100" controls-position="right" />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                icon="el-icon-refresh-right"
                :loading="building"
                :disabled="building || !hasReindexPerm"
                @click="handleBuild"
              >
                重建完整知识库
              </el-button>
            </el-form-item>
          </el-form>

          <div v-if="building && buildRun" class="index-progress-box">
            <el-progress :percentage="buildRun.progressPercent || 0" :status="progressStatus" />
            <p class="index-progress-msg">{{ progressMessage }}</p>
          </div>

          <div v-if="hasRecentRuns" class="index-recent-section">
            <h4 class="index-recent-title">最近批次</h4>
            <div v-for="run in recentRuns" :key="run.runId" class="index-recent-item">
              <el-tag :type="runTagType(run)" size="mini">{{ run.status }}</el-tag>
              <code class="index-run-id">{{ run.runId ? run.runId.slice(0, 8) : '-' }}</code>
              <span class="index-run-desc">{{ ' — ' }}{{ formatRunDescription(run) }}</span>
            </div>
          </div>
        </div>
      </section>

      <div class="rag-grid">
        <!-- 状态面板 -->
        <section class="rag-panel">
          <div class="panel-heading">
            <div>
              <h3>当前状态</h3>
              <span>索引、向量库后端与配置参数</span>
            </div>
          </div>
          <el-descriptions :column="2" border size="small" class="rag-desc">
            <el-descriptions-item label="向量库后端">{{ status.backend || 'Qdrant' }}</el-descriptions-item>
            <el-descriptions-item label="集合名称">{{ status.collectionName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="可连通">{{ boolText(status.reachable) }}</el-descriptions-item>
            <el-descriptions-item label="已索引">{{ boolText(status.indexed) }}</el-descriptions-item>
            <el-descriptions-item label="文档数">{{ status.documentCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="切片数">{{ status.chunkCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="最近索引">{{ formatTime(status.lastIndexedAt) }}</el-descriptions-item>
            <el-descriptions-item label="运行模式">{{ status.lastRunMode || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="error-box" :class="{ 'is-empty': !status.lastError }">
            <strong>最近错误</strong>
            <p>{{ status.lastError || '暂无' }}</p>
          </div>
        </section>

        <!-- 检索测试面板 -->
        <section class="rag-panel">
          <div class="panel-heading">
            <div>
              <h3>检索测试</h3>
              <span>直接测试检索管道（跳过 LLM），验证 Embedding → 检索 → 重排序环节</span>
            </div>
          </div>
          <div class="search-test-box">
            <el-input
              v-model="searchQuery"
              placeholder="输入查询，如：调剂是什么意思"
              clearable
              @keyup.enter.native="handleSearchTest"
            >
              <el-button
                slot="append"
                icon="el-icon-search"
                :loading="searchTesting"
                @click="handleSearchTest"
              >
                测试召回
              </el-button>
            </el-input>
            <el-form :inline="true" :model="searchFilters" size="mini" class="search-filter-form">
              <el-form-item label="schoolId">
                <el-input v-model="searchFilters.schoolId" clearable placeholder="学校ID" />
              </el-form-item>
              <el-form-item label="programId">
                <el-input v-model="searchFilters.programId" clearable placeholder="专业ID" />
              </el-form-item>
              <el-form-item label="year">
                <el-input v-model="searchFilters.year" clearable placeholder="年份" />
              </el-form-item>
              <el-form-item label="docType">
                <el-select v-model="searchFilters.docType" clearable placeholder="文档类型">
                  <el-option label="program_history" value="program_history" />
                  <el-option label="program_year" value="program_year" />
                  <el-option label="data_quality" value="data_quality" />
                </el-select>
              </el-form-item>
              <el-form-item label="section">
                <el-input v-model="searchFilters.section" clearable placeholder="如 score_trend" />
              </el-form-item>
            </el-form>
            <div v-if="searchError" class="search-error">{{ searchError }}</div>

            <div v-if="searchResult" class="search-result">
              <div class="search-meta">
                <span>召回 {{ searchResult.retrievedCount }} 条</span>
                <span v-if="searchResult.rerankEnabled">| Rerank 后选中 {{ searchResult.selectedCount }} 条</span>
                <span v-if="searchResult.rerankFailed" class="warn-text">| Rerank 失败，回退向量排序</span>
                <span class="latency-text">
                  | 总耗时 {{ searchResult.latency ? searchResult.latency.totalMs : '-' }}ms
                </span>
              </div>
              <div v-if="searchResult.appliedFilters && Object.keys(searchResult.appliedFilters).length" class="active-filters">
                <el-tag
                  v-for="(v, k) in searchResult.appliedFilters"
                  :key="k"
                  size="mini"
                  type="info"
                >
                  {{ k }}={{ v }}
                </el-tag>
              </div>

              <el-table :data="searchResult.chunks" size="mini" stripe class="search-table">
                <el-table-column prop="vectorRank" label="向量排名" width="80" />
                <el-table-column prop="rerankRank" label="Rerank排名" width="90">
                  <template slot-scope="scope">
                    {{ scope.row.rerankRank || '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="rerankScore" label="Rerank分" width="85">
                  <template slot-scope="scope">
                    <span v-if="scope.row.rerankScore != null">{{ scope.row.rerankScore.toFixed(3) }}</span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="选中" width="55">
                  <template slot-scope="scope">
                    <el-tag v-if="scope.row.selectedForPrompt" type="success" size="mini">是</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceName" label="来源文档" min-width="140" />
                <el-table-column prop="schoolId" label="schoolId" width="80" />
                <el-table-column prop="programId" label="programId" width="85" />
                <el-table-column prop="year" label="年份" width="65" />
                <el-table-column prop="docType" label="文档类型" width="120" />
                <el-table-column prop="section" label="章节" width="120" />
                <el-table-column prop="chunkIndex" label="切片序号" width="80" />
                <el-table-column prop="contentPreview" label="内容预览" min-width="200" show-overflow-tooltip />
              </el-table>
            </div>
          </div>
        </section>
      </div>

      <!-- RAG 评测 -->
      <section class="rag-panel eval-section" style="margin-top:12px">
        <div class="panel-heading">
          <div>
            <h3>RAG 评测</h3>
            <span>基于 Metadata 匹配的检索排序质量评测，验证 Embedding → Qdrant → Rerank 管道精度</span>
          </div>
          <el-button
            size="mini"
            type="primary"
            icon="el-icon-s-data"
            :loading="evalRunning"
            @click="handleRunEval"
          >
            运行评测
          </el-button>
        </div>

        <div v-if="evalResult" style="padding: 16px;">
          <!-- 指标卡片 -->
          <div class="eval-metrics">
            <div class="eval-metric-card">
              <div class="eval-metric__value">{{ (evalResult.passRate * 100).toFixed(1) }}%</div>
              <div class="eval-metric__label">Metadata 匹配率</div>
              <div class="eval-metric__hint">{{ evalResult.passedCases }}/{{ evalResult.totalCases }} 用例通过</div>
            </div>
            <div class="eval-metric-card">
              <div class="eval-metric__value">{{ evalResult.mrr != null ? evalResult.mrr.toFixed(3) : '-' }}</div>
              <div class="eval-metric__label">MRR</div>
              <div class="eval-metric__hint">Mean Reciprocal Rank</div>
            </div>
            <div class="eval-metric-card">
              <div class="eval-metric__value">{{ evalResult.hitAt5 != null ? (evalResult.hitAt5 * 100).toFixed(1) + '%' : '-' }}</div>
              <div class="eval-metric__label">Hit@{{ evalResult.evalK || 5 }}</div>
              <div class="eval-metric__hint">第一个相关 Chunk 在 Top-K 中的比例</div>
            </div>
            <div class="eval-metric-card">
              <div class="eval-metric__value">{{ evalResult.precisionAt5 != null ? (evalResult.precisionAt5 * 100).toFixed(1) + '%' : '-' }}</div>
              <div class="eval-metric__label">Precision@{{ evalResult.evalK || 5 }}</div>
              <div class="eval-metric__hint">Top-K 中相关 Chunk 的平均占比</div>
            </div>
          </div>

          <!-- 用例详情表 -->
          <div v-if="evalResult.cases && evalResult.cases.length" style="margin-top: 16px;">
            <h4 class="eval-table-title">用例详情</h4>
            <el-table :data="evalResult.cases" size="mini" stripe class="eval-table">
              <el-table-column prop="id" label="用例 ID" width="160" show-overflow-tooltip />
              <el-table-column prop="query" label="查询" min-width="180" show-overflow-tooltip />
              <el-table-column label="结果" width="80">
                <template slot-scope="scope">
                  <el-tag v-if="scope.row.passed" type="success" size="mini">通过</el-tag>
                  <el-tag v-else type="danger" size="mini">失败</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="firstRelevantRank" label="首个匹配排名" width="110">
                <template slot-scope="scope">
                  <span v-if="scope.row.firstRelevantRank > 0">{{ scope.row.firstRelevantRank }}</span>
                  <span v-else class="warn-text">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="relevantInTopK" :label="`Top-${evalResult.evalK || 5} 命中数`" width="120" />
              <el-table-column prop="failureReason" label="失败原因" min-width="140">
                <template slot-scope="scope">
                  <span v-if="!scope.row.passed && scope.row.failureReason" class="warn-text">
                    {{ scope.row.failureReason }}
                  </span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="匹配证据" min-width="200">
                <template slot-scope="scope">
                  <el-tag
                    v-for="(e, i) in (scope.row.evidence || []).slice(0, 3)"
                    :key="i"
                    size="mini"
                    type="info"
                    style="margin: 2px"
                  >{{ e }}</el-tag>
                  <span v-if="(scope.row.evidence || []).length === 0">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div v-else-if="!evalRunning" class="trace-empty" style="margin: 14px;">
          点击"运行评测"对当前知识库执行检索排序质量评测。
        </div>
      </section>

      <!-- 最近 Trace -->
      <section class="rag-panel trace-section" style="margin-top:12px">
        <div class="panel-heading">
          <div>
            <h3>最近 RAG Trace</h3>
            <span>展示 AI Chat 和检索测试最近产生的 RAG 调用记录</span>
          </div>
          <el-button size="mini" icon="el-icon-refresh" @click="loadTraces">刷新 Trace</el-button>
        </div>
        <div v-if="traces.length === 0" class="trace-empty">暂无 RAG Trace 记录</div>
        <el-table v-else :data="traces" size="mini" stripe class="trace-table">
          <el-table-column prop="operationKey" label="操作" width="100">
            <template slot-scope="scope">
              <el-tag v-if="scope.row.operationKey === 'ai-chat'" type="primary" size="mini">AI Chat</el-tag>
              <el-tag v-else-if="scope.row.operationKey === 'search-test'" type="warning" size="mini">检索测试</el-tag>
              <el-tag v-else-if="scope.row.operationKey === 'recommendation-evidence'" type="success" size="mini">推荐证据</el-tag>
              <el-tag v-else-if="scope.row.operationKey === 'database-reindex'" type="info" size="mini">数据库索引</el-tag>
              <el-tag v-else-if="scope.row.operationKey === 'database-generate'" type="info" size="mini">文档生成</el-tag>
              <el-tag v-else-if="scope.row.operationKey === 'rag-evaluate'" type="danger" size="mini">RAG 评测</el-tag>
              <span v-else>{{ scope.row.operationKey }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="runId" label="Run ID" width="105" show-overflow-tooltip />
          <el-table-column prop="retrievalRoute" label="检索路由" width="115" />
          <el-table-column label="结果" width="70">
            <template slot-scope="scope">
              <el-tag v-if="scope.row.outcome === 'SUCCESS'" type="success" size="mini">成功</el-tag>
              <el-tag v-else type="danger" size="mini">失败</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="query" label="查询" min-width="160" show-overflow-tooltip />
          <el-table-column label="召回/选中" width="90">
            <template slot-scope="scope">
              {{ scope.row.retrievedCount }}/{{ scope.row.selectedCount }}
            </template>
          </el-table-column>
          <el-table-column label="Top来源" min-width="140">
            <template slot-scope="scope">
              <template v-if="scope.row.topSources && scope.row.topSources.length">
                <el-tag v-for="s in scope.row.topSources.slice(0,2)" :key="s" size="mini" style="margin:2px">{{ s }}</el-tag>
              </template>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="过滤/回退" min-width="150">
            <template slot-scope="scope">
              <span v-if="scope.row.fallbackUsed" class="warn-text">fallback: {{ scope.row.fallbackMode || '-' }}</span>
              <template v-else-if="scope.row.metadataFilters">
                <el-tag v-for="(v, k) in scope.row.metadataFilters" :key="k" size="mini" type="info" style="margin:2px">
                  {{ k }}={{ v }}
                </el-tag>
              </template>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="延迟" width="150">
            <template slot-scope="scope">
              <div>{{ scope.row.totalLatencyMs }}ms</div>
              <div v-if="scope.row.embeddingLatencyMs != null" class="trace-latency-detail">E {{ scope.row.embeddingLatencyMs }}ms</div>
              <div v-if="scope.row.retrievalLatencyMs != null" class="trace-latency-detail">Q {{ scope.row.retrievalLatencyMs }}ms</div>
              <div v-if="scope.row.rerankLatencyMs != null" class="trace-latency-detail">R {{ scope.row.rerankLatencyMs }}ms</div>
            </template>
          </el-table-column>
          <el-table-column label="错误" min-width="120" show-overflow-tooltip>
            <template slot-scope="scope">
              <span v-if="scope.row.errorSummary" class="warn-text">{{ scope.row.errorSummary }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script>
import {
  getRagStatus,
  startIndexBuild,
  getIndexRun,
  searchTestRag,
  getRagTraces,
  runRagEval
} from '@/api/postgrad/rag'

export default {
  name: 'PostgradRagMonitor',
  data() {
    return {
      loading: false,
      status: {},
      notice: '',
      buildForm: {
        startYear: 2024,
        endYear: 2025
      },
      building: false,
      buildRunId: null,
      buildRun: null,
      latestRun: null,
      pollTimer: null,
      pollFailures: 0,
      searchQuery: '',
      searchFilters: {
        schoolId: '',
        programId: '',
        year: '',
        docType: '',
        section: ''
      },
      searchTesting: false,
      searchResult: null,
      searchError: '',
      traces: [],
      evalRunning: false,
      evalResult: null,
      evalError: ''
    }
  },
  computed: {
    hasReindexPerm() {
      try {
        const perms = this.$store?.getters?.permissions || []
        return perms.includes('postgrad:rag:reindex') || perms.includes('*:*:*')
      } catch (e) {
        return true
      }
    },
    kpis() {
      return [
        { key: 'backend', label: '向量库后端', value: this.status.backend || 'Qdrant', hint: '当前使用的向量存储' },
        { key: 'reachable', label: '可连通', value: this.boolText(this.status.reachable), hint: 'Qdrant 是否可达' },
        { key: 'indexed', label: '索引状态', value: this.boolText(this.status.indexed), hint: '文档是否已加载' },
        { key: 'docs', label: '文档数', value: this.status.documentCount || 0, hint: '总文档数' },
        { key: 'chunks', label: '切片数', value: this.status.chunkCount || 0, hint: '分块后的总片段数' },
        { key: 'lastIndexed', label: '最近索引', value: this.relativeTime(this.status.lastIndexedAt), hint: '上次成功索引时间' }
      ]
    },
    statusText() {
      return this.status.latestRunStatus || (this.status.indexed ? '已索引' : '未构建')
    },
    statusTag() {
      const s = this.status.latestRunStatus
      if (s === 'SUCCEEDED') return 'success'
      if (s === 'FAILED') return 'danger'
      if (s === 'RUNNING' || s === 'PENDING') return 'warning'
      return 'info'
    },
    displayCollection() {
      return this.status.activeCollection || this.status.collectionName || '-'
    },
    progressStatus() {
      if (!this.buildRun) return undefined
      if (this.buildRun.status === 'FAILED') return 'exception'
      if (this.buildRun.status === 'SUCCEEDED') return 'success'
      return undefined
    },
    progressMessage() {
      if (!this.buildRun) return '正在启动...'
      const run = this.buildRun
      const stage = run.progressStage || 'init'
      const pct = run.progressPercent || 0
      switch (stage) {
        case 'init':
          return '正在初始化...'
        case 'assembling':
          return '正在组装语料库...'
        case 'embedding': {
          if (run.indexedChunkCount != null && run.chunkCount != null) {
            return `正在向量化 ${pct}%（${run.indexedChunkCount}/${run.chunkCount} 切片）`
          }
          return `正在向量化 ${pct}%...`
        }
        case 'validating':
          return '正在验证索引...'
        case 'switching':
          return '正在切换集合...'
        default:
          return `${stage} ${pct}%`
      }
    },
    recentRuns() {
      const map = new Map()
      if (this.buildRun) map.set(this.buildRun.runId, this.buildRun)
      if (this.latestRun) map.set(this.latestRun.runId, this.latestRun)
      return Array.from(map.values())
    },
    hasRecentRuns() {
      return this.recentRuns.length > 0
    }
  },
  created() {
    this.loadAll()
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    async loadAll() {
      this.notice = ''
      await this.loadStatus()
      this.loadTraces()
      if (this.status.latestRunId) {
        await this.loadLatestRun()
      }
    },
    async loadStatus() {
      this.loading = true
      try {
        const res = await getRagStatus()
        this.status = (res && res.data) || {}
      } catch (e) {
        this.notice = 'RAG 状态请求失败，请检查后端接口是否启动。'
      } finally {
        this.loading = false
      }
    },
    async loadLatestRun() {
      if (!this.status.latestRunId) return
      try {
        const res = await getIndexRun(this.status.latestRunId)
        this.latestRun = (res && res.data) || null
        if (this.latestRun && !this.isTerminalBuildStatus(this.latestRun.status)) {
          this.resumeBuildPolling(this.latestRun)
        }
      } catch (e) {
        this.notice = '最近索引批次加载失败，请刷新后重试。'
      }
    },
    async loadTraces() {
      try {
        const res = await getRagTraces(20)
        this.traces = (res && res.data) || []
      } catch (e) {
        // traces are non-critical, ignore silently
      }
    },
    async handleRunEval() {
      this.evalRunning = true
      this.evalError = ''
      this.evalResult = null
      try {
        const res = await runRagEval()
        this.evalResult = (res && res.data) || null
        this.loadTraces()
      } catch (e) {
        this.evalError = '评测请求失败，请检查后端是否正常'
      } finally {
        this.evalRunning = false
      }
    },
    async handleBuild() {
      this.$confirm('确认重建完整知识库吗？这会重新生成所有文档并重建 Qdrant 向量库。', '重建完整知识库').then(async () => {
        this.stopPolling()
        this.building = true
        this.buildRunId = null
        this.buildRun = null
        this.pollFailures = 0
        try {
          const res = await startIndexBuild({
            startYear: this.buildForm.startYear,
            endYear: this.buildForm.endYear
          })
          const runId = (res && res.data && res.data.runId) || null
          if (!runId) {
            this.$message.error('启动索引构建失败：未获取到批次 ID')
            this.resetBuildState()
            return
          }
          this.buildRunId = runId

          // Fetch initial run state
          try {
            const runRes = await getIndexRun(runId)
            this.buildRun = (runRes && runRes.data) || null
          } catch (e) {
            // initial fetch may fail if run not yet persisted; poll will catch up
          }

          this.resumeBuildPolling(this.buildRun || { runId, status: 'PENDING', progressPercent: 0 })
        } catch (e) {
          this.resetBuildState()
          this.$message.error('启动索引构建失败')
        }
      }).catch(() => {})
    },
    async pollBuildRun() {
      if (!this.buildRunId) return
      try {
        const res = await getIndexRun(this.buildRunId)
        this.buildRun = (res && res.data) || null
        this.pollFailures = 0
        if (!this.buildRun) return

        const status = this.buildRun.status
        if (this.isTerminalBuildStatus(status)) {
          await this.finishBuild(this.buildRun, true)
        }
      } catch (e) {
        this.pollFailures += 1
        if (this.pollFailures >= 3) {
          this.stopPolling()
          this.building = false
          this.notice = '索引批次状态连续请求失败，已停止自动轮询，请刷新页面重试。'
          this.$message.error(this.notice)
        }
      }
    },
    resumeBuildPolling(run) {
      if (!run || !run.runId || this.isTerminalBuildStatus(run.status)) return
      this.stopPolling()
      this.buildRunId = run.runId
      this.buildRun = run
      this.building = true
      this.pollFailures = 0
      this.pollTimer = setInterval(() => this.pollBuildRun(), 2000)
    },
    async finishBuild(run, notify) {
      this.stopPolling()
      this.building = false
      this.buildRunId = null
      this.pollFailures = 0
      this.buildRun = run
      this.latestRun = run
      if (notify) {
        const succeeded = run.status === 'SUCCEEDED'
        this.$message({
          type: succeeded ? 'success' : 'error',
          message: succeeded ? '知识库重建完成' : `知识库重建失败：${run.errorSummary || run.errorCode || run.status}`
        })
      }
      await this.loadStatus()
      this.loadTraces()
    },
    isTerminalBuildStatus(status) {
      return status === 'SUCCEEDED' || status === 'FAILED' || status === 'INTERRUPTED'
    },
    resetBuildState() {
      this.stopPolling()
      this.building = false
      this.buildRunId = null
      this.buildRun = null
      this.pollFailures = 0
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    async handleSearchTest() {
      const q = (this.searchQuery || '').trim()
      if (!q) {
        this.searchError = '请输入查询内容'
        return
      }
      this.searchTesting = true
      this.searchError = ''
      this.searchResult = null
      try {
        const res = await searchTestRag({
          query: q,
          ...this.compactFilters(this.searchFilters)
        })
        this.searchResult = (res && res.data) || null
        if (this.searchResult && this.searchResult.errorSummary) {
          this.searchError = this.searchResult.errorSummary
        }
        this.loadTraces()
      } catch (e) {
        this.searchError = '检索测试失败，请检查后端是否正常'
      } finally {
        this.searchTesting = false
      }
    },
    boolText(value) {
      return value ? '是' : '否'
    },
    formatTime(value) {
      if (!value) return '-'
      return String(value)
    },
    relativeTime(value) {
      if (!value) return '-'
      const now = new Date()
      const then = new Date(value)
      const diffMs = now - then
      if (diffMs < 0) return '刚刚'
      const diffSec = Math.floor(diffMs / 1000)
      if (diffSec < 60) return `${diffSec}秒前`
      const diffMin = Math.floor(diffSec / 60)
      if (diffMin < 60) return `${diffMin}分钟前`
      const diffHour = Math.floor(diffMin / 60)
      if (diffHour < 24) return `${diffHour}小时前`
      return `${Math.floor(diffHour / 24)}天前`
    },
    formatRunDescription(run) {
      if (!run) return ''
      const status = run.status
      if (status === 'RUNNING' || status === 'PENDING') {
        const stage = run.progressStage || 'init'
        const pct = run.progressPercent != null ? `${run.progressPercent}%` : ''
        if (run.indexedChunkCount != null && run.chunkCount != null) {
          return `${stage} ${pct} (${run.indexedChunkCount}/${run.chunkCount} chunks)`
        }
        return `${stage} ${pct}`
      }
      if (status === 'SUCCEEDED') {
        const totalDocs = (run.generalDocumentCount || 0) + (run.databaseDocumentCount || 0)
        const chunks = run.chunkCount || 0
        const time = this.relativeTime(run.finishedAt)
        return `${totalDocs} docs, ${chunks} chunks, ${time}`
      }
      if (status === 'FAILED') {
        return `Error: ${run.errorSummary || run.errorCode || 'unknown'}`
      }
      return status
    },
    runTagType(run) {
      if (!run) return 'info'
      const s = run.status
      if (s === 'SUCCEEDED') return 'success'
      if (s === 'FAILED' || s === 'INTERRUPTED') return 'danger'
      if (s === 'RUNNING' || s === 'PENDING') return 'warning'
      return 'info'
    },
    compactFilters(filters) {
      const result = {}
      Object.keys(filters || {}).forEach(key => {
        const value = filters[key]
        if (value !== null && value !== undefined && String(value).trim() !== '') {
          result[key] = String(value).trim()
        }
      })
      return result
    }
  }
}
</script>

<style scoped>
.rag-page {
  min-height: calc(100vh - 84px);
  background: linear-gradient(180deg, #f8fafc 0%, #eef4ff 100%);
}

.rag-shell {
  color: #0f172a;
}

.rag-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.rag-eyebrow {
  margin-bottom: 6px;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.rag-header h2 {
  margin: 0;
  font-size: 24px;
}

.rag-header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.rag-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.rag-kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.rag-kpi,
.rag-panel {
  border: 1px solid #dbe5f2;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.rag-kpi {
  padding: 12px;
  min-height: 90px;
}

.rag-kpi__label {
  color: #64748b;
  font-size: 12px;
}

.rag-kpi__value {
  margin-top: 6px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 700;
}

.rag-kpi__hint {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.rag-alert {
  margin-bottom: 12px;
}

.rag-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.rag-panel {
  min-height: 320px;
  overflow: hidden;
}

.trace-section {
  min-height: auto;
}

.panel-heading {
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-heading h3 {
  margin: 0;
  font-size: 15px;
}

.panel-heading span {
  display: block;
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.rag-desc {
  margin: 14px;
}

.error-box,
.trace-hint,
.trace-empty {
  margin: 0 14px 14px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}

.error-box strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
}

.error-box.is-empty {
  opacity: 0.8;
}

.search-test-box {
  padding: 14px;
}

.search-error {
  margin-top: 8px;
  color: #ef4444;
  font-size: 13px;
}

.search-filter-form {
  margin-top: 10px;
}

.search-filter-form ::v-deep .el-form-item {
  margin-bottom: 8px;
}

.search-result {
  margin-top: 12px;
}

.search-meta {
  margin-bottom: 8px;
  font-size: 13px;
  color: #64748b;
}

.search-meta .warn-text {
  color: #f59e0b;
}

.search-meta .latency-text {
  color: #1d4ed8;
  font-weight: 600;
}

.search-table {
  font-size: 12px;
}

.trace-table {
  margin: 12px;
  font-size: 12px;
}

.trace-latency-detail {
  color: #64748b;
  line-height: 1.35;
}

.warn-text {
  color: #f59e0b;
}

/* 索引管理面板样式 */
.index-status-line {
  display: flex;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 14px;
}

.index-stat {
  color: #0f172a;
}

.index-version-line {
  margin-bottom: 14px;
  color: #64748b;
  font-size: 13px;
}

.index-version-line code {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #1d4ed8;
}

.index-build-form {
  margin-bottom: 0;
}

.index-progress-box {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.index-progress-msg {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.index-recent-section {
  margin-top: 14px;
  border-top: 1px solid #e2e8f0;
  padding-top: 12px;
}

.index-recent-title {
  margin: 0 0 8px;
  font-size: 13px;
  color: #64748b;
}

.index-recent-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
}

.index-run-id {
  font-family: monospace;
  color: #1d4ed8;
  font-size: 12px;
}

.index-run-desc {
  color: #475569;
}

@media (max-width: 1500px) {
  .rag-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .rag-grid {
    grid-template-columns: 1fr;
  }
}

/* 评测面板 */
.eval-section {
  min-height: auto;
}

.eval-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.eval-metric-card {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  text-align: center;
}

.eval-metric__value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.eval-metric__label {
  margin-top: 4px;
  font-size: 13px;
  color: #1d4ed8;
  font-weight: 600;
}

.eval-metric__hint {
  margin-top: 6px;
  font-size: 11px;
  color: #94a3b8;
}

.eval-table-title {
  margin: 0 0 8px;
  font-size: 13px;
  color: #64748b;
}

.eval-table {
  font-size: 12px;
}

@media (max-width: 1500px) {
  .rag-header {
    flex-direction: column;
  }

  .rag-kpis {
    grid-template-columns: 1fr;
  }
}
</style>
