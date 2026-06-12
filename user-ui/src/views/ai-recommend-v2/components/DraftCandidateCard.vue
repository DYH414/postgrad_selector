<template>
  <div class="candidate-card" :class="{ 'is-adjusted': candidate.adjusted }">
    <div class="card-top">
      <span class="school-name">{{ candidate.fact.schoolName }}</span>
      <el-tag :type="tierTagType" size="small" effect="plain">{{ candidate.fact.schoolTier }}</el-tag>
      <button class="remove-btn" title="移出草稿" @click="$emit('remove')">
        <svg viewBox="0 0 16 16" width="14" height="14">
          <line x1="4" y1="4" x2="12" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="12" y1="4" x2="4" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <p class="program-name">{{ candidate.fact.collegeName }} · {{ candidate.fact.programName }}</p>

    <!-- 数据行 -->
    <div class="fact-grid">
      <span class="fact-item">
        <em>均分</em> {{ displayVal(candidate.fact.avgAdmittedScore) }}
      </span>
      <span class="fact-item">
        <em>差距</em> {{ displayGap(candidate.fact.scoreGap) }}
      </span>
      <span class="fact-item">
        <em>名额</em> {{ candidate.fact.unifiedExamQuota || candidate.fact.planCount || '-' }}
      </span>
      <span class="fact-item">{{ candidate.fact.city }}</span>
    </div>

    <!-- AI 理由 -->
    <p v-if="candidate.opinion?.reason" class="ai-reason">{{ candidate.opinion.reason }}</p>

    <!-- 风险标签 -->
    <div v-if="candidate.opinion?.risks?.length" class="risk-tags">
      <span v-for="r in candidate.opinion.risks" :key="r" class="risk-tag">⚠ {{ r }}</span>
    </div>

    <!-- 降级提示 -->
    <div v-if="candidate.adjusted" class="adjust-notice">
      <i class="el-icon-warning"></i>
      {{ candidate.adjustReason || '系统已自动调整档位' }}
    </div>

    <!-- 底部操作 -->
    <div class="card-actions">
      <el-button type="text" size="small" @click="$emit('ask-about')">
        问 AI
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  candidate: { type: Object, required: true }
})

defineEmits(['remove', 'ask-about'])

const tierTagType = computed(() => {
  const tier = props.candidate.fact.schoolTier || ''
  if (tier.includes('985')) return 'danger'
  if (tier.includes('211') || tier.includes('双一流')) return 'warning'
  return 'info'
})

function displayVal(v) { return v != null ? v : '-' }
function displayGap(v) {
  if (v == null) return '-'
  return v >= 0 ? `+${v}` : `${v}`
}
</script>

<style scoped>
.candidate-card {
  padding: 12px;
  border: 1px solid #eef2f7;
  border-radius: 6px;
  background: #fafbfd;
  margin-bottom: 8px;
  transition: border-color .2s;
}
.candidate-card:hover { border-color: #d0dae8; }
.candidate-card.is-adjusted { border-color: #f5dab1; background: #fef8ef; }
.card-top { display: flex; align-items: center; gap: 6px; }
.school-name { font-weight: 600; font-size: 14px; color: #303133; }
.remove-btn {
  margin-left: auto;
  background: none;
  border: none;
  color: #a8b2c1;
  cursor: pointer;
  padding: 2px;
  border-radius: 4px;
  transition: color .2s, background .2s;
}
.remove-btn:hover { color: #f56c6c; background: rgba(245,108,108,.08); }
.program-name { font-size: 12px; color: #71829a; margin: 4px 0 6px; }

/* 数据网格 */
.fact-grid { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 6px; }
.fact-item { font-size: 12px; color: #303133; }
.fact-item em { font-style: normal; color: #a8b2c1; margin-right: 2px; }

/* AI 理由 */
.ai-reason { font-size: 12px; color: #555; line-height: 18px; margin: 6px 0; }

/* 风险 */
.risk-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 4px; }
.risk-tag { font-size: 11px; color: #e6a23c; }

/* 降级 */
.adjust-notice {
  margin-top: 6px;
  padding: 6px 10px;
  border-radius: 4px;
  background: rgba(230,162,60,.08);
  font-size: 12px;
  color: #b88230;
}
.card-actions { margin-top: 6px; }
</style>
