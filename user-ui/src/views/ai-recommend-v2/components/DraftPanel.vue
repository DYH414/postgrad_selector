<template>
  <section class="draft-panel">
    <header class="panel-head">
      <div class="panel-head-left">
        <p class="panel-eyebrow">报告草稿</p>
        <h2 class="panel-title">候选池</h2>
      </div>
      <div class="panel-head-right">
        <span class="total t-num">{{ totalCount }}<em>/10</em></span>
      </div>
    </header>

    <!-- 加载态 -->
    <div v-if="loading && !draft" class="panel-loading">
      <span class="loading-spinner" />
      <p>{{ progress.message || '正在准备...' }}</p>
    </div>

    <!-- 生成中占位 — 刷新后恢复 -->
    <div v-else-if="draftGenerating" class="panel-loading">
      <span class="loading-spinner" />
      <p>{{ generatingMessage }}</p>
    </div>

    <!-- 空态 -->
    <div v-else-if="!draft || allEmpty" class="panel-empty">
      <div class="empty-illustration" aria-hidden="true">
        <span class="orb orb-1" />
        <span class="orb orb-2" />
        <span class="orb orb-3" />
        <span class="orb-center">+</span>
      </div>
      <p class="empty-title">尚未生成草稿</p>
      <p class="empty-sub">点击左侧 <strong>生成新的候选草稿</strong><br/>AI 会按画像筛选冲稳保候选。</p>
      <div class="empty-steps">
        <span class="empty-step"><i>1</i>生成</span>
        <span class="empty-step-sep" />
        <span class="empty-step"><i>2</i>校准</span>
        <span class="empty-step-sep" />
        <span class="empty-step"><i>3</i>出报告</span>
      </div>
    </div>

    <!-- 三档内容 -->
    <div v-else class="tier-workspace">
      <nav class="tier-tabs">
        <button
          type="button"
          class="tier-tab tier-tab--all"
          :class="{ active: activeTier === 'all' }"
          @click="activeTier = 'all'"
        >
          <span class="tier-tab-name">全部</span>
          <span class="tier-tab-count t-num">{{ totalCount }}</span>
        </button>
        <button
          v-for="tier in draft.tiers"
          :key="tier.level"
          type="button"
          class="tier-tab"
          :class="['tier-tab--' + tier.level, { active: tier.level === activeTier }]"
          @click="activeTier = tier.level"
        >
          <span class="tier-tab-name">{{ tier.label }}</span>
          <span class="tier-tab-count t-num">
            {{ tier.candidates?.length || 0 }}<em>/{{ tier.targetCount }}</em>
          </span>
        </button>
      </nav>

      <div class="tier-list">
        <div v-for="tier in visibleTiers" :key="tier.level" class="tier-group">
          <div class="tier-head">
            <div class="tier-head-left">
              <span :class="['tier-badge', 'tier-badge--' + tier.level]">{{ tier.label }}</span>
              <span class="tier-hint">{{ tierHint(tier.level) }}</span>
            </div>
            <span v-if="tier.insufficient" class="tier-insufficient">不足 · {{ tier.insufficientReason || '数据有限' }}</span>
          </div>

          <TransitionGroup name="card-list" tag="div" class="cards">
            <DraftCandidateCard
              v-for="candidate in tier.candidates"
              :key="candidate.fact.programId"
              :candidate="candidate"
              :tier="tier.level"
              @remove="$emit('remove', candidate.fact.programId)"
              @ask-about="$emit('ask-about', candidate.fact.programId, candidate.fact.schoolName, candidate.fact.programName)"
            />
          </TransitionGroup>

          <button
            v-if="tier.insufficient && tier.candidates?.length < tier.targetCount"
            class="add-slot"
            @click="$emit('add-from-workspace', { tier: tier.level, preference: 'safer' })"
          >
            <span>补充一个 {{ tier.label }} 候选</span>
            <span class="add-icon">+</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 已移除候选（默认折叠） -->
    <div v-if="dedupedRemoved.length" class="removed-list">
      <button class="removed-toggle" @click="showRemoved = !showRemoved">
        <span class="t-eyebrow removed-title">已移出（{{ dedupedRemoved.length }}）</span>
        <span class="toggle-icon">{{ showRemoved ? '▾' : '▸' }}</span>
      </button>
      <Transition name="collapse">
        <ul v-if="showRemoved">
          <li v-for="c in dedupedRemoved" :key="c.fact.programId">
            <span class="removed-name">{{ c.fact.schoolName }}<em>·</em>{{ c.fact.programName }}</span>
            <button class="removed-restore" @click="$emit('add-back', c.fact.programId)">加回</button>
          </li>
        </ul>
      </Transition>
    </div>

    <!-- 底部生成按钮 -->
    <footer class="report-action-bar">
      <div class="report-action-meta">
        <span class="t-eyebrow">最终报告</span>
        <strong>{{ totalCount ? `${totalCount} 所已就绪` : '等待候选草稿' }}</strong>
      </div>
      <button
        class="report-action-btn"
        :disabled="!draft || totalCount === 0"
        @click="$emit('generate-report')"
      >
        生成报告
        <span class="btn-arrow">→</span>
      </button>
    </footer>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import DraftCandidateCard from './DraftCandidateCard.vue'
