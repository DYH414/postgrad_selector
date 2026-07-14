<template>
  <div class="app-page">
    <AppHeader current-page="home" />
    <div class="home-body">
      <h2>用数据择校</h2>
      <p>基于历年录取分数、招生计划和数据完整度，筛选目标院校</p>
      <el-button type="primary" size="large" @click="$router.push('/recommend')">开始筛选</el-button>
    </div>
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.home-body {
  max-width: 600px; margin: 80px auto; text-align: center; padding: 0 20px;
}
.home-body h2 { color: #303133; margin-bottom: 12px; }
.home-body p { color: #909399; margin-bottom: 32px; font-size: 16px; }
</style>
