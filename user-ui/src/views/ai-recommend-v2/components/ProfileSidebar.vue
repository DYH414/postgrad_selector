<template>
  <section class="profile-card">
    <div class="card-head">
      <div>
        <p class="card-eyebrow">决策输入</p>
        <h2>当前画像</h2>
        <p v-if="missingFields.length === 0">画像完整，可生成推荐草稿。</p>
        <p v-else>还缺少 {{ missingFields.length }} 项关键信息。</p>
      </div>
      <span :class="['profile-status', missingFields.length ? 'warn' : 'good']">
        {{ missingFields.length ? '待完善' : '可推荐' }}
      </span>
    </div>

    <div v-loading="loading" class="profile-list">
      <div class="score-panel">
        <span>预计初试总分</span>
        <strong>{{ profile.estimatedScore || '-' }}</strong>
        <em>用于判断复试线区间与冲稳保档位</em>
      </div>

      <div class="profile-section">
        <h3>基础条件</h3>
        <div class="profile-row">
          <span>本科层次</span>
          <strong>{{ tierLabel(profile.undergradTier) }}</strong>
        </div>
        <div class="profile-row">
          <span>跨考情况</span>
          <strong>{{ profile.isCrossMajor ? '跨考' : '非跨考' }}</strong>
        </div>
      </div>

      <div class="profile-section">
        <h3>偏好策略</h3>
        <div class="profile-row">
          <span>目标地区</span>
          <strong>{{ targetRegionsLabel }}</strong>
        </div>
        <div class="profile-row">
          <span>整体策略</span>
          <strong>{{ riskLabel(profile.riskPreference) }}</strong>
        </div>
        <div class="profile-row">
          <span>层次偏好</span>
          <strong>{{ schoolTierLabel(profile.schoolTierPreference) }}</strong>
        </div>
      </div>
    </div>

    <div class="profile-actions">
      <el-button size="small" @click="$emit('edit')">编辑画像</el-button>
    </div>
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
  const map = { safe_first: '稳妥优先', reach_first: '冲刺优先', balanced: '均衡策略' }
  return map[v] || '均衡策略'
}

function schoolTierLabel(v) {
  const map = {
    must_211_or_better: '强烈倾向 211/双一流',
    prefer_211_or_better: '优先 211/双一流',
    no_strict_requirement: '不强求层次'
  }
  return map[v] || '不强求层次'
}
</script>

<style scoped>
.profile-card {
  padding: 18px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39,86,166,.07);
}
.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.card-eyebrow { margin: 0 0 4px; color: #1769f6; font-size: 12px; line-height: 16px; font-weight: 900; }
.card-head h2 { margin: 0; font-size: 18px; line-height: 24px; }
.card-head p { margin: 5px 0 0; color: #71829a; font-size: 13px; }
.profile-status {
  align-self: flex-start;
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}
.profile-status.good { background: #edfdf5; color: #087443; }
.profile-status.warn { background: rgba(230,162,60,.12); color: #e6a23c; }
.profile-list { margin-bottom: 16px; }
.score-panel {
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #d7e6fb;
  border-radius: 8px;
  background: #f6f9ff;
}
.score-panel span,
.score-panel em {
  display: block;
  color: #6d7f99;
  font-size: 12px;
  line-height: 16px;
  font-style: normal;
}
.score-panel strong {
  display: block;
  margin: 5px 0 3px;
  color: #1769f6;
  font-size: 28px;
  line-height: 34px;
  font-weight: 900;
}
.profile-section {
  padding-top: 12px;
  border-top: 1px solid #edf2f9;
}
.profile-section + .profile-section { margin-top: 12px; }
.profile-section h3 {
  margin: 0 0 6px;
  color: #10213f;
  font-size: 13px;
  line-height: 18px;
  font-weight: 900;
}
.profile-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 12px;
  padding: 7px 0;
  font-size: 13px;
}
.profile-row span { color: #71829a; }
.profile-row strong { min-width: 0; font-weight: 700; color: #10213f; text-align: right; word-break: break-word; }
.profile-actions { text-align: right; }
.profile-actions :deep(.el-button) { border-radius: 7px; font-weight: 700; }
</style>
