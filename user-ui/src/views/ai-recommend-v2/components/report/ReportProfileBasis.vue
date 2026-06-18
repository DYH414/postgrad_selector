<template>
  <section class="basis-section">
    <div class="section-head">
      <p>推荐依据</p>
      <h2>画像与筛选口径</h2>
    </div>
    <div class="basis-grid">
      <div v-for="item in items" :key="item.key" class="basis-item">
        <span>{{ item.label }}</span>
        <strong>{{ displayValue(item.value) }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  profileBasis: { type: Object, default: () => ({}) }
})

const items = computed(() => {
  const basis = props.profileBasis || {}
  return [
    { key: 'estimatedScore', label: '预计分数', value: basis.estimatedScore },
    { key: 'targetRegions', label: '目标地区', value: basis.targetRegions },
    { key: 'undergradTier', label: '本科层次', value: basis.undergradTier },
    { key: 'isCrossMajor', label: '跨考情况', value: basis.isCrossMajor },
    { key: 'riskPreference', label: '安全边际', value: basis.riskPreference },
    { key: 'schoolTierPreference', label: '学校层次', value: basis.schoolTierPreference },
    { key: 'regionStrategy', label: '地区偏好', value: basis.regionStrategy },
    { key: 'candidateScope', label: '候选范围', value: basis.candidateScope }
  ]
})

function displayValue(value) {
  if (Array.isArray(value)) return value.length ? value.join('、') : '未填写'
  if (value === true) return '是'
  if (value === false) return '否'
  if (value === null || value === undefined || value === '') return '未填写'
  return String(value)
}
</script>

<style scoped>
.basis-section {
  margin-top: 24px;
}

.section-head {
  margin-bottom: 12px;
}

.section-head p {
  margin: 0 0 4px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 900;
}

.section-head h2 {
  margin: 0;
  color: #10213f;
  font-size: 20px;
  line-height: 28px;
}

.basis-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.basis-item {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e4edf8;
  border-radius: 8px;
  background: #fff;
}

.basis-item span {
  display: block;
  color: #71829a;
  font-size: 12px;
  font-weight: 800;
}

.basis-item strong {
  display: block;
  margin-top: 5px;
  color: #10213f;
  font-size: 14px;
  line-height: 20px;
  word-break: break-word;
}

@media (max-width: 900px) {
  .basis-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .basis-grid {
    grid-template-columns: 1fr;
  }
}
</style>
