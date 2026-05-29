<template>
  <div class="app-page">
    <AppHeader current-page="results" />
    <div class="app-body">
      <div class="page-title"><h3>筛选结果</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && programs.length === 0" description="暂无推荐结果，请先生成推荐" />
        <el-table v-else :data="programs" stripe>
          <el-table-column prop="schoolName" label="院校" width="160" />
          <el-table-column prop="programName" label="专业" min-width="200" />
          <el-table-column prop="matchScore" label="匹配度" width="100" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="toggleFavorite(row)">
                {{ row.isFavorited ? '取消收藏' : '收藏' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { generateRecommendation } from '@/api/recommendation'
import { addFavorite, removeFavorite } from '@/api/favorites'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const programs = ref([])

async function loadResults() {
  loading.value = true
  try {
    const res = await generateRecommendation({ estimatedScore: 300 })
    programs.value = res.data?.rows || res.rows || []
  } catch (e) {
    ElMessage.error('加载推荐结果失败')
  } finally {
    loading.value = false
  }
}

async function toggleFavorite(row) {
  try {
    if (row.isFavorited) {
      await removeFavorite(row.programId)
      row.isFavorited = false
    } else {
      await addFavorite(row.programId)
      row.isFavorited = true
    }
  } catch (e) { /* ignore */ }
}

onMounted(loadResults)
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 900px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
