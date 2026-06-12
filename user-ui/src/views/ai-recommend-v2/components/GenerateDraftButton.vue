<template>
  <div class="generate-section">
    <el-button
      type="primary"
      size="large"
      :loading="generating"
      :disabled="generating"
      class="generate-btn"
      @click="$emit('generate')"
    >
      {{ generating ? 'AI 正在生成...' : '生成 AI 推荐草稿' }}
    </el-button>

    <div v-if="generating && progress.message" class="progress-hint">
      <i class="el-icon-loading"></i>
      <span>{{ progress.message }}</span>
    </div>

    <p v-if="!generating" class="generate-tip">
      系统将基于画像自动筛选候选池，由 AI 在每档内挑选最合适的学校。
    </p>
  </div>
</template>

<script setup>
defineProps({
  generating: { type: Boolean, default: false },
  progress: { type: Object, default: () => ({ phase: '', message: '' }) }
})
defineEmits(['generate'])
</script>

<style scoped>
.generate-section { text-align: center; }
.generate-btn {
  width: 100%;
  font-size: 15px;
  font-weight: 600;
  height: 44px;
}
.progress-hint {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: #71829a;
}
.generate-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #a8b2c1;
  line-height: 18px;
}
</style>
