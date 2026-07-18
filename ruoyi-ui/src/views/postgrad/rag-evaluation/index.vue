<template>
  <div class="app-container eval-workbench">
    <div class="page-heading">
      <div><h2>RAG 评测工作台</h2><p>维护评测数据集、运行可重复实验并定位检索退化。</p></div>
      <el-button icon="el-icon-refresh" size="mini" @click="refreshAll">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="数据集" name="datasets">
        <div class="toolbar">
          <el-button v-hasPermi="['postgrad:ragEval:edit']" type="primary" size="mini" icon="el-icon-plus" @click="openDataset">新建开发集</el-button>
          <el-button size="mini" @click="handleImport('frozen-v1')">导入 frozen-v1</el-button>
          <el-button size="mini" @click="handleImport('safety-v1')">导入 safety-v1</el-button>
          <el-upload action="#" :auto-upload="false" :show-file-list="false" accept=".jsonl" :on-change="handleJsonlFile"><el-button size="mini" icon="el-icon-upload2">导入 JSONL</el-button></el-upload>
        </div>
        <el-table v-loading="loading.datasets" :data="datasetRows" stripe size="mini" @row-click="selectDataset">
          <el-table-column prop="name" label="数据集" min-width="160" />
          <el-table-column prop="version" label="版本" width="100" />
          <el-table-column prop="datasetType" label="类型" width="90" />
          <el-table-column prop="lifecycleState" label="状态" width="90"><template slot-scope="s"><el-tag size="mini" :type="s.row.lifecycleState === 'FROZEN' ? 'success' : 'warning'">{{ s.row.lifecycleState }}</el-tag></template></el-table-column>
          <el-table-column prop="caseCount" label="用例" width="70" />
          <el-table-column prop="checksumSha256" label="SHA-256" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="250"><template slot-scope="s">
            <el-button type="text" @click.stop="selectDataset(s.row)">查看</el-button>
            <el-button v-hasPermi="['postgrad:ragEval:export']" type="text" @click.stop="exportDatasetFile(s.row)">导出</el-button>
            <el-button v-if="s.row.lifecycleState !== 'FROZEN'" v-hasPermi="['postgrad:ragEval:edit']" type="text" @click.stop="openCase(null, s.row)">加用例</el-button>
            <el-button v-if="s.row.lifecycleState !== 'FROZEN'" v-hasPermi="['postgrad:ragEval:freeze']" type="text" @click.stop="handleFreeze(s.row)">冻结</el-button>
          </template></el-table-column>
        </el-table>

        <section v-if="selectedDataset" class="detail-band">
          <div class="section-heading"><h3>{{ selectedDataset.name }} / {{ selectedDataset.version }}</h3><span>{{ caseRows.length }} 条 Ground Truth</span></div>
          <el-table :data="caseRows" stripe size="mini">
            <el-table-column prop="caseKey" label="Case ID" width="150" />
            <el-table-column prop="queryText" label="查询" min-width="220" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column label="预期" min-width="180" show-overflow-tooltip><template slot-scope="s">{{ s.row.noAnswer ? 'No-answer' : evidenceSummary(s.row) }}</template></el-table-column>
            <el-table-column label="操作" width="120"><template slot-scope="s"><span v-if="selectedDataset.lifecycleState === 'FROZEN'">只读</span><template v-else><el-button type="text" @click="openCase(s.row)">编辑</el-button><el-button type="text" class="danger" @click="removeCase(s.row)">删除</el-button></template></template></el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="实验" name="runs">
        <div class="toolbar">
          <el-select v-model="runForm.datasetId" size="mini" placeholder="选择数据集" style="width:260px"><el-option v-for="d in datasetRows" :key="d.id" :label="`${d.name} / ${d.version}`" :value="d.id" /></el-select>
          <el-checkbox-group v-model="runForm.modes" class="inline-checks"><el-checkbox label="VECTOR_ONLY">Vector Only</el-checkbox><el-checkbox label="VECTOR_RERANK">Vector + Rerank</el-checkbox></el-checkbox-group>
          <el-button v-hasPermi="['postgrad:ragEval:run']" type="primary" size="mini" :loading="loading.start" @click="handleStartRun">运行评测</el-button>
        </div>
        <el-form :inline="true" :model="runQuery" class="run-query-form" size="mini">
          <el-form-item>
            <el-select v-model="runQuery.datasetId" clearable filterable placeholder="筛选数据集" style="width:220px" @change="handleRunQuery">
              <el-option v-for="d in datasetRows" :key="d.id" :label="`${d.name} / ${d.version}`" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="runQuery.status" clearable placeholder="筛选状态" style="width:130px" @change="handleRunQuery">
              <el-option label="待执行" value="PENDING" />
              <el-option label="运行中" value="RUNNING" />
              <el-option label="已完成" value="SUCCEEDED" />
              <el-option label="失败" value="FAILED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input v-model="runQuery.runIdKeyword" clearable placeholder="Run ID" style="width:180px" @keyup.enter.native="handleRunQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" @click="handleRunQuery">查询</el-button>
            <el-button icon="el-icon-refresh" @click="resetRunQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading.runs" :data="runRows" stripe size="mini">
          <el-table-column prop="runId" label="Run ID" width="135" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="105"><template slot-scope="s"><el-tag size="mini" :type="runTag(s.row.status)">{{ s.row.status }}</el-tag></template></el-table-column>
          <el-table-column label="进度" width="150"><template slot-scope="s"><el-progress :percentage="progress(s.row)" :stroke-width="8" /></template></el-table-column>
          <el-table-column prop="qdrantCollection" label="Collection" min-width="170" show-overflow-tooltip />
          <el-table-column prop="embeddingModel" label="Embedding" width="140" />
          <el-table-column prop="rerankModel" label="Rerank" width="180" />
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column prop="finishedAt" label="完成时间" width="170" />
          <el-table-column label="操作" width="190"><template slot-scope="s"><el-button type="text" @click="openRun(s.row)">结果</el-button><el-button v-if="s.row.status === 'RUNNING'" type="text" @click="handleCancel(s.row)">取消</el-button><el-dropdown v-if="s.row.status === 'SUCCEEDED'" @command="f => exportRun(s.row, f)"><span class="export-link">导出<i class="el-icon-arrow-down" /></span><el-dropdown-menu slot="dropdown"><el-dropdown-item command="markdown">Markdown</el-dropdown-item><el-dropdown-item command="json">JSON</el-dropdown-item></el-dropdown-menu></el-dropdown></template></el-table-column>
        </el-table>
        <pagination v-show="runTotal > 0" :total="runTotal" :page.sync="runQuery.pageNum" :limit.sync="runQuery.pageSize" @pagination="loadRuns" />
      </el-tab-pane>

      <el-tab-pane label="对比" name="compare">
        <div class="toolbar"><el-select v-model="compareForm.baselineRunId" size="mini" placeholder="基线 Run" style="width:280px"><el-option v-for="r in succeededRuns" :key="r.runId" :label="shortRun(r)" :value="r.runId" /></el-select><span>对比</span><el-select v-model="compareForm.candidateRunId" size="mini" placeholder="候选 Run" style="width:280px"><el-option v-for="r in succeededRuns" :key="r.runId" :label="shortRun(r)" :value="r.runId" /></el-select><el-button type="primary" size="mini" @click="handleCompare">开始对比</el-button></div>
        <el-alert v-if="comparison && !comparison.compatible" :title="comparison.warning" type="warning" :closable="false" show-icon />
        <div v-if="comparison" class="comparison-summary"><div><b>{{ comparison.improved }}</b><span>改善</span></div><div><b>{{ comparison.unchanged }}</b><span>持平</span></div><div><b>{{ comparison.regressed }}</b><span>退化</span></div><div v-if="comparison.compatible"><b>{{ formatDelta(comparison.latencyDeltaMs) }} ms</b><span>平均延迟变化</span></div></div>
        <el-table v-if="comparison && comparison.compatible" :data="metricDeltaRows" stripe size="mini" style="margin-bottom:14px"><el-table-column prop="metric" label="指标" /><el-table-column prop="delta" label="变化"><template slot-scope="s">{{ formatDelta(s.row.delta) }}</template></el-table-column></el-table>
        <el-table v-if="comparison" :data="comparison.caseChanges" stripe size="mini"><el-table-column prop="key" label="Case / Mode" min-width="220" /><el-table-column prop="baselineRank" label="基线排名" width="100" /><el-table-column prop="candidateRank" label="候选排名" width="100" /><el-table-column prop="change" label="变化" width="120"><template slot-scope="s"><el-tag size="mini" :type="s.row.change === 'IMPROVED' ? 'success' : s.row.change === 'REGRESSED' ? 'danger' : 'info'">{{ s.row.change }}</el-tag></template></el-table-column></el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="datasetForm.id ? '编辑数据集' : '新建开发集'" :visible.sync="datasetDialog" width="520px"><el-form :model="datasetForm" label-width="90px"><el-form-item label="标识"><el-input v-model="datasetForm.datasetKey" /></el-form-item><el-form-item label="名称"><el-input v-model="datasetForm.name" /></el-form-item><el-form-item label="版本"><el-input v-model="datasetForm.version" /></el-form-item><el-form-item label="类型"><el-select v-model="datasetForm.datasetType"><el-option label="DEV" value="DEV" /><el-option label="SAFETY" value="SAFETY" /></el-select></el-form-item><el-form-item label="说明"><el-input v-model="datasetForm.description" type="textarea" /></el-form-item></el-form><span slot="footer"><el-button @click="datasetDialog=false">取消</el-button><el-button type="primary" @click="submitDataset">保存</el-button></span></el-dialog>

    <el-dialog title="评测用例" :visible.sync="caseDialog" width="720px"><el-form :model="caseForm" label-width="110px"><el-form-item label="Case ID"><el-input v-model="caseForm.caseKey" /></el-form-item><el-form-item label="用户问题"><el-input v-model="caseForm.queryText" type="textarea" /></el-form-item><el-form-item label="分类/难度"><el-input v-model="caseForm.category" style="width:48%" /><el-select v-model="caseForm.difficulty" style="width:48%;margin-left:3%"><el-option label="easy" value="easy" /><el-option label="medium" value="medium" /><el-option label="hard" value="hard" /></el-select></el-form-item><el-form-item label="No-answer"><el-switch v-model="caseForm.noAnswer" /></el-form-item><el-form-item label="Metadata JSON"><el-input v-model="caseForm.expectedMetadataJson" type="textarea" :rows="3" /></el-form-item><el-form-item label="相关条件 JSON"><el-input v-model="caseForm.relevantConditionsJson" type="textarea" :rows="4" /></el-form-item><el-form-item label="相关来源 JSON"><el-input v-model="caseForm.relevantSourcesJson" type="textarea" :rows="2" /></el-form-item><el-form-item label="审核备注"><el-input v-model="caseForm.reviewerNote" type="textarea" /></el-form-item></el-form><span slot="footer"><el-button @click="caseDialog=false">取消</el-button><el-button type="primary" @click="submitCase">保存 Ground Truth</el-button></span></el-dialog>

    <el-drawer title="实验结果" :visible.sync="runDrawer" size="62%"><div class="drawer-body"><el-descriptions v-if="runDetail.run" :column="2" border size="small"><el-descriptions-item label="Run ID">{{ runDetail.run.runId }}</el-descriptions-item><el-descriptions-item label="状态">{{ runDetail.run.status }}</el-descriptions-item><el-descriptions-item label="参数">{{ runDetail.run.parameterSnapshotJson }}</el-descriptions-item><el-descriptions-item label="指标">{{ runDetail.run.aggregateMetricsJson }}</el-descriptions-item></el-descriptions><div class="toolbar" style="margin-top:16px"><el-select v-model="resultFilter" size="mini"><el-option label="全部用例" value="all" /><el-option label="仅失败" value="failed" /><el-option label="仅技术错误" value="technical" /></el-select></div><el-table :data="filteredResults" stripe size="mini"><el-table-column prop="caseId" label="Case" width="80" /><el-table-column prop="evalMode" label="模式" width="140" /><el-table-column prop="passed" label="通过" width="70"><template slot-scope="s">{{ s.row.passed ? '是' : '否' }}</template></el-table-column><el-table-column prop="firstRelevantRank" label="首个相关排名" width="110" /><el-table-column prop="failureReason" label="失败原因" min-width="140" /><el-table-column prop="latencyMs" label="耗时(ms)" width="90" /><el-table-column label="操作" width="130"><template slot-scope="s"><el-button type="text" @click="inspectResult(s.row)">诊断</el-button><el-button type="text" @click="review(s.row)">标注</el-button></template></el-table-column></el-table></div></el-drawer>
    <el-dialog title="用例诊断" :visible.sync="resultDialog" width="760px"><el-descriptions v-if="selectedResult" :column="1" border><el-descriptions-item label="预期 Metadata">{{ selectedResult.expectedMetadataJson }}</el-descriptions-item><el-descriptions-item label="相关条件">{{ selectedResult.relevantConditionsJson }}</el-descriptions-item><el-descriptions-item label="候选与排名"><pre>{{ prettyJson(selectedResult.candidateSummaryJson) }}</pre></el-descriptions-item><el-descriptions-item label="选中证据"><pre>{{ prettyJson(selectedResult.selectedEvidenceJson) }}</pre></el-descriptions-item><el-descriptions-item label="技术错误">{{ selectedResult.technicalErrorCode || '-' }} {{ selectedResult.technicalErrorSummary || '' }}</el-descriptions-item></el-descriptions></el-dialog>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import * as api from '@/api/postgrad/ragEvaluation'

