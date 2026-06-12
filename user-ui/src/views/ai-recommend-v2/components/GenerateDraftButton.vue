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
      {{ generating ? '正在生成...' : '生成 AI 推荐草稿' }}
    </el-button>

    <!-- 进度提示 -->
    <div v-if="generating && progress.message" class="progress-hint">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ progress.message }}</span>
    </div>
  </div>
</template>

<script setup>
import { Loading } from '@element-plus/icons-vue'

defineProps({
  generating: { type: Boolean, default: false },
  progress: {
    type: Object,
    default: () => ({ phase: '', message: '' })
  }
})

defineEmits(['generate'])
</script>

<style scoped>
.generate-section {
  margin: 16px 0;
  text-align: center;
}
.generate-btn {
  width: 100%;
}
.progress-hint {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: #666;
}
</style>
