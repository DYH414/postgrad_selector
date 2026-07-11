<template>
  <div class="app-container rag-page">
    <div class="rag-shell">
      <div class="rag-header">
        <div>
          <div class="rag-eyebrow">Postgrad RAG</div>
          <h2>知识库观测台</h2>
          <p>查看索引、检索测试、最近 Trace 和配置参数，快速确认 AI 是否在用知识库。</p>
        </div>
        <div class="rag-actions">
          <el-button size="mini" icon="el-icon-refresh" @click="loadStatus">刷新</el-button>
          <el-button
            v-if="hasReindexPerm"
            size="mini"
            type="primary"
            icon="el-icon-refresh-right"
            :loading="reindexing"
            @click="handleReindex"
          >
            重建通用知识库
          </el-button>
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

      <section class="rag-panel database-ops-panel">
        <div class="panel-heading">
          <div>
            <h3>数据库 RAG 管道</h3>
            <span>从 MySQL 生成 rag-generated 文档，再索引为推荐证据</span>
          </div>
        </div>
        <div class="database-ops-body">
          <el-form :inline="true" :model="databaseForm" size="mini" class="database-form">
            <el-form-item label="起始年份">
              <el-input-number v-model="databaseForm.startYear" :min="2000" :max="2100" controls-position="right" />
            </el-form-item>
            <el-form-item label="结束年份">
              <el-input-number v-model="databaseForm.endYear" :min="2000" :max="2100" controls-position="right" />
            </el-form-item>
            <el-form-item label="上限">
              <el-input-number v-model="databaseForm.limit" :min="1" :max="10000" controls-position="right" />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                icon="el-icon-document-add"
                :loading="generatingDocuments"
                :disabled="!hasReindexPerm"
                @click="handleGenerateDatabaseDocuments"
              >
                生成数据库文档
              </el-button>
              <el-button
                type="success"
                icon="el-icon-connection"
                :loading="reindexingDatabase"
                :disabled="!hasReindexPerm || generatingAndReindexing"
                @click="handleReindexDatabase"
              >
                索引数据库 RAG
              </el-button>
              <el-button
                type="warning"
                icon="el-icon-finished"
                :loading="generatingAndReindexing"
                :disabled="!hasReindexPerm || generatingDocuments || reindexingDatabase"
                @click="handleGenerateAndReindexDatabase"
              >
                生成并索引数据库 RAG
              </el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="databaseNeedsIndexing"
            title="数据库 RAG 文档已生成，但 Qdrant 尚未更新。请继续点击“索引数据库 RAG”，或使用“生成并索引数据库 RAG”完成闭环。"
            type="warning"
            show-icon
            :closable="false"
            class="database-stage-alert"
          />
          <div v-if="databaseResult" class="database-result">
            <span v-if="databaseOperationStage">{{ databaseOperationStage }}</span>
            <span>{{ databaseResult.mode || 'database' }}</span>
            <span>文档 {{ databaseResult.documentCount || 0 }}</span>
            <span v-if="databaseResult.chunkCount != null">切片 {{ databaseResult.chunkCount }}</span>
            <span v-if="databaseResult.writtenFileCount != null">写入 {{ databaseResult.writtenFileCount }} 个文件</span>
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
            <el-descriptions-item label="Embedding 模型">{{ status.embeddingModel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Rerank 模型">{{ status.rerankModel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分块大小">{{ status.chunkSize || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分块重叠">{{ status.chunkOverlap || '-' }}</el-descriptions-item>
            <el-descriptions-item label="召回上限">{{ status.maxResults || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Rerank 截断">{{ status.rerankTopN || '-' }}</el-descriptions-item>
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
              <span>不经过 LLM，直接测试 Embedding → Qdrant → Rerank 管道</span>
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
  reindexRag,
  generateDatabaseDocuments,
  reindexDatabaseRag,
  searchTestRag,
  getRagTraces
} from '@/api/postgrad/rag'

export default {
  name: 'PostgradRagMonitor',
  data() {
    return {
      loading: false,
      reindexing: false,
      generatingDocuments: false,
      reindexingDatabase: false,
      generatingAndReindexing: false,
      databaseOperationStage: '',
      databaseNeedsIndexing: false,
      status: {},
      notice: '',
      databaseForm: {
        startYear: 2023,
        endYear: 2025,
        limit: 1000
      },
      databaseResult: null,
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
      traces: []
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
        { key: 'docs', label: '文档数', value: this.status.documentCount || 0, hint: 'rag-generated 或 rag-documents 文档数' },
        { key: 'chunks', label: '切片数', value: this.status.chunkCount || 0, hint: '分块后的总片段数' },
        { key: 'embedding', label: 'Embedding', value: this.status.embeddingModel || '-', hint: '向量化模型' }
      ]
    }
  },
  created() {
    this.loadStatus()
    this.loadTraces()
  },
  methods: {
    async loadStatus() {
      this.loading = true
      this.notice = ''
      try {
        const res = await getRagStatus()
        this.status = (res && res.data) || {}
      } catch (e) {
        this.notice = 'RAG 状态请求失败，请检查后端接口是否启动。'
      } finally {
        this.loading = false
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
    handleReindex() {
      this.$confirm('确认重新索引通用 rag-documents 文档吗？这会重建 Qdrant 向量库。', '重建通用知识库').then(() => {
        this.reindexing = true
        reindexRag().then(res => {
          this.$message.success((res && res.msg) || '重新索引完成')
          this.loadStatus()
        }).catch(() => {
          this.$message.error('重新索引失败')
        }).finally(() => {
          this.reindexing = false
        })
      }).catch(() => {})
    },
    handleGenerateDatabaseDocuments() {
      this.generatingDocuments = true
      this.databaseOperationStage = '生成文档中'
      generateDatabaseDocuments(this.databaseForm).then(res => {
        this.databaseResult = (res && res.data) || {}
        this.databaseOperationStage = '文档已生成，待索引'
        this.databaseNeedsIndexing = true
        this.$message.warning('数据库 RAG 文档生成完成，Qdrant 尚未更新，请继续索引数据库 RAG')
        this.loadTraces()
      }).catch(() => {
        this.databaseOperationStage = '文档生成失败'
        this.$message.error('数据库 RAG 文档生成失败')
      }).finally(() => {
        this.generatingDocuments = false
      })
    },
    handleReindexDatabase() {
      this.$confirm('确认索引数据库生成的 RAG 证据吗？这会重建 Qdrant 中的推荐证据。', '索引数据库 RAG').then(() => {
        this.reindexingDatabase = true
        reindexDatabaseRag(this.databaseForm).then(res => {
          this.databaseResult = (res && res.data) || {}
          this.databaseOperationStage = '索引完成'
          this.databaseNeedsIndexing = false
          this.$message.success('数据库 RAG 索引完成')
          this.loadStatus()
          this.loadTraces()
        }).catch(() => {
          this.$message.error('数据库 RAG 索引失败')
        }).finally(() => {
          this.reindexingDatabase = false
        })
      }).catch(() => {})
    },
    async handleGenerateAndReindexDatabase() {
      this.$confirm('确认生成并索引数据库 RAG 吗？这会先写入 rag-generated 文档，再重建 Qdrant 推荐证据。', '生成并索引数据库 RAG').then(async () => {
        this.generatingAndReindexing = true
        this.databaseNeedsIndexing = false
        this.databaseOperationStage = '生成文档中'
        try {
          const generateRes = await generateDatabaseDocuments(this.databaseForm)
          this.databaseResult = (generateRes && generateRes.data) || {}
          this.databaseOperationStage = '文档已生成，正在索引'
          this.databaseNeedsIndexing = true
          await this.loadTraces()
          const reindexRes = await reindexDatabaseRag(this.databaseForm)
          this.databaseResult = (reindexRes && reindexRes.data) || this.databaseResult
          this.databaseOperationStage = '生成并索引完成'
          this.databaseNeedsIndexing = false
          this.$message.success('数据库 RAG 文档生成并索引完成')
          await this.loadStatus()
          await this.loadTraces()
        } catch (e) {
          this.databaseOperationStage = this.databaseNeedsIndexing ? '索引失败，文档已保留' : '生成并索引失败'
          this.$message.error(this.databaseNeedsIndexing ? '文档已生成，但数据库 RAG 索引失败' : '数据库 RAG 生成并索引失败')
          this.loadStatus()
          this.loadTraces()
        } finally {
          this.generatingAndReindexing = false
        }
      }).catch(() => {})
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
        // refresh traces to include this test
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

.database-ops-panel {
  min-height: auto;
  margin-bottom: 12px;
}

.database-ops-body {
  padding: 14px 16px;
}

.database-form {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
}

.database-result,
.active-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  color: #64748b;
  font-size: 12px;
}

.database-stage-alert {
  margin-top: 10px;
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

@media (max-width: 1500px) {
  .rag-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .rag-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .rag-header {
    flex-direction: column;
  }

  .rag-kpis {
    grid-template-columns: 1fr;
  }
}
</style>
