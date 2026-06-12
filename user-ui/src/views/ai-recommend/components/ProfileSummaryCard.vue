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
          <span>本科专业</span>
          <strong>{{ profile.undergraduateMajor || '-' }}</strong>
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
          <strong>{{ riskPreferenceLabels[profile.riskPreference] || '稳中求进，冲稳保均衡' }}</strong>
        </div>
        <div class="profile-row">
          <span>地区偏好</span>
          <strong>{{ regionStrategyLabels[profile.regionStrategy] || '地区不强求，有学上更重要' }}</strong>
        </div>
        <div class="profile-row">
          <span>层次偏好</span>
          <strong>{{ schoolTierPreferenceLabels[profile.schoolTierPreference] || '不强求层次，有学上更重要' }}</strong>
        </div>
      </div>
    </div>

    <div class="profile-actions">
      <el-button type="primary" :loading="starting" :disabled="!canStart" @click="$emit('start-ai')">
        生成 AI 推荐草稿
      </el-button>
      <el-button @click="$emit('edit-profile')">编辑画像</el-button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  profile: { type: Object, required: true },
  loading: { type: Boolean, default: false },
  missingFields: { type: Array, default: () => [] },
  targetRegionsLabel: { type: String, default: '不限' },
  riskPreferenceLabels: { type: Object, default: () => ({}) },
  schoolTierPreferenceLabels: { type: Object, default: () => ({}) },
  regionStrategyLabels: { type: Object, default: () => ({}) },
  tierLabel: { type: Function, required: true },
  canStart: { type: Boolean, default: false },
  starting: { type: Boolean, default: false }
})

defineEmits(['edit-profile', 'start-ai'])
</script>

<style scoped>
.profile-card {
  padding: 18px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39, 86, 166, .07);
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.card-eyebrow {
  margin: 0 0 4px;
  color: #1769f6;
  font-size: 12px;
  line-height: 16px;
  font-weight: 900;
}

.card-head h2 {
  margin: 0;
  font-size: 18px;
  line-height: 24px;
}

.card-head p {
  margin: 5px 0 0;
  color: #71829a;
  font-size: 13px;
  line-height: 18px;
}

.profile-status {
  align-self: flex-start;
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.profile-status.good {
  color: #087443;
  background: #edfdf5;
}

.profile-status.warn {
  color: #a85d00;
  background: #fff7e8;
}

.profile-list {
  min-height: 0;
}

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

.profile-section + .profile-section {
  margin-top: 12px;
}

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
}

.profile-row span {
  color: #6d7f99;
  font-size: 13px;
  line-height: 20px;
}

.profile-row strong {
  min-width: 0;
  color: #10213f;
  font-size: 13px;
  line-height: 20px;
  text-align: right;
  word-break: break-word;
}

.profile-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-top: 18px;
}

.profile-actions :deep(.el-button) {
  margin-left: 0;
  border-radius: 7px;
  font-weight: 700;
  min-height: 38px;
}
</style>
