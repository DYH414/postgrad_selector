<template>
  <article class="candidate-card" :class="{ 'is-adjusted': candidate.adjusted, ['tier--' + tier]: tier }">
    <div class="card-top">
      <div class="card-title">
        <h4 class="school-name">{{ candidate.fact.schoolName }}</h4>
        <p class="program-name">{{ candidate.fact.collegeName }} · {{ candidate.fact.programName }}</p>
      </div>
      <button class="remove-btn" title="移出草稿" aria-label="移出草稿" @click="$emit('remove')">
        <svg viewBox="0 0 16 16" width="12" height="12">
          <line x1="4" y1="4" x2="12" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <line x1="12" y1="4" x2="4" y2="12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <dl class="fact-grid">
      <div class="fact">
        <dt>均分</dt>
        <dd class="t-num">{{ displayVal(candidate.fact.avgAdmittedScore) }}</dd>
      </div>
      <div class="fact">
        <dt>差距</dt>
        <dd :class="['t-num', gapClass]">{{ displayGap(candidate.fact.scoreGap) }}</dd>
      </div>
      <div class="fact">
        <dt>名额</dt>
        <dd class="t-num">{{ candidate.fact.unifiedExamQuota || candidate.fact.planCount || '—' }}</dd>
      </div>
      <div class="fact">
        <dt>城市</dt>
        <dd>{{ candidate.fact.city || '—' }}</dd>
      </div>
    </dl>

    <p v-if="candidate.opinion?.reason" class="ai-reason">
      <span class="ai-quote">"</span>{{ candidate.opinion.reason }}
    </p>

    <div v-if="candidate.opinion?.risks?.length" class="risk-tags">
      <span v-for="risk in candidate.opinion.risks" :key="risk" class="risk-tag">{{ risk }}</span>
    </div>

    <div v-if="candidate.adjusted" class="adjust-notice">
      <i class="el-icon-warning-outline" />
      {{ candidate.adjustReason || '已自动调整档位' }}
    </div>

    <div v-if="hasSourceMeta" class="source-meta">
      <span v-if="candidate.fact.dataYear">{{ candidate.fact.dataYear }}年数据</span>
      <span v-if="candidate.fact.dataYear && sourceLabel" class="source-sep"> · </span>
      <a v-if="candidate.fact.sourceUrl" :href="candidate.fact.sourceUrl" target="_blank" rel="noopener noreferrer" class="source-link">
        来源：{{ sourceLabel }}
      </a>
      <span v-else-if="sourceLabel && !candidate.fact.sourceUrl">来源：{{ sourceLabel }}</span>
      <span v-else-if="candidate.fact.dataYear && !sourceLabel">来源待补</span>
    </div>

    <footer class="card-actions">
      <span v-if="candidate.fact.schoolTier" class="tier-label">{{ candidate.fact.schoolTier }}</span>
      <button type="button" class="ask-btn" @click="$emit('ask-about')">问 AI →</button>
    </footer>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  candidate: { type: Object, required: true },
  tier: { type: String, default: '' }
})

defineEmits(['remove', 'ask-about'])

const gapClass = computed(() => {
  const g = props.candidate.fact.scoreGap
  if (g == null) return ''
  if (g > 0) return 'gap--up'
  if (g < 0) return 'gap--down'
  return ''
})

const hasSourceMeta = computed(() => {
  const f = props.candidate.fact || {}
  return !!(f.dataYear || f.sourceUrl || f.sourceOwner)
})

const sourceLabel = computed(() => {
  const f = props.candidate.fact || {}
  return f.sourceOwner || '查看来源'
})

function displayVal(value) {
  return value != null ? value : '—'
}

function displayGap(value) {
  if (value == null) return '—'
  return value >= 0 ? `+${value}` : `${value}`
}
</script>

<style scoped>
.candidate-card {
  position: relative;
  padding: 14px 14px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--bg-elev);
  transition: border-color var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease);
}

.candidate-card:hover {
  border-color: var(--brand-soft-2);
  box-shadow: 0 4px 12px rgba(36, 78, 156, 0.08);
}

.candidate-card.is-adjusted {
  border-color: #f5dab1;
  background: var(--warn-amber-soft);
}

.candidate-card.tier--reach { border-left: 3px solid var(--tier-reach); }
.candidate-card.tier--steady { border-left: 3px solid var(--tier-steady); }
.candidate-card.tier--safe,
.candidate-card.tier--conservative { border-left: 3px solid var(--tier-safe); }

/* ── Top row ── */
.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.card-title { min-width: 0; flex: 1; }

.school-name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--ink-1);
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.program-name {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-btn {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 4px;
  color: var(--ink-4);
  cursor: pointer;
  transition: all var(--t-fast) var(--ease);
}

.remove-btn:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

/* ── Fact grid ── */
.fact-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  margin: 12px 0 0;
  padding: 10px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.fact {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 6px;
  border-right: 1px solid var(--line);
}

.fact:last-child { border-right: 0; }

.fact dt {
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--ink-3);
}

.fact dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
  line-height: 1.3;
}

.gap--up { color: var(--ok); }
.gap--down { color: var(--danger); }

/* ── AI reason — 浅蓝块引用 ── */
.ai-reason {
  position: relative;
  margin: 10px 0 0;
  padding: 8px 10px 8px 22px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--ink-2);
  background: var(--brand-soft-3);
  border-left: 2px solid var(--brand);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
}

.ai-quote {
  position: absolute;
  left: 8px;
  top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: var(--brand);
  line-height: 1;
}

/* ── Risk tags ── */
.risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 8px 0 0;
}

.risk-tag {
  font-size: 10px;
  letter-spacing: 0.04em;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--warn-soft-2);
  color: var(--warn);
  font-weight: 500;
}

/* ── Adjust notice ── */
.adjust-notice {
  margin: 8px 0 0;
  padding: 6px 10px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--warn-amber);
  background: var(--warn-amber-soft);
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  gap: 6px;
}

.adjust-notice i { font-size: 12px; }

/* ── Source meta ── */
.source-meta {
  margin-top: 8px;
  font-size: 11px;
  color: var(--ink-4);
  line-height: 1.5;
}

.source-sep { color: var(--line-strong); }

.source-link {
  color: var(--brand);
  text-decoration: none;
}

.source-link:hover { text-decoration: underline; }

/* ── Actions ── */
.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.tier-label {
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--ink-3);
  background: var(--bg-soft);
  padding: 2px 6px;
  border-radius: 3px;
}

.ask-btn {
  background: transparent;
  border: 0;
  padding: 2px 0;
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: color var(--t-fast) var(--ease);
}

.ask-btn:hover { color: var(--brand-hover); }

@media (max-width: 520px) {
  .fact-grid { grid-template-columns: repeat(2, 1fr); row-gap: 8px; }
  .fact { border-right: 0; }
}
</style>