import { isDraftGenerating } from '../generationRecovery.mjs'

const props = defineProps({
  draft: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  progress: { type: Object, default: () => ({ phase: '', message: '' }) }
})

defineEmits(['remove', 'replace', 'add-back', 'add-from-workspace', 'ask-about', 'generate-report'])

const activeTier = ref('all')
const showRemoved = ref(false)

const dedupedRemoved = computed(() => {
  const list = props.draft?.removedCandidates || []
  const seen = new Set()
  return list.filter(c => {
    const pid = c?.fact?.programId
    if (!pid || seen.has(pid)) return false
    seen.add(pid)
    return true
  })
})

const totalCount = computed(() => {
  if (!props.draft?.tiers) return 0
  return props.draft.tiers.reduce((s, t) => s + (t.candidates?.length || 0), 0)
})

const allEmpty = computed(() => totalCount.value === 0)
const draftGenerating = computed(() => isDraftGenerating(props.draft))
const generatingMessage = computed(() => {
  const t = props.draft?.tiers?.find(t => t.insufficient && t.insufficientReason)
  return t?.insufficientReason || '正在生成候选草稿...'
})

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
    reach: '冲刺档 · 保留上限机会',
    steady: '稳妥档 · 主目标区间',
    safe: '保底档 · 控制落空风险',
    conservative: '保底档 · 控制落空风险'
  }
  return map[level] || '按画像与数据排序'
}
</script>

<style scoped>
.draft-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--bg-elev);
  box-shadow: 0 8px 24px rgba(36, 78, 156, 0.06);
  overflow: hidden;
}

/* ── Head ── */
.panel-head {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 16px 20px 14px;
  border-bottom: 1px solid var(--line);
  background:
    linear-gradient(135deg, rgba(23, 105, 246, 0.05) 0%, rgba(155, 89, 255, 0.04) 100%),
    var(--bg-elev);
}

.panel-eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--brand);
}

.panel-title {
  margin: 2px 0 0;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.total {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--brand);
  line-height: 1;
}

.total em {
  font-size: 12px;
  color: var(--ink-4);
  font-style: normal;
  font-weight: 400;
  margin-left: 1px;
}

/* ── Loading ── */
.panel-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--ink-3);
  font-size: 13px;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 1.5px solid var(--brand-soft-2);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.panel-loading p { margin: 0; }

/* ── Empty state — illustration + steps ── */
.panel-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 28px 20px;
  gap: 14px;
}

.empty-illustration {
  position: relative;
  width: 84px;
  height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.orb {
  position: absolute;
  border-radius: 50%;
  opacity: 0.6;
}

.orb-1 {
  width: 40px;
  height: 40px;
  background: var(--brand-soft-2);
  top: 4px;
  left: 4px;
  animation: float 4s var(--ease) infinite;
}

.orb-2 {
  width: 28px;
  height: 28px;
  background: #f0e9ff;
  bottom: 8px;
  right: 6px;
  animation: float 4s var(--ease) infinite 0.6s;
}

.orb-3 {
  width: 18px;
  height: 18px;
  background: var(--warn-soft-2);
  top: 14px;
  right: 4px;
  animation: float 4s var(--ease) infinite 1.2s;
}

.orb-center {
  position: relative;
  z-index: 1;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-elev);
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  color: var(--brand);
  font-size: 18px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(36, 78, 156, 0.1);
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(0, -4px); }
}

.empty-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
}

.empty-sub {
  margin: 0;
  font-size: 12px;
  color: var(--ink-3);
  line-height: 1.6;
  max-width: 240px;
}

.empty-sub strong { color: var(--brand); font-weight: 600; }

.empty-steps {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.empty-step {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--ink-3);
  font-weight: 500;
}

.empty-step i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--bg-soft);
  color: var(--brand);
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
}

.empty-step-sep {
  width: 16px;
  height: 1px;
  background: var(--line-strong);
}

/* ── Tier workspace ── */
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
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
  background: var(--bg-soft);
}

.tier-tab {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 8px 10px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  color: var(--ink-3);
  cursor: pointer;
  text-align: left;
  transition: color var(--t-fast) var(--ease), border-color var(--t-fast) var(--ease), background var(--t-fast) var(--ease);
}

.tier-tab:hover {
  color: var(--ink-1);
  background: var(--brand-soft-3);
}

.tier-tab-name {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
}

.tier-tab-count {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-2);
  letter-spacing: -0.02em;
}

.tier-tab-count em {
  font-size: 11px;
  color: var(--ink-4);
  font-style: normal;
  font-weight: 400;
}

