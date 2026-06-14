<template>
  <div class="generate-section">
    <button
      class="generate-btn"
      :disabled="generating"
      @click="$emit('generate')"
    >
      <span v-if="generating" class="btn-spinner" />
      <span>{{ generating ? 'AI 生成中...' : '生成候选草稿' }}</span>
    </button>

    <p v-if="generating && progress.message" class="progress-hint">
      <span class="progress-dot" />
      {{ progress.message }}
    </p>

    <p v-else class="generate-tip">
      基于画像自动筛选候选池，AI 在每档内挑选最合适的学校。
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
.generate-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--bg-elev);
  box-shadow: 0 8px 24px rgba(36, 78, 156, 0.06);
}

.generate-btn {
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 16px;
  background: var(--brand-gradient);
  color: #fff;
  border: 0;
  border-radius: var(--r-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(23, 105, 246, 0.22);
  transition: box-shadow var(--t-fast) var(--ease), transform var(--t-fast) var(--ease);
}

.generate-btn:hover:not(:disabled) {
  box-shadow: 0 12px 24px rgba(23, 105, 246, 0.32);
  transform: translateY(-1px);
}

.generate-btn:active:not(:disabled) { transform: translateY(0); }

.generate-btn:disabled {
  background: var(--line-strong);
  box-shadow: none;
  cursor: not-allowed;
  transform: none;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 1.5px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.progress-hint {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--brand);
  padding: 0 4px;
}

.progress-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  flex-shrink: 0;
  animation: pulse 1.4s var(--ease) infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

.generate-tip {
  margin: 0;
  font-size: 11px;
  color: var(--ink-3);
  line-height: 1.55;
  padding: 0 4px;
}
</style>
