<template>
  <section class="candidate-card">
    <div class="candidate-head">
      <div>
        <h2>报告候选</h2>
        <p>已确认 {{ summary.confirmed }} · 对话加入 {{ summary.conversation }} · 后台推荐 {{ summary.background }}</p>
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
        <article v-for="candidate in group.items" :key="candidate.programId" class="candidate-item">
          <div class="candidate-item-main">
            <div class="candidate-title-row">
              <strong>{{ candidate.schoolName }}</strong>
              <span class="judgement-tag">{{ candidate.judgementLabel }}</span>
            </div>
            <p>{{ candidate.reason || candidate.programName || '等待 AI 补充推荐理由' }}</p>
            <div class="candidate-tags">
              <span>{{ candidate.sourceLabel }}</span>
              <span v-if="candidate.adjustedLabel" class="adjusted-tag">{{ candidate.adjustedLabel }}</span>
              <span v-for="tag in candidate.tags" :key="tag">{{ tag }}</span>
            </div>
          </div>
          <div class="candidate-item-meta">
            <span :class="['status-pill', candidate.userConfirmed || candidate.status === 'confirmed' ? 'confirmed' : 'pending']">
              {{ candidate.userConfirmed || candidate.status === 'confirmed' ? '已确认' : '待确认' }}
            </span>
            <button type="button" title="继续追问" @click="$emit('ask-about', candidate.raw)">
              <i class="el-icon-chat-dot-round" />
            </button>
            <button type="button" title="删除候选" @click="$emit('remove', candidate.raw)">
              <i class="el-icon-close" />
            </button>
          </div>
        </article>
      </section>
    </div>

    <div v-else class="candidate-placeholder">
      <strong>{{ active ? '正在等待报告候选' : '尚未开始 AI 对话' }}</strong>
      <p>{{ active ? 'AI 完成画像分析后，冲刺、稳妥、保底候选会在这里汇总。' : '点击左侧“按我的画像开始筛选”后开始生成候选。' }}</p>
    </div>

    <el-button type="primary" class="candidate-action" @click="$emit('generate')">
      生成推荐报告
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
.candidate-card {
  height: 100%;
  min-height: 0;
  padding: 20px;
  border: 1px solid #e5edf8;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 35, 75, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.candidate-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
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

.candidate-summary {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 16px;
}

.candidate-summary div {
  min-height: 64px;
  padding: 10px 8px;
  border-radius: 8px;
  background: #f8fbff;
  text-align: center;
}

.candidate-summary strong {
  display: block;
  color: #1769f6;
  font-size: 20px;
  line-height: 24px;
}

.candidate-summary span {
  display: block;
  margin-top: 4px;
  color: #71829a;
  font-size: 12px;
  line-height: 16px;
}

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

.candidate-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e7eef8;
  border-radius: 8px;
  background: #fbfdff;
}

.candidate-item + .candidate-item {
  margin-top: 8px;
}

.candidate-item-main {
  min-width: 0;
}

.candidate-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.candidate-item-main strong {
  display: block;
  min-width: 0;
  color: #10213f;
  font-size: 14px;
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.judgement-tag {
  flex-shrink: 0;
  min-height: 22px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eaf2ff;
  color: #1769f6;
  font-size: 12px;
  line-height: 18px;
  font-weight: 800;
}

.candidate-item-main p {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 4px 0 0;
  color: #667992;
  font-size: 12px;
  line-height: 18px;
}

.candidate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.candidate-tags span {
  min-height: 22px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #f0f6ff;
  color: #4f6684;
  font-size: 12px;
  line-height: 18px;
  font-weight: 700;
}

.candidate-tags .adjusted-tag {
  color: #a85d00;
  background: #fff7e8;
}

.candidate-item-meta {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  flex-shrink: 0;
}

.status-pill {
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 800;
  white-space: nowrap;
}

.status-pill.confirmed {
  color: #1769f6;
  background: #eaf2ff;
}

.status-pill.pending {
  color: #a85d00;
  background: #fff7e8;
}

.candidate-item-meta button {
  width: 24px;
  height: 24px;
  padding: 0;
  border: 1px solid #dbe7f7;
  border-radius: 6px;
  background: #fff;
  color: #6d7f99;
  cursor: pointer;
}

.candidate-item-meta button:hover {
  color: #1769f6;
  border-color: #bfd5ff;
  background: #f4f8ff;
}

.candidate-placeholder {
  flex: 1;
  padding: 16px;
  border: 1px dashed #cfe0f6;
  border-radius: 8px;
  background: #fbfdff;
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

.candidate-action {
  flex-shrink: 0;
  width: 100%;
  min-height: 40px;
  margin-top: 18px;
  border-radius: 7px;
  font-weight: 800;
}
</style>
