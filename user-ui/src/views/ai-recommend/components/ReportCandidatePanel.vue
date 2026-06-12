<template>
  <section class="candidate-card">
    <div class="candidate-head">
      <div>
        <p class="candidate-eyebrow">输出草稿</p>
        <h2>报告草稿</h2>
        <p>{{ bookmarks.length }} / 10 所 · 已确认 {{ summary.confirmed }}</p>
      </div>
      <span v-if="analyzing" class="candidate-badge">分析中</span>
    </div>

    <div class="candidate-summary">
      <div>
        <strong>{{ summary.confirmed }}</strong>
        <span>已确认</span>
      </div>
      <div>
        <strong>{{ summary.conversation }}</strong>
        <span>对话加入</span>
      </div>
      <div>
        <strong>{{ summary.background }}</strong>
        <span>后台推荐</span>
      </div>
    </div>

    <div v-if="displayGroups.length" class="candidate-groups">
      <section v-for="group in displayGroups" :key="group.key" class="candidate-group">
        <div class="group-title" :class="group.tone">
          <span>{{ group.label }}</span>
          <strong>{{ group.items.length }}</strong>
        </div>

        <article
          v-for="candidate in group.items"
          :key="candidate.programId"
          class="candidate-item"
        >
          <!-- 学校名 / 专业方向 -->
          <div class="candidate-item-header">
            <strong class="candidate-school-name">{{ candidate.schoolName }}</strong>
            <span v-if="candidate.programName" class="candidate-program-name">{{ candidate.programName }}</span>
          </div>

          <!-- 标签行：档位 · 来源 · 状态 · 调整标记 -->
          <div class="candidate-tags-row">
            <span class="tag-chip judgement-chip">{{ candidate.judgementLabel }}</span>
            <span class="tag-chip source-chip">{{ candidate.sourceLabel }}</span>
            <span :class="['tag-chip', 'status-chip', candidate.userConfirmed ? 'chip-confirmed' : 'chip-pending']">
              {{ candidate.statusLabel }}
            </span>
            <span v-if="candidate.adjustedLabel" class="tag-chip adjusted-chip">{{ candidate.adjustedLabel }}</span>
          </div>

          <!-- 指标胶囊行 -->
          <div v-if="candidate.metrics.gapText || candidate.metrics.quotaText || candidate.metrics.completenessText" class="candidate-metrics-row">
            <span v-if="candidate.metrics.gapText" class="metric-pill gap-pill">{{ candidate.metrics.gapText }}</span>
            <span v-if="candidate.metrics.quotaText" class="metric-pill quota-pill">{{ candidate.metrics.quotaText }}</span>
            <span v-if="candidate.metrics.completenessText" class="metric-pill data-pill">{{ candidate.metrics.completenessText }}</span>
          </div>

          <!-- 推荐理由 -->
          <p class="candidate-reason">{{ candidate.reason }}</p>

          <!-- 操作按钮 -->
          <div class="candidate-actions">
            <el-button size="small" plain @click="$emit('ask-about', candidate.raw)">确认</el-button>
            <el-button size="small" plain type="danger" @click="$emit('remove', candidate.raw)">移除</el-button>
          </div>
        </article>
      </section>
    </div>

    <div v-else class="candidate-placeholder">
      <div class="placeholder-icon">
        <i class="el-icon-document" />
      </div>
      <strong>{{ active ? '正在等待报告候选' : '草稿尚未生成' }}</strong>
      <p>{{ active ? 'AI 完成画像分析后，冲刺、稳妥、保底候选会在这里汇总。' : '先生成 AI 推荐草稿，再确认要写入报告的学校。' }}</p>
      <div class="draft-steps">
        <span>生成候选</span>
        <span>确认学校</span>
        <span>生成报告</span>
      </div>
    </div>

    <el-button type="primary" :plain="!displayGroups.length" class="candidate-action" @click="$emit('generate')">
      生成最终报告
    </el-button>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { displayCandidate } from '../utils/display'

const props = defineProps({
  active: { type: Boolean, default: false },
  analyzing: { type: Boolean, default: false },
  bookmarks: { type: Array, default: () => [] },
  bookmarkGroups: { type: Array, default: () => [] },
  summary: {
    type: Object,
    default: () => ({ confirmed: 0, conversation: 0, background: 0 })
  }
})

const displayGroups = computed(() => {
  return props.bookmarkGroups.map(group => ({
    ...group,
    items: group.items.map(item => ({
      ...displayCandidate(item),
      raw: item
    }))
  })).filter(group => group.items.length > 0)
})

defineEmits(['generate', 'remove', 'ask-about'])
</script>

