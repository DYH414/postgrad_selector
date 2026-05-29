<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <div class="page-title"><h3>推荐历史</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && history.length === 0" description="暂无推荐记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in history" :key="item.id" :timestamp="item.createdAt"
            placement="top">
            <el-card shadow="hover">
              <p>预估分: {{ item.estimatedScore || '-' }} | 目标地区: {{ item.targetRegions || '不限' }}</p>
              <el-button size="small" type="primary" @click="$router.push('/history/' + item.id)">查看详情</el-button>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { listRecommendationHistory } from '@/api/recommendation'

const loading = ref(false)
const history = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await listRecommendationHistory()
    history.value = res.data || res.rows || []
  } catch (e) {
    ElMessage.error('加载历史失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
