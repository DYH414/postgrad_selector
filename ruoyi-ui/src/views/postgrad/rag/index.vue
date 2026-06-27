<template>
  <div class="app-container rag-page">
    <div class="rag-shell">
      <div class="rag-header">
        <div>
          <div class="rag-eyebrow">Postgrad RAG</div>
          <h2>知识库观测台</h2>
          <p>查看索引、检索、重排和最近一次错误，快速确认 AI 推荐是否真的在用知识库。</p>
        </div>
        <div class="rag-actions">
          <el-button size="mini" icon="el-icon-refresh" @click="loadStatus">刷新</el-button>
          <el-button size="mini" type="primary" icon="el-icon-refresh-right" :loading="reindexing" @click="handleReindex">
            重新索引
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

      <div class="rag-grid">
        <section class="rag-panel">
          <div class="panel-heading">
            <div>
              <h3>当前状态</h3>
              <span>索引、启用、错误与最近运行模式</span>
            </div>
          </div>
          <el-descriptions :column="2" border size="small" class="rag-desc">
            <el-descriptions-item label="RAG 启用">{{ boolText(status.enabled) }}</el-descriptions-item>
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

        <section class="rag-panel">
          <div class="panel-heading">
            <div>
              <h3>最近一次查询</h3>
              <span>建议直接看 Micrometer / Actuator 指标与 LangChain4j 官方 listener</span>
            </div>
          </div>
          <div class="trace-hint">
            这层页面只保留索引健康检查；请求级观测请去看 `/actuator/metrics`、`/actuator/health` 和 LangChain4j listener 指标。
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script>
import { getRagStatus, reindexRag } from '@/api/postgrad/rag'

export default {
  name: 'PostgradRagMonitor',
  data() {
    return {
      loading: false,
      reindexing: false,
      status: {},
      notice: ''
    }
  },
  computed: {
    kpis() {
      return [
        { key: 'enabled', label: 'RAG 启用', value: this.boolText(this.status.enabled), hint: 'Embedding 模型是否就绪' },
        { key: 'indexed', label: '索引状态', value: this.boolText(this.status.indexed), hint: '文档是否已加载到内存向量库' },
        { key: 'docs', label: '文档数', value: this.status.documentCount || 0, hint: 'rag-documents/ 下文件数' },
        { key: 'chunks', label: '切片数', value: this.status.chunkCount || 0, hint: '按 chunk-size / overlap 计算' },
        { key: 'error', label: '最近错误', value: this.status.lastError ? '有' : '无', hint: '便于排查配置和索引问题' },
        { key: 'actuator', label: '观测入口', value: '/actuator', hint: '请求级观测走官方指标' }
      ]
    }
  },
  created() {
    this.loadStatus()
  },
  methods: {
    loadStatus() {
      this.loading = true
      this.notice = ''
      getRagStatus().then(res => {
        this.status = (res && res.data) || {}
      }).catch(() => {
        this.notice = 'RAG 状态请求失败，请检查后端接口是否启动。'
      }).finally(() => {
        this.loading = false
      })
    },
    handleReindex() {
      this.$confirm('确认重新索引 RAG 文档吗？这会重建内存向量库。', '重新索引').then(() => {
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
    boolText(value) {
      return value ? '是' : '否'
    },
    formatTime(value) {
      if (!value) return '-'
      return String(value)
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

.rag-desc {
  margin: 14px;
}

.error-box,
.trace-hint {
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