.tier-tab.active {
  color: var(--ink-1);
  background: var(--bg-elev);
  border-bottom-color: var(--brand);
}

.tier-tab.active .tier-tab-count { color: var(--brand); }

.tier-tab--reach.active { border-bottom-color: var(--tier-reach); }
.tier-tab--reach.active .tier-tab-count { color: var(--tier-reach); }
.tier-tab--steady.active { border-bottom-color: var(--tier-steady); }
.tier-tab--safe.active,
.tier-tab--conservative.active { border-bottom-color: var(--tier-safe); }
.tier-tab--safe.active .tier-tab-count,
.tier-tab--conservative.active .tier-tab-count { color: var(--tier-safe); }

/* ── Tier list ── */
.tier-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 20px 16px;
}

.tier-group { padding: 14px 0 12px; }
.tier-group + .tier-group { border-top: 1px dashed var(--line); }

.tier-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.tier-head-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

/* Tier badge — 替换原来简陋的色条 */
.tier-badge {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
  padding: 3px 10px;
  border-radius: 999px;
  color: #fff;
  flex-shrink: 0;
}

.tier-badge--reach {
  background: linear-gradient(135deg, #f57c1f 0%, #e85a0a 100%);
  box-shadow: 0 2px 8px rgba(232, 90, 10, 0.25);
}

.tier-badge--steady {
  background: linear-gradient(135deg, #2a7bff 0%, #1769f6 100%);
  box-shadow: 0 2px 8px rgba(23, 105, 246, 0.25);
}

.tier-badge--safe,
.tier-badge--conservative {
  background: linear-gradient(135deg, #2bb078 0%, #0f9b6c 100%);
  box-shadow: 0 2px 8px rgba(15, 155, 108, 0.25);
}

.tier-hint {
  font-size: 11px;
  color: var(--ink-3);
}

.tier-insufficient {
  font-size: 11px;
  color: var(--warn);
  background: var(--warn-soft-2);
  padding: 2px 6px;
  border-radius: 3px;
}

.cards { display: flex; flex-direction: column; gap: 8px; }

/* ── Add slot ── */
.add-slot {
  width: 100%;
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--bg-soft);
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-md);
  color: var(--ink-3);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all var(--t-fast) var(--ease);
}

.add-slot:hover {
  border-color: var(--brand);
  border-style: solid;
  color: var(--brand);
  background: var(--brand-soft-3);
}

.add-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--bg-elev);
  border: 1px solid var(--line-strong);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--brand);
  transition: all var(--t-fast) var(--ease);
}

.add-slot:hover .add-icon {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

/* ── Card transition ── */
.card-list-enter-active { transition: all .25s var(--ease); }
.card-list-leave-active { transition: all .2s var(--ease); }
.card-list-enter-from { opacity: 0; transform: translateY(4px); }
.card-list-leave-to { opacity: 0; transform: translateX(8px); }

/* ── Removed list ── */
.removed-list {
  flex-shrink: 0;
  border-top: 1px solid var(--line);
  padding: 12px 20px;
  background: var(--bg-soft);
}

.removed-title { margin: 0 0 8px; }

.removed-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: inherit;
}

.toggle-icon {
  font-size: 10px;
  color: var(--ink-4);
  margin-left: 8px;
}

.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.2s ease;
}
.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.removed-list ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.removed-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0;
}

.removed-name {
  font-size: 12px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.removed-name em {
  margin: 0 4px;
  color: var(--ink-4);
  font-style: normal;
}

.removed-restore {
  background: transparent;
  border: 0;
  padding: 2px 6px;
  color: var(--brand);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  flex-shrink: 0;
}
.removed-restore:hover { color: var(--brand-hover); text-decoration: underline; }

/* ── Report action bar ── */
.report-action-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid var(--line);
  background: var(--bg-elev);
}

.report-action-meta { min-width: 0; }
.report-action-meta strong {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.report-action-btn {
  height: 36px;
  padding: 0 18px;
  background: var(--brand-gradient);
  color: #fff;
  border: 0;
  border-radius: var(--r-md);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(23, 105, 246, 0.22);
  transition: box-shadow var(--t-fast) var(--ease);
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.report-action-btn:hover:not(:disabled) {
  box-shadow: 0 10px 20px rgba(23, 105, 246, 0.32);
}

.report-action-btn:hover:not(:disabled) .btn-arrow {
  transform: translateX(2px);
}

.btn-arrow {
  transition: transform var(--t-fast) var(--ease);
}

.report-action-btn:disabled {
  background: var(--line-strong);
  color: var(--ink-4);
  cursor: not-allowed;
  box-shadow: none;
}

@media (max-width: 960px) {
  .tier-tabs { grid-template-columns: repeat(2, 1fr); }
  .report-action-bar { flex-direction: column; align-items: stretch; }
}
</style>
