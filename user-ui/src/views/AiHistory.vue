<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <div class="page-title"><h3>AI 推荐记录</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && reports.length === 0" description="暂无 AI 推荐记录" />
        <div v-else>
          <el-table :data="reports" stripe>
            <el-table-column prop="title" label="报告标题" />
            <el-table-column prop="createdAt" label="生成时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="$router.push('/ai-report/' + row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="total > 0"
            style="margin-top:16px;text-align:right"
            :current-page="pageNum" :page-size="pageSize" :total="total"
            layout="total, prev, pager, next" @current-change="fetchPage" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { getAiReports } from '@/api/ai'

const loading = ref(false)
const reports = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

async function fetchReports(page) {
  loading.value = true
  try {
    const res = await getAiReports(page)
    reports.value = res.data?.rows || res.rows || res.data || []
    total.value = res.total || 0
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

function fetchPage(page) {
  pageNum.value = page
  fetchReports(page)
}

onMounted(() => fetchReports(1))
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
