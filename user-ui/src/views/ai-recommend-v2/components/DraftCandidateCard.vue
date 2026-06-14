<template>
  <div class="candidate-card" :class="{ 'is-adjusted': candidate.adjusted }">
    <div class="card-top">
      <div class="card-title">
        <span class="school-name">{{ candidate.fact.schoolName }}</span>
        <p class="program-name">{{ candidate.fact.collegeName }} · {{ candidate.fact.programName }}</p>
      </div>
      <div class="card-badges">
        <el-tag :type="tierTagType" size="small" effect="plain">{{ candidate.fact.schoolTier }}</el-tag>
        <button class="remove-btn" title="移出草稿" @click="$emit('remove')">
          <svg viewBox="0 0 16 16" width="14" height="14">
            <line x1="4" y1="4" x2="12" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            <line x1="12" y1="4" x2="4" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="fact-grid">
      <span class="fact-item">
        <em>均分</em>{{ displayVal(candidate.fact.avgAdmittedScore) }}
      </span>
      <span class="fact-item">
        <em>差距</em>{{ displayGap(candidate.fact.scoreGap) }}
      </span>
      <span class="fact-item">
        <em>名额</em>{{ candidate.fact.unifiedExamQuota || candidate.fact.planCount || '-' }}
      </span>
      <span class="fact-item">
        <em>城市</em>{{ candidate.fact.city || '-' }}
      </span>
    </div>

    <p v-if="candidate.opinion?.reason" class="ai-reason">{{ candidate.opinion.reason }}</p>

    <div v-if="candidate.opinion?.risks?.length" class="risk-tags">
      <span v-for="risk in candidate.opinion.risks" :key="risk" class="risk-tag">{{ risk }}</span>
    </div>

    <div v-if="candidate.adjusted" class="adjust-notice">
      <i class="el-icon-warning"></i>
      {{ candidate.adjustReason || '系统已自动调整档位' }}
    </div>

    <div class="card-actions">
      <button type="button" class="ask-btn" @click="$emit('ask-about')">问 AI</button>
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

function displayVal(value) {
  return value != null ? value : '-'
}

function displayGap(value) {
  if (value == null) return '-'
  return value >= 0 ? `+${value}` : `${value}`
}
</script>

<style scoped>
.candidate-card {
  min-width: 0;
  padding: 13px;
  border: 1px solid #e4edf8;
  border-radius: 8px;
  background: #fff;
  margin-bottom: 10px;
  transition: border-color .18s ease, background .18s ease;
}

.candidate-card:hover {
  border-color: #b8d3fb;
  background: #fbfdff;
}

.candidate-card.is-adjusted {
  border-color: #f5dab1;
  background: #fef8ef;
}

.card-top {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.card-title {
  min-width: 0;
  flex: 1;
}

.school-name {
  display: block;
  overflow: hidden;
  color: #10213f;
  font-weight: 900;
  font-size: 15px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.program-name {
  overflow: hidden;
  margin: 4px 0 0;
  color: #71829a;
  font-size: 12px;
  line-height: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-badges {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
}

.remove-btn {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #e4edf8;
  border-radius: 6px;
  background: #f6f9fd;
  color: #a8b2c1;
  cursor: pointer;
  transition: color .18s ease, background .18s ease, border-color .18s ease;
}

.remove-btn:hover {
  border-color: #fecdd3;
  background: #fff1f2;
  color: #dc2626;
}

.fact-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin: 10px 0 8px;
}

.fact-item {
  min-width: 0;
  padding: 7px 6px;
  border-radius: 7px;
  background: #f6f9fd;
  color: #10213f;
  font-size: 12px;
  line-height: 16px;
  font-weight: 800;
  text-align: center;
}

.fact-item em {
  display: block;
  margin: 0 0 2px;
  color: #71829a;
  font-style: normal;
  font-weight: 700;
}

.ai-reason {
  margin: 8px 0;
  padding: 8px 10px;
  border-left: 3px solid #1769f6;
  border-radius: 6px;
  background: #f4f8ff;
  color: #334155;
  font-size: 12px;
  line-height: 19px;
}

.risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 4px;
}

.risk-tag {
  max-width: 100%;
  padding: 3px 7px;
  border-radius: 999px;
  background: #fff7ed;
  color: #c46810;
  font-size: 11px;
  line-height: 16px;
}

.adjust-notice {
  margin-top: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(230, 162, 60, .1);
  font-size: 12px;
  color: #b88230;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.ask-btn {
  min-width: 72px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #cfe0f6;
  border-radius: 999px;
  background: #fff;
  color: #1769f6;
  cursor: pointer;
  font-size: 12px;
  line-height: 28px;
  font-weight: 800;
  transition: background .18s ease, border-color .18s ease;
}

.ask-btn:hover {
  border-color: #9dc4ff;
  background: #f4f8ff;
}

@media (max-width: 520px) {
  .fact-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
