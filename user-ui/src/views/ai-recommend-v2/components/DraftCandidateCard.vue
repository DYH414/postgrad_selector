<template>
  <div class="candidate-card" :class="cardStatusClass">
    <div class="card-header">
      <span class="school-name">{{ candidate.schoolName }}</span>
      <el-tag :type="tierTagType" size="small">{{ candidate.schoolTier }}</el-tag>
      <el-button
        type="text"
        size="small"
        class="remove-btn"
        @click="$emit('remove')"
      >
        移出
      </el-button>
    </div>

    <p class="program-name">{{ candidate.programName }}</p>

    <!-- 事实摘要 -->
    <div class="fact-row">
      <span>均分 {{ displayScore(candidate.avgAdmittedScore) }}</span>
      <span>差距 {{ displayGap(candidate.scoreGap) }}</span>
      <span>名额 {{ candidate.unifiedExamQuota || candidate.planCount || '-' }}</span>
      <span>{{ candidate.city }}</span>
    </div>

    <!-- AI 理由 -->
    <p v-if="candidate.reason" class="ai-reason">{{ candidate.reason }}</p>

    <!-- 风险 -->
    <div v-if="candidate.risks?.length" class="risks">
      <span v-for="r in candidate.risks" :key="r" class="risk-tag">⚠ {{ r }}</span>
    </div>

    <!-- 标签 -->
    <div v-if="candidate.tags?.length" class="tags">
      <el-tag v-for="t in candidate.tags" :key="t" size="small" type="info">{{ t }}</el-tag>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  candidate: { type: Object, required: true }
})

defineEmits(['remove', 'ask-about'])

const cardStatusClass = computed(() => ({
  'status-adjusted': props.candidate.adjusted,
  'status-verified-pending': props.candidate.status === 'verified_pending'
}))

const tierTagType = computed(() => {
  const tier = props.candidate.schoolTier || ''
  if (tier.includes('985')) return 'danger'
  if (tier.includes('211') || tier.includes('双一流')) return 'warning'
  return 'info'
})

function displayScore(val) {
  return val != null ? val : '-'
}

function displayGap(val) {
  if (val == null) return '-'
  return val >= 0 ? `+${val}` : `${val}`
}
</script>

<style scoped>
.candidate-card {
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
}
.candidate-card.status-adjusted { border-color: #e6a23c; background: #fdf6ec; }
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.school-name { font-weight: 600; }
.program-name { font-size: 13px; color: #666; margin: 4px 0; }
.fact-row {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #888;
  margin: 6px 0;
}
.ai-reason { font-size: 12px; color: #333; margin: 6px 0; }
.risks { margin: 4px 0; }
.risk-tag { font-size: 11px; color: #e6a23c; margin-right: 8px; }
.tags { margin-top: 4px; }
</style>
