<template>
  <header class="report-cover">
    <div class="cover-main">
      <p class="eyebrow">408 考研筛选平台</p>
      <h1>AI 择校推荐报告</h1>
      <p class="subtitle">基于当前画像与候选池生成的最终推荐快照</p>
      <div class="cover-seal">
        <span>AI 生成</span>
        <span>只读报告</span>
        <span>候选池快照</span>
      </div>
    </div>
    <dl class="meta-grid">
      <div>
        <dt>报告编号</dt>
        <dd>{{ reportId || '-' }}</dd>
      </div>
      <div>
        <dt>生成时间</dt>
        <dd>{{ createdAt || '-' }}</dd>
      </div>
      <div>
        <dt>报告状态</dt>
        <dd>{{ statusLabel }}</dd>
      </div>
    </dl>
  </header>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  reportId: { type: [String, Number], default: '' },
  createdAt: { type: String, default: '' },
  status: { type: String, default: '' },
  summary: { type: String, default: '' }
})

const statusLabel = computed(() => {
  if (!props.status) return '已生成'
  const map = { COMPLETED: '已生成', FAILED: '生成失败', PROCESSING: '生成中' }
  return map[props.status] || props.status
})
</script>

<style scoped>
.report-cover {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 28px;
  padding: 8px 0 30px;
  border-bottom: 1px solid #e4edf8;
  overflow: hidden;
}

.report-cover::before {
  content: "";
  position: absolute;
  inset: -40px auto auto 50%;
  width: 360px;
  height: 180px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(23, 105, 246, .12), transparent 68%);
  transform: translateX(-20%);
  pointer-events: none;
}

.cover-main,
.meta-grid {
  position: relative;
  z-index: 1;
}

.eyebrow {
  margin: 0 0 8px;
  color: #1769f6;
  font-size: 13px;
  font-weight: 900;
}

h1 {
  margin: 0;
  color: #10213f;
  font-size: 34px;
  line-height: 42px;
  letter-spacing: 0;
}

.subtitle {
  margin: 10px 0 0;
  color: #607592;
  font-size: 14px;
  line-height: 22px;
}

.cover-seal {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cover-seal span {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border: 1px solid #dce7f6;
  border-radius: 999px;
  background: #f8fbff;
  color: #425b7c;
  font-size: 12px;
  font-weight: 900;
}

.meta-grid {
  margin: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.meta-grid div {
  padding: 10px 12px;
  border: 1px solid #e4edf8;
  border-radius: 8px;
  background: #f8fbff;
}

dt {
  color: #71829a;
  font-size: 12px;
  font-weight: 800;
}

dd {
  margin: 4px 0 0;
  color: #10213f;
  font-size: 13px;
  font-weight: 800;
  word-break: break-word;
}

@media (max-width: 900px) {
  .report-cover {
    grid-template-columns: 1fr;
  }
}
</style>
