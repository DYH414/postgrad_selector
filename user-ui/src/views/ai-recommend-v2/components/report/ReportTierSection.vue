<template>
  <section class="tier-section">
    <div class="tier-title-row">
      <div>
        <p class="chapter-index">{{ chapterIndex }}</p>
        <h2>{{ tierLabel }}</h2>
        <span>{{ strategyText }}</span>
      </div>
      <div class="tier-count">
        <strong>{{ candidates.length }}</strong>
        <span>/ {{ targetCount }} 所</span>
      </div>
    </div>

    <p v-if="tier?.insufficient" class="insufficient-notice">
      {{ tier.insufficientReason || '该档位候选不足，报告已保留该风险。' }}
    </p>

    <div v-if="candidates.length" class="candidate-list">
      <ReportCandidateCard
        v-for="(candidate, candidateIndex) in candidates"
        :key="candidate?.fact?.programId || `${tier?.level || index}-${candidateIndex}`"
        :candidate="candidate"
        :tier-level="tier?.level"
        :tier-label="tierLabel"
        :index="candidateIndex"
      />
    </div>

    <p v-else class="empty-tier">该档位暂无候选学校。</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import ReportCandidateCard from './ReportCandidateCard.vue'

const props = defineProps({
  tier: { type: Object, default: () => ({}) },
  index: { type: Number, default: 0 }
})

const candidates = computed(() => Array.isArray(props.tier?.candidates) ? props.tier.candidates : [])
const tierLabel = computed(() => props.tier?.label || labelByLevel(props.tier?.level) || `第 ${props.index + 1} 档`)
const targetCount = computed(() => props.tier?.targetCount || candidates.value.length)
const chapterIndex = computed(() => String(props.index + 1).padStart(2, '0'))
const strategyText = computed(() => {
  const level = props.tier?.level
  if (level === 'reach') return '保留上限机会，重点关注风险边界。'
  if (level === 'steady') return '兼顾录取概率和学校质量，是报告核心选择。'
  if (level === 'safe') return '优先控制落榜风险，确保保底选择有效。'
  return '根据当前画像和候选池形成的分档建议。'
})

function labelByLevel(level) {
  const map = { reach: '冲刺档', steady: '稳妥档', safe: '保底档' }
  return map[level] || ''
}
</script>

<style scoped>
.tier-section {
  margin-top: 30px;
  padding-top: 24px;
  border-top: 1px solid #e4edf8;
}

.tier-title-row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.chapter-index {
  margin: 0 0 4px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 900;
}

h2 {
  margin: 0;
  color: #10213f;
  font-size: 22px;
  line-height: 30px;
}

.tier-title-row span {
  display: block;
  margin-top: 5px;
  color: #607592;
  font-size: 13px;
  line-height: 20px;
}

.tier-count {
  flex-shrink: 0;
  min-width: 92px;
  padding: 10px 12px;
  border: 1px solid #dce7f6;
  border-radius: 8px;
  background: #f8fbff;
  text-align: center;
}

.tier-count strong {
  display: block;
  color: #1769f6;
  font-size: 24px;
  line-height: 28px;
}

.tier-count span {
  margin-top: 2px;
  color: #71829a;
  font-size: 12px;
  font-weight: 800;
}

.insufficient-notice {
  margin: 0 0 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff7ed;
  color: #b55c00;
  font-size: 13px;
  line-height: 20px;
}

.candidate-list {
  display: grid;
  gap: 14px;
}

.empty-tier {
  margin: 0;
  padding: 18px;
  border: 1px dashed #dce7f6;
  border-radius: 8px;
  color: #71829a;
  text-align: center;
}

@media (max-width: 640px) {
  .tier-title-row {
    flex-direction: column;
  }

  .tier-count {
    width: 100%;
  }
}
</style>
