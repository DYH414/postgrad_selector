<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <el-button @click="$router.back()" style="margin-bottom:16px">← 返回</el-button>
      <el-card v-loading="loading">
        <div v-if="detail">
          <h4>推荐详情</h4>
          <p>预估分: {{ detail.estimatedScore || '-' }}</p>
          <p>目标地区: {{ detail.targetRegions || '不限' }}</p>
          <el-divider />
          <el-table v-if="detail.results" :data="detail.results" stripe>
            <el-table-column prop="schoolName" label="院校" />
            <el-table-column prop="programName" label="专业" />
            <el-table-column prop="matchScore" label="匹配度" width="100" />
          </el-table>
        </div>
        <el-empty v-else description="未找到推荐记录" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { getRecommendationHistoryDetail } from '@/api/recommendation'

const route = useRoute()
const loading = ref(false)
const detail = ref(null)

onMounted(async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getRecommendationHistoryDetail(id)
    detail.value = res.data || res
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
</style>
