<template>
  <div class="report-view">
    <div class="report-header">
      <el-button text @click="$router.back()">← 返回</el-button>
      <h2>AI 择校推荐报告</h2>
      <p class="report-meta">生成时间：{{ report?.createdAt }}</p>
    </div>

    <!-- 报告摘要 -->
    <div v-if="report?.summary" class="report-summary">
      <p>{{ report.summary }}</p>
    </div>

    <!-- 画像依据 -->
    <div v-if="report?.profileBasis" class="report-basis">
      <h3>推荐依据</h3>
      <!-- TODO: 展示画像依据字段 -->
    </div>

    <!-- 三档候选 -->
    <div v-for="tier in report?.tiers || []" :key="tier.level" class="report-tier">
      <h3>
        {{ tier.label }}
        <span class="tier-count">{{ tier.candidates?.length || 0 }} 所</span>
      </h3>
      <p v-if="tier.insufficient" class="insufficient-notice">{{ tier.insufficientReason }}</p>

      <DraftCandidateCard
        v-for="c in tier.candidates"
        :key="c.programId"
        :candidate="c"
        :show-remove="false"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getReport } from '@/api/recommend-v2'
import DraftCandidateCard from './DraftCandidateCard.vue'

const route = useRoute()
const report = ref(null)

onMounted(async () => {
  const id = route.params.id
  if (id) {
    try {
      const res = await getReport(id)
      report.value = res.data
    } catch (e) {
      console.error('Failed to load report', e)
    }
  }
})
</script>

<style scoped>
.report-view {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}
.report-header { margin-bottom: 24px; }
.report-meta { color: #999; font-size: 13px; }
.report-summary {
  background: #f0f9eb;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 24px;
}
.report-tier { margin-bottom: 24px; }
.tier-count { font-weight: normal; color: #999; font-size: 14px; }
.insufficient-notice { color: #e6a23c; font-size: 13px; }
</style>
