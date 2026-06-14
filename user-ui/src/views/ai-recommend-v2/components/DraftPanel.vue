<template>
  <section class="draft-panel">
    <div class="panel-head">
      <div>
        <p class="panel-eyebrow">候选池确认</p>
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
    <div v-else class="tier-workspace">
      <div class="tier-tabs">
        <button
          type="button"
          class="tier-tab"
          :class="{ active: activeTier === 'all' }"
          @click="activeTier = 'all'"
        >
          <span>全部</span>
          <strong>{{ totalCount }}/10</strong>
        </button>
        <button
          v-for="tier in draft.tiers"
          :key="tier.level"
          type="button"
          class="tier-tab"
          :class="{ active: tier.level === activeTier }"
          @click="activeTier = tier.level"
        >
          <span>{{ tier.label }}</span>
          <strong>{{ tier.candidates?.length || 0 }}/{{ tier.targetCount }}</strong>
        </button>
      </div>

      <div class="tier-list-shell">
        <div v-for="tier in visibleTiers" :key="tier.level" class="tier-group">
          <div class="tier-head">
            <div>
              <span class="tier-label">{{ tier.label }}</span>
              <small>{{ tierHint(tier.level) }}</small>
            </div>
            <div class="tier-meta">
              <span class="tier-progress">{{ tier.candidates?.length || 0 }} / {{ tier.targetCount }}</span>
              <el-tag v-if="tier.insufficient" type="warning" size="small" effect="plain">不足</el-tag>
            </div>
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
            <span>补充{{ tier.label }}候选</span>
            <i class="el-icon-plus"></i>
          </button>
        </div>
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
    <div class="report-action-bar">
      <div>
        <span>最终报告</span>
        <strong>{{ totalCount ? '已整理候选草稿' : '等待候选草稿' }}</strong>
      </div>
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
import { computed, ref, watch } from 'vue'
import DraftCandidateCard from './DraftCandidateCard.vue'

const props = defineProps({
  draft: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  progress: { type: Object, default: () => ({ phase: '', message: '' }) }
})

defineEmits(['remove', 'replace', 'add-back', 'ask-about', 'generate-report'])

const activeTier = ref('all')

const totalCount = computed(() => {
  if (!props.draft?.tiers) return 0
  return props.draft.tiers.reduce((s, t) => s + (t.candidates?.length || 0), 0)
})

const allEmpty = computed(() => totalCount.value === 0)

const visibleTiers = computed(() => {
  const tiers = props.draft?.tiers || []
  if (activeTier.value === 'all') return tiers
  return tiers.filter(tier => tier.level === activeTier.value)
})

watch(
  () => props.draft?.tiers?.map(t => t.level).join(','),
  value => {
    if (!value) {
      activeTier.value = 'all'
      return
    }
    const levels = value.split(',')
    if (activeTier.value !== 'all' && !levels.includes(activeTier.value)) {
      activeTier.value = 'all'
    }
  }
)

function tierHint(level) {
  const map = {
    reach: '冲刺档：保留上限机会',
    steady: '稳妥档：优先匹配主目标',
    safe: '保底档：控制落空风险',
    conservative: '保底档：控制落空风险'
  }
  return map[level] || '按画像和数据质量排序'
}
</script>

<style scoped>
.draft-panel {
  height: 100%;
  min-height: 0;
  padding: 16px 16px 0;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39,86,166,.07);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  overflow-x: hidden;
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
.tier-workspace {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tier-tabs {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
  padding: 6px;
  border: 1px solid #edf2f9;
  border-radius: 8px;
  background: #f7faff;
}
.tier-tab {
  min-width: 0;
  min-height: 44px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #607592;
  cursor: pointer;
  text-align: left;
  transition: background .18s ease, border-color .18s ease, color .18s ease;
}
.tier-tab:hover,
.tier-tab.active {
  border-color: #b8d3fb;
  background: #fff;
  color: #1769f6;
}
.tier-tab span,
.tier-tab strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tier-tab span { font-size: 12px; line-height: 16px; font-weight: 800; }
.tier-tab strong { margin-top: 2px; color: #10213f; font-size: 14px; line-height: 18px; }
.tier-list-shell {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}
.tier-group { margin-bottom: 18px; }
.tier-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.tier-label { display: block; color: #10213f; font-size: 15px; font-weight: 800; }
.tier-head small { display: block; margin-top: 2px; color: #71829a; font-size: 12px; line-height: 17px; }
.tier-meta { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.tier-progress { font-size: 12px; color: #71829a; }
.tier-notice { color: #e6a23c; font-size: 12px; margin: 0 0 8px; line-height: 18px; }

/* 卡片过渡 */
.card-list-enter-active { transition: all .3s ease; }
.card-list-leave-active { transition: all .25s ease; }
.card-list-enter-from { opacity: 0; transform: translateY(10px); }
.card-list-leave-to { opacity: 0; transform: translateX(20px); }

/* 补充按钮 */
.add-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
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

.report-action-bar {
  position: sticky;
  bottom: 0;
  z-index: 2;
  flex-shrink: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  align-items: center;
  gap: 12px;
  margin: 12px -16px 0;
  padding: 12px 16px;
  border-top: 1px solid #edf2f9;
  background: rgba(255,255,255,.96);
  box-shadow: 0 -10px 24px rgba(39,86,166,.08);
}
.report-action-bar span,
.report-action-bar strong {
  display: block;
  min-width: 0;
}
.report-action-bar span { color: #71829a; font-size: 12px; line-height: 16px; }
.report-action-bar strong { overflow: hidden; color: #10213f; font-size: 13px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.report-action-bar .el-button { width: 100%; min-width: 0; font-size: 14px; font-weight: 800; height: 42px; border-radius: 8px; }

@media (max-width: 960px) {
  .tier-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .report-action-bar { grid-template-columns: 1fr; }
}
</style>
