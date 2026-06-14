<template>
  <div class="report-page">
    <AppHeader current-page="ai" />

    <main class="report-shell">
      <div class="report-toolbar">
        <el-button text class="back-btn" @click="goBack">← 返回工作台</el-button>
      </div>

      <section v-if="loading" class="report-state">
        <i class="el-icon-loading"></i>
        <span>报告加载中...</span>
      </section>

      <section v-else-if="error" class="report-state error">
        <strong>报告加载失败</strong>
        <p>请返回工作台后重试。</p>
        <el-button type="primary" @click="goBack">返回工作台</el-button>
      </section>

      <section v-else-if="!report" class="report-state">
        <strong>报告暂不可用</strong>
        <p>请返回工作台重新生成报告。</p>
        <el-button type="primary" @click="goBack">返回工作台</el-button>
      </section>

      <ReportDocument v-else :report="report" />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getReport } from '@/api/recommend-v2'
import AppHeader from '@/components/AppHeader.vue'
import ReportDocument from './report/ReportDocument.vue'

const route = useRoute()
const router = useRouter()
const report = ref(null)
const loading = ref(false)
const error = ref(false)

function goBack() {
  router.push('/ai-recommend')
}

onMounted(async () => {
  window.scrollTo({ top: 0, left: 0, behavior: 'auto' })

  const id = route.params.id
  if (!id) {
    error.value = true
    return
  }

  loading.value = true
  error.value = false
  try {
    const res = await getReport(id)
    report.value = res.data || null
  } catch (e) {
    console.error('Failed to load report', e)
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.report-page {
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f6f9fe 0%, #eef4fb 100%);
  color: #10213f;
}

.report-shell {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 24px 56px;
}

.report-toolbar {
  margin-bottom: 14px;
}

.back-btn {
  color: #425b7c;
  font-weight: 700;
}

.report-state {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(39, 86, 166, .08);
  color: #607592;
}

.report-state strong {
  color: #10213f;
  font-size: 18px;
}

.report-state p {
  margin: 0;
}

.report-state.error {
  color: #b55c00;
}

@media (max-width: 768px) {
  .report-shell {
    padding: 16px 12px 36px;
  }
}
</style>
