<template>
  <div class="prototype-page">
    <AppHeader current-page="ai" />

    <main class="report-wrap">
      <div class="report-top">
        <el-button text @click="$router.back()">← 返回工作台</el-button>
        <h2>AI 择校推荐报告</h2>
        <p class="report-meta" v-if="report?.createdAt">生成时间：{{ report.createdAt }}</p>
      </div>

      <!-- 摘要 -->
      <section v-if="report?.summary" class="report-section summary">
        <p>{{ report.summary }}</p>
      </section>

      <!-- 画像依据 -->
      <section v-if="report?.profileBasis" class="report-section basis">
        <h3>推荐依据</h3>
        <div class="basis-grid">
          <div class="basis-item" v-if="report.profileBasis.estimatedScore">
            <span>预估分数</span>
            <strong>{{ report.profileBasis.estimatedScore }}</strong>
          </div>
          <div class="basis-item" v-if="report.profileBasis.targetRegions">
            <span>目标地区</span>
            <strong>{{ report.profileBasis.targetRegions }}</strong>
          </div>
          <div class="basis-item" v-if="report.profileBasis.undergradTier">
            <span>本科层次</span>
            <strong>{{ report.profileBasis.undergradTier }}</strong>
          </div>
          <div class="basis-item" v-if="report.profileBasis.riskPreference">
            <span>风险偏好</span>
            <strong>{{ report.profileBasis.riskPreference }}</strong>
          </div>
        </div>
      </section>

      <!-- 三档 -->
      <section v-for="tier in (report?.tiers || [])" :key="tier.level" class="report-section tier-section">
        <h3>
          {{ tier.label }}
          <span class="tier-count-badge">{{ tier.candidates?.length || 0 }} 所</span>
        </h3>
        <p v-if="tier.insufficient" class="insufficient-notice">{{ tier.insufficientReason }}</p>

        <DraftCandidateCard
          v-for="c in tier.candidates"
          :key="c.fact.programId"
          :candidate="c"
          :show-actions="false"
        />
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getReport } from '@/api/recommend-v2'
import AppHeader from '@/components/AppHeader.vue'
import DraftCandidateCard from './DraftCandidateCard.vue'

const route = useRoute()
const report = ref(null)

onMounted(async () => {
  const id = route.params.id
  if (!id) return
  try {
    const res = await getReport(id)
    report.value = res.data
  } catch (e) {
    console.error('Failed to load report', e)
  }
})
</script>

<style scoped>
.prototype-page { min-height: 100vh; background: #f4f7fc; }
.report-wrap { max-width: 780px; margin: 0 auto; padding: 24px 16px 60px; }
.report-top { margin-bottom: 24px; }
.report-top h2 { margin: 8px 0 4px; font-size: 22px; }
.report-meta { margin: 0; color: #71829a; font-size: 13px; }

.report-section {
  margin-bottom: 24px;
  padding: 20px;
  border: 1px solid rgba(215,227,245,.9);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(42,84,153,.08);
}
.report-section h3 { margin: 0 0 12px; font-size: 16px; }

.summary p { margin: 0; font-size: 14px; line-height: 22px; color: #303133; }

.basis-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.basis-item { padding: 8px 12px; border: 1px solid #f0f4fa; border-radius: 6px; }
.basis-item span { display: block; font-size: 12px; color: #71829a; }
.basis-item strong { display: block; font-size: 14px; color: #303133; margin-top: 2px; }

.tier-count-badge { font-weight: 400; font-size: 13px; color: #71829a; margin-left: 8px; }
.insufficient-notice { color: #e6a23c; font-size: 12px; margin-bottom: 12px; }
</style>
