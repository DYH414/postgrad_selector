<template>
  <div class="ai-history-page">
    <AppHeader current-page="history" />

    <div class="page-body">
      <div class="page-title">
        <h2>AI 推荐报告记录</h2>
        <el-button type="primary" size="small" icon="el-icon-plus" @click="$router.push('/recommend')">
          新建推荐
        </el-button>
      </div>

      <div v-loading="loading" class="report-list">
        <div v-if="!loading && reports.length === 0" class="empty-state">
          <i class="el-icon-document"></i>
          <p>暂无 AI 推荐报告</p>
          <el-button type="primary" @click="$router.push('/recommend')">去生成推荐</el-button>
        </div>

        <div v-for="report in reports" :key="report.id" class="report-card"
          @click="$router.push('/ai-report/' + report.id)">
          <div class="card-main">
            <div class="card-summary">{{ report.summary || 'AI 择校推荐报告' }}</div>
            <div class="card-meta">
              <span class="meta-item">
                <i class="el-icon-date"></i>
                {{ report.createdAt || '-' }}
              </span>
              <span v-if="report.tierSummary" class="meta-item tier-summary">
                {{ report.tierSummary }}
              </span>
            </div>
          </div>
          <div class="card-arrow">
            <i class="el-icon-arrow-right"></i>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { getAiReports } from '@/api/ai'

const reports = ref([])
const loading = ref(false)

function parseReport(raw) {
  const report = { id: raw.id, createdAt: raw.created_at || raw.createdAt || '' }
  try {
    let resultJson = raw.result_json || raw.resultJson || '{}'
    if (typeof resultJson === 'string') {
      resultJson = JSON.parse(resultJson)
    }
    report.summary = resultJson.summary || ''
    if (resultJson.tiers && Array.isArray(resultJson.tiers)) {
      const parts = resultJson.tiers.map(t => {
        const count = t.schools ? t.schools.length : 0
        return t.label + ' ' + count + '所'
      })
      report.tierSummary = parts.join(' · ')
    }
  } catch (e) {
    report.summary = 'AI 择校推荐报告'
  }
  return report
}

async function fetchReports() {
  loading.value = true
  try {
    const res = await getAiReports()
    const raw = res.data && res.data.reports ? res.data.reports : (res.data || [])
    reports.value = raw.map(r => parseReport(r))
  } catch (e) {
    ElMessage.error('加载报告列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchReports()
})
</script>

<style scoped>
.ai-history-page { min-height: 100vh; background: #f5f7fa; }

.page-body { max-width: 800px; margin: 0 auto; padding: 24px 16px; }

.page-title {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;
}
.page-title h2 { margin: 0; font-size: 20px; color: #303133; }

.empty-state { text-align: center; padding: 80px 0; color: #909399; }
.empty-state i { font-size: 48px; display: block; margin-bottom: 16px; }

.report-card {
  background: #fff; border-radius: 10px; padding: 20px 24px; margin-bottom: 12px;
  display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 1px 6px rgba(0,0,0,.06); cursor: pointer; transition: box-shadow .2s;
}
.report-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.1); }

.card-main { flex: 1; min-width: 0; }
.card-summary {
  font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 8px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.card-meta { display: flex; gap: 20px; flex-wrap: wrap; }
.meta-item { font-size: 13px; color: #909399; display: flex; align-items: center; gap: 4px; }
.tier-summary { color: #606266; }
.card-arrow { color: #c0c4cc; font-size: 18px; flex-shrink: 0; margin-left: 16px; }
</style>
