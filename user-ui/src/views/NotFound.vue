<template>
  <div class="not-found">
    <div class="nf-code">404</div>
    <h1>页面未找到</h1>
    <p>你访问的页面不存在或已被移除</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
  color: var(--ink-2, #333);
}
.nf-code {
  font-size: 96px;
  font-weight: 800;
  color: var(--brand, #1769f6);
  letter-spacing: -0.04em;
  line-height: 1;
}
.not-found h1 { margin: 16px 0 8px; font-size: 22px; }
.not-found p { margin-bottom: 24px; color: var(--ink-4, #999); }
</style>
