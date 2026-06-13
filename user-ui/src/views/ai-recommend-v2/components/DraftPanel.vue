<template>
  <section class="draft-panel">
    <div class="panel-head">
      <div>
        <p class="panel-eyebrow">输出草稿</p>
        <h3>报告草稿</h3>
      </div>
      <span class="draft-total">{{ totalCount }} / 10 所</span>
    </div>

    <!-- 加载态 -->
    <div v-if="loading && !draft" class="panel-loading">
      <div class="loading-phase">
        <i class="el-icon-loading"></i>
        <span>{{ progress.message || '正在准备...' }}</span>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="!draft || allEmpty" class="panel-empty">
      <div class="empty-icon">
        <svg viewBox="0 0 64 64" width="64" height="64">
          <rect x="8" y="12" width="48" height="40" rx="4" fill="none" stroke="#c0c8d4" stroke-width="2"/>
          <line x1="20" y1="26" x2="44" y2="26" stroke="#c0c8d4" stroke-width="2" stroke-linecap="round"/>
          <line x1="20" y1="32" x2="38" y2="32" stroke="#c0c8d4" stroke-width="2" stroke-linecap="round"/>
          <line x1="20" y1="38" x2="34" y2="38" stroke="#c0c8d4" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <p>草稿尚未生成</p>
      <span>先生成 AI 推荐草稿，再确认要写入报告的学校。</span>
      <div class="draft-steps">
        <span>生成候选</span>
        <span>确认学校</span>
        <span>生成报告</span>
      </div>
    </div>

    <!-- 三档内容 -->
    <div v-else class="tier-list">
      <div v-for="tier in draft.tiers" :key="tier.level" class="tier-group">
        <div class="tier-head">
          <span class="tier-label">{{ tier.label }}</span>
          <span class="tier-progress">
            {{ tier.candidates?.length || 0 }} / {{ tier.targetCount }}
          </span>
          <el-tag v-if="tier.insufficient" type="warning" size="small" effect="plain">不足</el-tag>
        </div>

        <p v-if="tier.insufficient && tier.insufficientReason" class="tier-notice">
          {{ tier.insufficientReason }}
        </p>

        <!-- 候选卡片列表 -->
        <TransitionGroup name="card-list" tag="div">
          <DraftCandidateCard
            v-for="candidate in tier.candidates"
            :key="candidate.fact.programId"
            :candidate="candidate"
            @remove="$emit('remove', candidate.fact.programId)"
            @ask-about="$emit('ask-about', candidate.fact.programId, candidate.fact.schoolName, candidate.fact.programName)"
          />
        </TransitionGroup>

        <!-- 替换占位（该档不足时） -->
        <button
          v-if="tier.insufficient && tier.candidates?.length < tier.targetCount"
          class="add-slot"
          @click="$emit('replace', { tier: tier.level, preference: 'safer' })"
        >
          + 为{{ tier.label }}补充一所
        </button>
      </div>
    </div>

    <!-- 已移除候选 -->
    <div v-if="draft?.removedCandidates?.length" class="removed-list">
      <h4>已移出草稿</h4>
      <div v-for="c in draft.removedCandidates" :key="c.fact.programId" class="removed-item">
        <span>{{ c.fact.schoolName }} — {{ c.fact.programName }}</span>
        <el-button type="text" size="small" @click="$emit('add-back', c.fact.programId)">加回</el-button>
      </div>
    </div>

    <!-- 底部生成按钮 -->
    <div class="panel-footer">
      <el-button
        type="primary"
        size="large"
        :disabled="!draft || totalCount === 0"
        @click="$emit('generate-report')"
      >
        生成最终报告
      </el-button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import DraftCandidateCard from './DraftCandidateCard.vue'

const props = defineProps({
  draft: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  progress: { type: Object, default: () => ({ phase: '', message: '' }) }
})

defineEmits(['remove', 'replace', 'add-back', 'ask-about', 'generate-report'])

const totalCount = computed(() => {
  if (!props.draft?.tiers) return 0
  return props.draft.tiers.reduce((s, t) => s + (t.candidates?.length || 0), 0)
})

const allEmpty = computed(() => totalCount.value === 0)
</script>

<style scoped>
.draft-panel {
  height: 100%;
  min-height: 0;
  padding: 18px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39,86,166,.07);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-head {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}
.panel-eyebrow { margin: 0 0 4px; color: #1769f6; font-size: 12px; line-height: 16px; font-weight: 900; }
.panel-head h3 { margin: 0; font-size: 18px; }
.draft-total { font-size: 13px; color: #71829a; font-weight: 600; }

/* 加载 */
.panel-loading { flex: 1; display: flex; align-items: center; justify-content: center; text-align: center; padding: 60px 0; }
.loading-phase { display: flex; align-items: center; justify-content: center; gap: 8px; color: #71829a; font-size: 14px; }

/* 空态 */
.panel-empty {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 36px 20px;
  border: 1px dashed #cfe0f6;
  border-radius: 8px;
  background: #fbfdff;
}
.empty-icon { margin-bottom: 16px; }
.panel-empty p { margin: 0 0 4px; font-size: 15px; color: #303133; }
.panel-empty span { font-size: 13px; color: #a8b2c1; }
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

/* 档位 */
.tier-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  padding-right: 4px;
}
.tier-group { margin-bottom: 16px; }
.tier-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.tier-label { font-size: 15px; font-weight: 600; }
.tier-progress { font-size: 12px; color: #71829a; }
.tier-notice { color: #e6a23c; font-size: 12px; margin: 0 0 8px; line-height: 18px; }

/* 卡片过渡 */
.card-list-enter-active { transition: all .3s ease; }
.card-list-leave-active { transition: all .25s ease; }
.card-list-enter-from { opacity: 0; transform: translateY(10px); }
.card-list-leave-to { opacity: 0; transform: translateX(20px); }

/* 补充按钮 */
.add-slot {
  display: block;
  width: 100%;
  padding: 10px;
  border: 1px dashed #c0c8d4;
  border-radius: 6px;
  background: transparent;
  color: #71829a;
  font-size: 13px;
  cursor: pointer;
  transition: border-color .2s, color .2s;
}
.add-slot:hover { border-color: #409eff; color: #409eff; }

/* 已移除 */
.removed-list {
  flex-shrink: 0;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f4fa;
}
.removed-list h4 { margin: 0 0 8px; font-size: 13px; color: #71829a; }
.removed-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  font-size: 13px;
  color: #a8b2c1;
}

.panel-footer { flex-shrink: 0; margin-top: 16px; text-align: center; }
.panel-footer .el-button { width: 100%; font-size: 15px; font-weight: 600; height: 44px; }
</style>