<style scoped>
/* ── 面板容器 ── */
.candidate-card {
  height: 100%;
  min-height: 0;
  padding: 18px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39, 86, 166, .07);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.candidate-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.candidate-eyebrow {
  margin: 0 0 4px !important;
  color: #1769f6 !important;
  font-size: 12px !important;
  line-height: 16px !important;
  font-weight: 900;
}

.candidate-head h2 {
  margin: 0;
  font-size: 18px;
  line-height: 24px;
}

.candidate-head p {
  margin: 5px 0 0;
  color: #71829a;
  font-size: 13px;
  line-height: 18px;
}

.candidate-badge {
  align-self: flex-start;
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 999px;
  color: #a85d00;
  background: #fff7e8;
  font-size: 12px;
  font-weight: 800;
}

/* ── 摘要统计 ── */
.candidate-summary {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.candidate-summary div {
  min-height: 56px;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #edf2f9;
  background: #fbfdff;
  text-align: center;
}

.candidate-summary strong {
  display: block;
  color: #1769f6;
  font-size: 19px;
  line-height: 23px;
}

.candidate-summary span {
  display: block;
  margin-top: 4px;
  color: #71829a;
  font-size: 12px;
  line-height: 16px;
}

/* ── 候选列表滚动区 ── */
.candidate-groups {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0 -8px;
  padding: 0 8px 4px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.candidate-groups::-webkit-scrollbar {
  width: 8px;
}

.candidate-groups::-webkit-scrollbar-thumb {
  background: #c7d6ea;
  border-radius: 999px;
}

.candidate-group {
  min-width: 0;
}

/* ── 分组标题 ── */
.group-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 800;
  line-height: 20px;
}

.group-title.reach {
  color: #df4d2f;
}

.group-title.steady {
  color: #1769f6;
}

.group-title.safe {
  color: #109160;
}

.group-title strong {
  min-width: 26px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: #f1f6ff;
  color: inherit;
  font-size: 12px;
  line-height: 22px;
  text-align: center;
}

/* ── 候选卡片 ── */
.candidate-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e7eef8;
  border-radius: 8px;
  background: #fbfdff;
}

.candidate-item + .candidate-item {
  margin-top: 8px;
}

/* ── 学校名 + 专业方向 ── */
.candidate-item-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.candidate-school-name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #10213f;
  font-size: 15px;
  line-height: 22px;
  font-weight: 700;
  word-break: break-all;
}

.candidate-program-name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #4f6684;
  font-size: 13px;
  line-height: 20px;
}

/* ── 标签行 ── */
.candidate-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.judgement-chip {
  color: #1769f6;
  background: #eaf2ff;
}

.source-chip {
  color: #4f6684;
  background: #f0f6ff;
}

.status-chip {
}

.chip-confirmed {
  color: #109160;
  background: #e8f8f0;
}

.chip-pending {
  color: #a85d00;
  background: #fff7e8;
}

.adjusted-chip {
  color: #a85d00;
  background: #fff7e8;
}

/* ── 指标胶囊 ── */
.candidate-metrics-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.metric-pill {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 700;
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
}

.gap-pill {
  color: #1769f6;
  background: #eaf2ff;
}

.quota-pill {
  color: #109160;
  background: #e8f8f0;
}

.data-pill {
  color: #8b6f2f;
  background: #fef9e7;
}

/* ── 推荐理由 ── */
.candidate-reason {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
  color: #667992;
  font-size: 13px;
  line-height: 20px;
}

/* ── 操作按钮 ── */
.candidate-actions {
  display: flex;
  gap: 8px;
  padding-top: 2px;
}

.candidate-actions .el-button {
  min-width: 72px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
}

/* ── 空状态 ── */
.candidate-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 22px 16px;
  border: 1px dashed #cfe0f6;
  border-radius: 8px;
  background: #fbfdff;
  text-align: center;
}

.placeholder-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 12px;
  border: 1px solid #dce7f6;
  border-radius: 8px;
  background: #fff;
  color: #8ba0bd;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.candidate-placeholder strong {
  display: block;
  color: #10213f;
  font-size: 14px;
  line-height: 20px;
}

.candidate-placeholder p {
  margin: 8px 0 0;
  color: #71829a;
  font-size: 13px;
  line-height: 1.7;
}

.draft-steps {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin-top: 18px;
}

.draft-steps span {
  min-width: 0;
  min-height: 30px;
  padding: 6px;
  border-radius: 7px;
  background: #eef4fb;
  color: #607592;
  font-size: 12px;
  line-height: 18px;
  font-weight: 800;
}

/* ── 生成报告按钮 ── */
.candidate-action {
  flex-shrink: 0;
  width: 100%;
  min-height: 42px;
  margin-top: 16px;
  border-radius: 7px;
  font-weight: 800;
}
</style>
