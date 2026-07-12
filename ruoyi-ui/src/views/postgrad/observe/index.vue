<template>
  <div class="app-container ai-observe-page">
    <div class="observe-shell">
      <div class="observe-header">
        <div>
          <div class="observe-eyebrow">Postgrad AI</div>
          <h2>AI 观测台</h2>
          <p>查看官方指标聚合后的 AI 调用情况，确认推荐和对话是否真的在工作。</p>
        </div>
        <div class="observe-actions">
          <el-button size="mini" icon="el-icon-refresh" :loading="loading" @click="loadOverview">刷新</el-button>
          <el-button size="mini" @click="$router.push('/postgrad/rag')">查看 RAG 观测台</el-button>
        </div>
      </div>

      <div class="observe-kpis">
        <div v-for="item in kpis" :key="item.key" class="observe-kpi">
          <div class="observe-kpi__label">{{ item.label }}</div>
          <div class="observe-kpi__value">{{ item.value }}</div>
          <div class="observe-kpi__hint">{{ item.hint }}</div>
        </div>
      </div>

      <div class="observe-grid">
        <section class="observe-panel">
          <div class="panel-heading">
            <h3>按模型分组</h3>
            <span>基于官方 metrics 标签聚合</span>
          </div>
          <div class="group-list">
            <div v-for="item in overview.modelGroups || []" :key="item.key" class="group-item">
              <span>{{ item.key }}</span>
              <strong>{{ item.calls }}</strong>
            </div>
            <div v-if="!overview.modelGroups || !overview.modelGroups.length" class="empty-state">暂无数据</div>
          </div>
        </section>

        <section class="observe-panel">
          <div class="panel-heading">
            <h3>按入口分组</h3>
            <span>推荐、聊天等业务入口</span>
          </div>
          <div class="group-list">
            <div v-for="item in overview.operationGroups || []" :key="item.key" class="group-item">
              <span>{{ item.key }}</span>
              <strong>{{ item.calls }}</strong>
            </div>
            <div v-if="!overview.operationGroups || !overview.operationGroups.length" class="empty-state">暂无数据</div>
          </div>
        </section>
      </div>

      <div class="observe-grid">
        <section class="observe-panel">
          <div class="panel-heading">
            <h3>最近一次调用</h3>
            <span>最新调用的运行快照</span>
          </div>
          <el-descriptions :column="2" border size="small" class="observe-desc">
            <el-descriptions-item label="入口">{{ latest.operationKey || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Run ID">{{ latest.runId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结果">{{ latest.outcome || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Provider">{{ latest.provider || '-' }}</el-descriptions-item>
            <el-descriptions-item label="模型">{{ latest.model || '-' }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ latest.durationMs != null ? latest.durationMs + ' ms' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ latest.occurredAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="输入 token">{{ latest.inputTokens != null ? latest.inputTokens : '-' }}</el-descriptions-item>
            <el-descriptions-item label="输出 token">{{ latest.outputTokens != null ? latest.outputTokens : '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="observe-panel">
          <div class="panel-heading">
            <h3>最近错误</h3>
            <span>如果有失败，这里只展示摘要</span>
          </div>
          <div class="error-box" :class="{ 'is-empty': !latest.errorSummary }">
            <strong>{{ latest.errorSummary ? '失败摘要' : '暂无错误' }}</strong>
            <p>{{ latest.errorSummary || '当前没有记录到错误。' }}</p>
          </div>
          <div class="rag-quick-card">
            <strong>RAG 快照</strong>
            <p>文档数：{{ rag.documentCount || 0 }}，切片数：{{ rag.chunkCount || 0 }}，已索引：{{ rag.indexed ? '是' : '否' }}</p>
            <p>最近索引：{{ rag.lastIndexedAt || '-' }}</p>
            <p>最近错误：{{ rag.lastError || '暂无' }}</p>
          </div>
        </section>
      </div>

      <section class="tool-trace-panel">
        <div class="panel-heading"><h3>Agent Tool 调用</h3><span>仅展示摘要；点击 Run ID 查看同次运行的完整调用链</span></div>
        <el-table :data="toolTraces" size="mini" v-loading="traceLoading">
          <el-table-column prop="startedAt" label="开始时间" min-width="150" />
          <el-table-column label="Run ID" min-width="150"><template slot-scope="scope"><el-button type="text" size="mini" @click="openRun(scope.row.runId)">{{ scope.row.runId }}</el-button></template></el-table-column>
          <el-table-column prop="toolName" label="Tool" min-width="150" />
          <el-table-column prop="toolCategory" label="类别" width="80" />
          <el-table-column prop="status" label="状态" min-width="130" />
          <el-table-column prop="resultCode" label="结果码" min-width="160" />
          <el-table-column prop="durationMs" label="耗时" width="90"><template slot-scope="scope">{{ scope.row.durationMs == null ? '-' : scope.row.durationMs + ' ms' }}</template></el-table-column>
          <el-table-column prop="budgetGroup" label="预算组" width="100" />
        </el-table>
      </section>

      <el-drawer title="Run Tool 调用详情" :visible.sync="runDrawerVisible" size="52%">
        <div class="run-trace-drawer"><el-table :data="runToolTraces" size="mini">
          <el-table-column prop="toolName" label="Tool" min-width="140" />
          <el-table-column prop="status" label="状态" min-width="120" />
          <el-table-column prop="resultCode" label="结果码" min-width="150" />
          <el-table-column prop="resultSummary" label="结果摘要" min-width="240" show-overflow-tooltip />
          <el-table-column prop="durationMs" label="耗时" width="90" />
        </el-table></div>
      </el-drawer>
    </div>
  </div>
</template>

<script>
import { getAiObserveOverview, getRecentToolTraces, getRunToolTraces } from '@/api/postgrad/aiObserve'

export default {
  name: 'PostgradAiObserve',
  data() {
    return {
      loading: false,
      traceLoading: false,
      overview: {},
      toolTraces: [],
      runToolTraces: [],
      runDrawerVisible: false,
      notice: ''
    }
  },
  computed: {
    latest() {
      return this.overview.latest || {}
    },
    rag() {
      return this.overview.rag || {}
    },
    kpis() {
      return [
        { key: 'calls', label: '总调用数', value: this.overview.totalCalls || 0, hint: '官方调用计数' },
        { key: 'succ', label: '成功率', value: `${(this.overview.successRate || 0).toFixed(1)}%`, hint: '成功/总调用' },
        { key: 'dur', label: '平均耗时', value: `${(this.overview.averageDurationMs || 0).toFixed(0)} ms`, hint: '按官方 duration 聚合' },
        { key: 'in', label: '输入 token', value: this.overview.inputTokens || 0, hint: '所有输入 token 汇总' },
        { key: 'out', label: '输出 token', value: this.overview.outputTokens || 0, hint: '所有输出 token 汇总' },
        { key: 'rag', label: 'RAG 状态', value: this.rag.indexed ? '已索引' : '未索引', hint: '知识库健康快照' }
      ]
    }
  },
  created() {
    this.loadOverview()
    this.loadToolTraces()
  },
  methods: {
    loadOverview() {
      this.loading = true
      this.notice = ''
      getAiObserveOverview().then(res => {
        this.overview = (res && res.data) || {}
      }).catch(() => {
        this.notice = 'AI 观测请求失败，请检查后端接口是否启动。'
      }).finally(() => {
        this.loading = false
      })
    },
    loadToolTraces() {
      this.traceLoading = true
      getRecentToolTraces().then(res => {
        this.toolTraces = (res && res.data) || []
      }).finally(() => {
        this.traceLoading = false
      })
    },
    openRun(runId) {
      if (!runId) return
      getRunToolTraces(runId).then(res => {
        this.runToolTraces = (res && res.data) || []
        this.runDrawerVisible = true
      })
    }
  }
}
</script>

<style scoped>
.ai-observe-page {
  min-height: calc(100vh - 84px);
  background: linear-gradient(180deg, #0f172a 0%, #111827 40%, #e5eefb 100%);
}

.observe-shell {
  color: #0f172a;
}

.observe-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  color: #e2e8f0;
}

.observe-eyebrow {
  margin-bottom: 6px;
  color: #93c5fd;
  font-size: 12px;
  font-weight: 700;
}

.observe-header h2 {
  margin: 0;
  font-size: 24px;
}

.observe-header p {
  margin: 8px 0 0;
  color: #cbd5e1;
  font-size: 13px;
}

.observe-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.observe-kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.observe-kpi,
.observe-panel {
  border: 1px solid #dbe5f2;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.tool-trace-panel {
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  margin-bottom: 12px;
}

.run-trace-drawer {
  padding: 0 16px;
}

.observe-kpi {
  padding: 12px;
  min-height: 90px;
}

.observe-kpi__label {
  color: #64748b;
  font-size: 12px;
}

.observe-kpi__value {
  margin-top: 6px;
  color: #0f172a;
  font-size: 24px;
  font-weight: 700;
}

.observe-kpi__hint {
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.observe-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.observe-panel {
  min-height: 260px;
  overflow: hidden;
}

.panel-heading {
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
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

.group-list {
  padding: 14px 16px;
}

.group-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #edf2f7;
}

.empty-state,
.error-box,
.rag-quick-card {
  margin: 0 14px 14px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}

.error-box strong,
.rag-quick-card strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
}

@media (max-width: 1500px) {
  .observe-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .observe-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .observe-header {
    flex-direction: column;
  }

  .observe-kpis {
    grid-template-columns: 1fr;
  }
}
</style>
