<template>
  <section class="profile-card">
    <header class="card-head">
      <p class="card-eyebrow">画像</p>
      <h2 class="card-title">决策输入</h2>
    </header>

    <div v-loading="loading" class="card-body">
      <!-- 分数块 — 浅蓝渐变 + 大数字 -->
      <div class="score-block">
        <div class="score-block-top">
          <span class="score-label">预计初试总分</span>
          <span class="score-badge">分</span>
        </div>
        <div class="score-value t-num">
          {{ profile.estimatedScore || '—' }}
        </div>
        <p class="score-hint">用于判断复试线区间与冲稳保档位</p>
      </div>

      <dl class="kv-list">
        <div class="kv">
          <dt>本科</dt>
          <dd>{{ tierLabel(profile.undergradTier) }}</dd>
        </div>
        <div class="kv">
          <dt>跨考</dt>
          <dd>{{ profile.isCrossMajor ? '是' : '否' }}</dd>
        </div>
        <div class="kv">
          <dt>地区</dt>
          <dd class="dd-ellipsis" :title="targetRegionsLabel">{{ targetRegionsLabel }}</dd>
        </div>
        <div class="kv">
          <dt>安全</dt>
          <dd>{{ riskLabel(profile.riskPreference) }}</dd>
        </div>
        <div class="kv">
          <dt>层次</dt>
          <dd class="dd-ellipsis" :title="schoolTierLabel(profile.schoolTierPreference)">{{ schoolTierLabel(profile.schoolTierPreference) }}</dd>
        </div>
      </dl>

      <div v-if="missingFields.length" class="warn-line">
        <i class="el-icon-warning-outline" />
        还缺 {{ missingFields.length }} 项关键信息
      </div>
    </div>

    <button class="card-link" @click="$emit('edit')">
      编辑画像
      <span class="arrow">→</span>
    </button>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  profile: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  missingFields: { type: Array, default: () => [] }
})

defineEmits(['edit'])

const targetRegionsLabel = computed(() => {
  const v = props.profile.targetRegions
  if (!v || v === '不限' || v === '[]') return '不限'
  try {
    const arr = JSON.parse(v)
    return Array.isArray(arr) && arr.length ? arr.join('、') : '不限'
  } catch { return v }
})

function tierLabel(v) {
  if (!v) return '双非'
  const map = {
    '985': '985',
    '211': '211',
    'DOUBLE_FIRST': '双一流',
    'PUBLIC_REGULAR': '普通本科',
    'PUBLIC_FIRST': '普通一本',
    'PUBLIC_SECOND': '普通二本',
    'PRIVATE': '民办本科',
    'PRIVATE_REGULAR': '民办本科',
    'JUNIOR_COLLEGE': '专科',
    'OTHER': '其他'
  }
  return map[v] || v
}

function riskLabel(v) {
  const map = {
    safe_first: '稳妥优先',
    balanced: '适度冲刺',
    reach_first: '接受压线',
    conservative: '稳妥优先',
    aggressive: '接受压线'
  }
  return map[v] || '适度冲刺'
}

function schoolTierLabel(v) {
  const map = {
    tier_priority: '学校层次优先',
    must_211_or_better: '学校层次优先',
    prefer_211_or_better: '211/双一流优先',
    no_strict_requirement: '层次不限制'
  }
  return map[v] || '层次不限制'
}
</script>

<style scoped>
.profile-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--bg-elev);
  box-shadow: 0 8px 24px rgba(36, 78, 156, 0.06);
  padding: 16px 16px 12px;
  position: relative;
  overflow: hidden;
}

.profile-card::before {
  /* 右上角装饰光斑 */
  content: "";
  position: absolute;
  top: -30px;
  right: -30px;
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, rgba(23, 105, 246, 0.1) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.card-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 14px;
  position: relative;
}

.card-eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--brand);
  letter-spacing: 0.02em;
}

.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-1);
  letter-spacing: -0.01em;
}

.card-body { flex: 1; min-height: 0; position: relative; }

/* Score block — visual focal point */
.score-block {
  padding: 14px 14px 12px;
  border: 1px solid var(--brand-soft-2);
  border-radius: var(--r-md);
  background:
    linear-gradient(135deg, #f0f6ff 0%, #eaf2ff 50%, #f7eeff 100%);
  margin-bottom: 12px;
  position: relative;
  overflow: hidden;
}

.score-block::after {
  /* 右上角斜纹装饰 */
  content: "";
  position: absolute;
  top: 0;
  right: 0;
  width: 60px;
  height: 60px;
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0,
      transparent 6px,
      rgba(23, 105, 246, 0.06) 6px,
      rgba(23, 105, 246, 0.06) 7px
    );
  pointer-events: none;
}

.score-block-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.score-label {
  font-size: 11px;
  color: var(--ink-3);
  font-weight: 500;
}

.score-badge {
  font-size: 10px;
  color: var(--brand);
  background: var(--bg-elev);
  border: 1px solid var(--brand-soft-2);
  padding: 1px 6px;
  border-radius: 999px;
  font-weight: 600;
}

.score-value {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--brand);
  margin: 6px 0 6px;
}

.score-hint {
  margin: 0;
  font-size: 11px;
  color: var(--ink-3);
  line-height: 1.5;
}

/* KV list */
.kv-list {
  margin: 0 0 10px;
  display: flex;
  flex-direction: column;
}

.kv {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--line);
}

.kv:last-child { border-bottom: 0; }

.kv dt {
  font-size: 12px;
  color: var(--ink-3);
  font-weight: 400;
  flex-shrink: 0;
}

.kv dd {
  margin: 0;
  font-size: 12px;
  color: var(--ink-1);
  font-weight: 600;
  text-align: right;
  word-break: break-word;
}

.dd-ellipsis {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.warn-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--warn);
  padding: 6px 10px;
  background: var(--warn-soft-2);
  border-radius: var(--r-sm);
  margin-bottom: 4px;
  border: 1px solid #f5dab1;
}

.warn-line i { font-size: 12px; }

.card-link {
  margin-top: 8px;
  padding: 9px 4px 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: transparent;
  border: 0;
  border-top: 1px dashed var(--line);
  border-radius: 0;
  font-size: 12px;
  color: var(--ink-3);
  font-weight: 500;
  cursor: pointer;
  transition: color var(--t-fast) var(--ease);
}

.card-link:hover { color: var(--brand); }

.card-link .arrow {
  transition: transform var(--t-fast) var(--ease);
}

.card-link:hover .arrow { transform: translateX(2px); }
</style>
