<template>
  <div class="app-page">
    <AppHeader current-page="favorites" />
    <div class="app-body">
      <div class="page-title"><h3>我的收藏</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && favorites.length === 0" description="暂无收藏" />
        <el-table v-else :data="favorites" stripe>
          <el-table-column prop="schoolName" label="院校" />
          <el-table-column prop="programName" label="专业" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="handleRemove(row)">取消收藏</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { listFavorites, removeFavorite } from '@/api/favorites'

const loading = ref(false)
const favorites = ref([])

async function fetchFavorites() {
  loading.value = true
  try {
    const res = await listFavorites()
    favorites.value = res.data || res.rows || []
  } catch (e) {
    ElMessage.error('加载收藏失败')
  } finally {
    loading.value = false
  }
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm('确定取消收藏？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await removeFavorite(row.programId)
    favorites.value = favorites.value.filter(f => f.programId !== row.programId)
    ElMessage.success('已取消收藏')
  } catch (e) { /* cancelled or error */ }
}

onMounted(fetchFavorites)
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
