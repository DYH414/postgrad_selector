<template>
  <section class="profile-card">
    <div class="card-head">
      <div>
        <h2>当前画像</h2>
        <p v-if="missingFields.length === 0">画像完整，可生成推荐。</p>
        <p v-else>还缺少 {{ missingFields.length }} 项关键信息。</p>
      </div>
      <span :class="['profile-status', missingFields.length ? 'warn' : 'good']">
        {{ missingFields.length ? '待完善' : '可推荐' }}
      </span>
    </div>

    <div v-loading="loading" class="profile-list">
      <div class="profile-row strong">
        <span>预计初试总分</span>
        <strong>{{ profile.estimatedScore || '-' }}</strong>
      </div>
      <div class="profile-row">
        <span>本科层次</span>
        <strong>{{ tierLabel(profile.undergradTier) }}</strong>
      </div>
      <div class="profile-row">
        <span>跨考情况</span>
        <strong>{{ profile.isCrossMajor ? '跨考' : '非跨考' }}</strong>
      </div>
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
  const map = { '985': '985', '211': '211', 'DOUBLE_FIRST': '双一流' }
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
  padding: 20px;
  border: 1px solid rgba(215,227,245,.9);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(42,84,153,.08);
}
.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
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
.profile-status.good { background: rgba(103,194,58,.12); color: #67c23a; }
.profile-status.warn { background: rgba(230,162,60,.12); color: #e6a23c; }
.profile-list { display: flex; flex-direction: column; gap: 2px; margin-bottom: 16px; }
.profile-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid #f0f4fa;
  font-size: 13px;
}
.profile-row:last-child { border-bottom: none; }
.profile-row span { color: #71829a; }
.profile-row strong { font-weight: 600; color: #303133; }
.profile-row.strong strong { color: #409eff; font-size: 15px; }
.profile-actions { text-align: right; }
</style>
