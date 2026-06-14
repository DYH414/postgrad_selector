<template>
  <section class="summary-section">
    <div class="summary-copy">
      <p class="section-kicker">结论摘要</p>
      <h2>推荐结构与核心结论</h2>
      <p>{{ summary || '报告已生成，请查看下方分档推荐与风险说明。' }}</p>
    </div>
    <div class="count-grid">
      <div v-for="item in countItems" :key="item.key" class="count-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
        <em>所</em>
      </div>
    </div>
    <p v-if="insufficientText" class="insufficient-text">{{ insufficientText }}</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  summary: { type: String, default: '' },
  tiers: { type: Array, default: () => [] }
})

function countByLevel(level) {
  const labelMap = { reach: '冲刺', steady: '稳妥', safe: '保底' }
  const tier = props.tiers.find(item => item?.level === level || item?.label?.includes(labelMap[level]))
  return Array.isArray(tier?.candidates) ? tier.candidates.length : 0
}

const countItems = computed(() => {
  const reach = countByLevel('reach')
  const steady = countByLevel('steady')
  const safe = countByLevel('safe')
  return [
    { key: 'total', label: '总计', count: reach + steady + safe },
    { key: 'reach', label: '冲刺档', count: reach },
    { key: 'steady', label: '稳妥档', count: steady },
    { key: 'safe', label: '保底档', count: safe }
  ]
})

const insufficientText = computed(() => {
  const names = props.tiers
    .filter(item => item?.insufficient)
    .map(item => item.label || item.level)
    .filter(Boolean)
  return names.length ? `候选不足提醒：${names.join('、')}存在候选不足，报告已保留该不确定性。` : ''
})
</script>

<style scoped>
.summary-section {
  margin-top: 26px;
  padding: 22px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #f8fbff;
}

.section-kicker {
  margin: 0 0 6px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 900;
}

h2 {
  margin: 0;
  color: #10213f;
  font-size: 20px;
  line-height: 28px;
}

.summary-copy p:last-child {
  margin: 10px 0 0;
  color: #425b7c;
  font-size: 14px;
  line-height: 24px;
}

.count-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.count-card {
  padding: 12px;
  border: 1px solid #e4edf8;
  border-radius: 8px;
  background: #fff;
}

.count-card span,
.count-card em {
  display: block;
  color: #71829a;
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.count-card strong {
  display: block;
  margin-top: 4px;
  color: #1769f6;
  font-size: 24px;
  line-height: 28px;
}

.insufficient-text {
  margin: 14px 0 0;
  color: #b55c00;
  font-size: 13px;
  line-height: 20px;
}

@media (max-width: 768px) {
  .count-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
