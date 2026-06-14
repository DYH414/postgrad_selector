<template>
  <footer class="data-notice">
    <strong>数据口径说明</strong>
    <p>本报告基于当前画像、候选池与可用院校专业事实生成，是一次最终推荐快照。</p>
    <p v-if="insufficientText">{{ insufficientText }}</p>
    <p>当部分院校专业数据不完整时，报告会保留风险提示，不将候选结果包装为确定结论。</p>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tiers: { type: Array, default: () => [] },
  profileBasis: { type: Object, default: () => ({}) }
})

const insufficientText = computed(() => {
  const names = props.tiers
    .filter(item => item?.insufficient)
    .map(item => item.label || item.level)
    .filter(Boolean)
  return names.length ? `候选不足档位：${names.join('、')}。` : ''
})
</script>

<style scoped>
.data-notice {
  margin-top: 28px;
  padding: 16px;
  border: 1px solid #e4edf8;
  border-radius: 8px;
  background: #f8fbff;
  color: #607592;
}

.data-notice strong {
  display: block;
  color: #10213f;
  font-size: 14px;
}

.data-notice p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 21px;
}
</style>