const emptyCase = () => ({ id: null, datasetId: null, caseKey: '', queryText: '', category: 'fact', difficulty: 'medium', noAnswer: false, sourceDataAvailable: true, expectedFallback: false, expectedMetadataJson: '{}', relevantConditionsJson: '[]', relevantSourcesJson: '[]', tagsJson: '[]', reviewerNote: '' })
export default {
  name: 'RagEvaluationWorkbench',
  data() { return { activeTab: 'datasets', loading: { datasets: false, runs: false, start: false }, datasetRows: [], caseRows: [], runRows: [], runQuery: { pageNum: 1, pageSize: 10, datasetId: null, status: '', runIdKeyword: '' }, runTotal: 0, poller: null, selectedDataset: null, datasetDialog: false, caseDialog: false, runDrawer: false, resultDialog: false, resultFilter: 'all', selectedResult: null, datasetForm: {}, caseForm: emptyCase(), runForm: { datasetId: null, modes: ['VECTOR_ONLY', 'VECTOR_RERANK'] }, compareForm: { baselineRunId: '', candidateRunId: '' }, comparison: null, runDetail: {} } },
  computed: { succeededRuns() { return this.runRows.filter(r => r.status === 'SUCCEEDED') }, metricDeltaRows() { return Object.entries((this.comparison && this.comparison.metricDeltas) || {}).map(([metric, delta]) => ({ metric, delta })) }, filteredResults() { const rows = this.runDetail.results || []; return this.resultFilter === 'failed' ? rows.filter(r => !r.passed) : this.resultFilter === 'technical' ? rows.filter(r => r.technicalErrorCode) : rows } },
  async created() { await this.refreshAll(); await this.openTraceDraft() },
  beforeDestroy() { if (this.poller) clearInterval(this.poller) },
  methods: {
    async refreshAll() { await Promise.all([this.loadDatasets(), this.loadRuns()]) },
    async openTraceDraft() { const traceId = this.$route.query.traceId; if (!traceId) return; const target = this.datasetRows.find(d => d.lifecycleState !== 'FROZEN'); if (!target) return this.$message.warning('请先创建可编辑的 DEV 数据集'); const r = await api.getTraceDraft(traceId); this.caseForm = { ...emptyCase(), ...(r.data || {}), datasetId: target.id }; this.caseDialog = true },
    async loadDatasets() { this.loading.datasets = true; try { const r = await api.listDatasets({ pageNum: 1, pageSize: 100 }); this.datasetRows = r.rows || r.data || [] } finally { this.loading.datasets = false } },
    async loadRuns() { if (this.loading.runs) return; this.loading.runs = true; try { const r = await api.listRuns({ ...this.runQuery }); this.runRows = r.rows || []; this.runTotal = Number(r.total || 0); this.syncRunPolling() } finally { this.loading.runs = false } },
    handleRunQuery() { this.runQuery.pageNum = 1; this.loadRuns() },
    resetRunQuery() { this.runQuery = { pageNum: 1, pageSize: 10, datasetId: null, status: '', runIdKeyword: '' }; this.loadRuns() },
    syncRunPolling() { const active = this.runRows.some(row => ['PENDING', 'RUNNING'].includes(row.status)); if (active && !this.poller) this.poller = setInterval(() => { if (!this.loading.runs) this.loadRuns() }, 3000); if (!active && this.poller) { clearInterval(this.poller); this.poller = null } },
    async selectDataset(row) { const r = await api.getDataset(row.id); this.selectedDataset = r.data.dataset; this.caseRows = r.data.cases || [] },
    openDataset() { this.datasetForm = { datasetKey: '', name: '', version: 'dev-v1', datasetType: 'DEV', description: '' }; this.datasetDialog = true },
    async submitDataset() { await api.createDataset(this.datasetForm); this.datasetDialog = false; this.$message.success('数据集已创建'); this.loadDatasets() },
    openCase(row, dataset) { this.caseForm = row ? { ...row } : { ...emptyCase(), datasetId: (dataset || this.selectedDataset).id }; this.caseDialog = true },
    async submitCase() { JSON.parse(this.caseForm.expectedMetadataJson || '{}'); JSON.parse(this.caseForm.relevantConditionsJson || '[]'); JSON.parse(this.caseForm.relevantSourcesJson || '[]'); if (this.caseForm.id) await api.updateCase(this.caseForm.id, this.caseForm); else await api.saveCase(this.caseForm.datasetId, this.caseForm); this.caseDialog = false; this.$message.success('Ground Truth 已保存'); this.selectDataset(this.selectedDataset) },
    async removeCase(row) { await this.$confirm('删除该评测用例？'); await api.deleteCase(row.id); this.selectDataset(this.selectedDataset) },
    async handleFreeze(row) { const { value } = await this.$prompt('输入新的冻结版本号', '冻结数据集', { inputValue: `frozen-${Date.now()}` }); await api.freezeDataset(row.id, value); this.$message.success('冻结版本已发布'); this.loadDatasets() },
    async handleImport(name) { await api.importBundled(name); this.$message.success(`${name} 已导入`); this.loadDatasets() },
    async handleJsonlFile(file) { const { value } = await this.$prompt('输入数据集标识', '导入 JSONL', { inputValue: `dev-${Date.now()}` }); const data = new FormData(); data.append('datasetKey', value); data.append('name', value); data.append('version', 'v1'); data.append('datasetType', 'DEV'); data.append('file', file.raw); await api.importJsonl(data); this.$message.success('JSONL 已导入为开发集'); this.loadDatasets() },
    async exportDatasetFile(row) { const blob = await api.exportDataset(row.id); saveAs(blob, `${row.datasetKey}-${row.version}.jsonl`) },
    async handleStartRun() { if (!this.runForm.datasetId) return this.$message.warning('请选择数据集'); this.loading.start = true; try { const r = await api.startRun({ ...this.runForm, parameters: {} }); this.$message.success(`评测已提交：${r.data.runId}`); this.activeTab = 'runs'; this.runQuery.pageNum = 1; await this.loadRuns(); this.syncRunPolling() } finally { this.loading.start = false } },
    async openRun(row) { const r = await api.getRun(row.runId); this.runDetail = r.data || {}; this.runDrawer = true },
    async handleCancel(row) { await api.cancelRun(row.runId); this.loadRuns() },
    async review(row) { const { value } = await this.$prompt('填写失败分类，如 WRONG_RANK / NO_RECALL', '人工诊断'); await api.reviewResult(row.id, { category: value, note: '' }); this.openRun(this.runDetail.run) },
    inspectResult(row) { const expected = (this.runDetail.cases || []).find(c => c.id === row.caseId) || {}; this.selectedResult = { ...row, ...expected }; this.resultDialog = true },
    prettyJson(value) { try { return JSON.stringify(JSON.parse(value || '[]'), null, 2) } catch (_) { return value || '-' } },
    async handleCompare() { const r = await api.compareRuns(this.compareForm); this.comparison = r.data },
    async exportRun(row, format) { const blob = await api.downloadReport(row.runId, format); saveAs(blob, `rag-eval-${row.runId}.${format === 'json' ? 'json' : 'md'}`) },
    evidenceSummary(row) { return row.relevantConditionsJson !== '[]' ? row.relevantConditionsJson : row.relevantSourcesJson },
    runTag(status) { return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'RUNNING' ? 'warning' : 'info' },
    progress(row) { return row.totalCases ? Math.round((row.completedCases || 0) * 100 / row.totalCases) : 0 },
    formatDelta(value) { const n = Number(value || 0); return `${n > 0 ? '+' : ''}${n.toFixed(4)}` },
    shortRun(row) { return `${row.runId.slice(0, 8)} · ${row.status} · ${row.qdrantCollection || '-'}` }
  }
}
</script>

<style scoped>
.eval-workbench { background:#f5f7fa; min-height:calc(100vh - 84px); }
.page-heading,.section-heading,.toolbar { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.page-heading { margin-bottom:14px; }.page-heading h2,.section-heading h3 { margin:0 0 4px; }.page-heading p,.section-heading span { margin:0; color:#6b7280; }
.toolbar { justify-content:flex-start; margin-bottom:14px; flex-wrap:wrap; }.inline-checks { display:inline-flex; margin:0 8px; }.run-query-form { margin-bottom:14px; }
.detail-band { margin-top:18px; padding-top:16px; border-top:1px solid #e5e7eb; }.section-heading { margin-bottom:10px; }
.danger { color:#f56c6c; }.export-link { margin-left:10px; color:#409eff; cursor:pointer; }.drawer-body { padding:0 20px 24px; }
.comparison-summary { display:grid; grid-template-columns:repeat(3, minmax(120px, 180px)); gap:12px; margin:16px 0; }
.comparison-summary div { border:1px solid #dcdfe6; padding:14px; background:#fff; }.comparison-summary b { display:block; font-size:24px; }.comparison-summary span { color:#6b7280; }
</style>
