<template>
  <div class="draft-panel">
    <div class="panel-header">
      <h3>报告草稿</h3>
      <span class="draft-count">{{ totalCount }} / 10 所</span>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="panel-loading">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 空草稿 -->
    <div v-else-if="!draft || totalCount === 0" class="panel-empty">
      <p>点击左侧"生成 AI 推荐草稿"开始</p>
    </div>

    <!-- 三档候选 -->
    <div v-else class="tier-list">
      <div v-for="tier in draft.tiers" :key="tier.level" class="tier-group">
        <div class="tier-header">
          <span class="tier-label">{{ tier.label }}</span>
          <span class="tier-progress">
            {{ tier.candidates?.length || 0 }} / {{ tier.targetCount }}
          </span>
          <el-tag v-if="tier.insufficient" type="warning" size="small">不足</el-tag>
        </div>

        <p v-if="tier.insufficient" class="tier-insufficient-notice">
          {{ tier.insufficientReason }}
        </p>

        <DraftCandidateCard
          v-for="candidate in tier.candidates"
          :key="candidate.programId"
          :candidate="candidate"
          @remove="$emit('remove', candidate.programId)"
          @ask-about="$emit('ask-about', candidate.programId)"
        />
      </div>
    </div>

    <!-- 已移除列表 -->
    <div v-if="draft?.removedCandidates?.length" class="removed-section">
      <h4>已移出草稿</h4>
      <div v-for="c in draft.removedCandidates" :key="c.programId" class="removed-item">
        <span>{{ c.schoolName }} — {{ c.programName }}</span>
        <el-button type="text" size="small" @click="$emit('add-back', c.programId)">加回</el-button>
      </div>
    </div>

    <!-- 底部操作 -->
    <div class="panel-footer">
      <el-button
        type="primary"
        :disabled="!draft || totalCount === 0"
        @click="$emit('generate-report')"
      >
        生成最终报告
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DraftCandidateCard from './DraftCandidateCard.vue'

const props = defineProps({
  draft: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

defineEmits(['remove', 'replace', 'add-back', 'ask-about', 'generate-report'])

const totalCount = computed(() => {
  if (!props.draft?.tiers) return 0
  return props.draft.tiers.reduce((sum, t) => sum + (t.candidates?.length || 0), 0)
})
</script>

<style scoped>
.draft-panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel-header h3 { margin: 0; }
.tier-list { flex: 1; overflow-y: auto; }
.tier-group { margin-bottom: 16px; }
.tier-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.tier-label { font-weight: 600; }
.tier-insufficient-notice {
  color: #e6a23c;
  font-size: 12px;
  margin-bottom: 8px;
}
.removed-section { margin-top: 12px; border-top: 1px solid #eee; padding-top: 12px; }
.removed-item { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; }
.panel-footer { margin-top: 12px; text-align: center; }
</style>
