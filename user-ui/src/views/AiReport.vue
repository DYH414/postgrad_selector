<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <el-button @click="$router.back()" style="margin-bottom:16px">← 返回</el-button>
      <el-card v-loading="loading">
        <div v-if="report" class="report-content">
          <h2>{{ report.title || 'AI 推荐报告' }}</h2>
          <div class="report-meta" v-if="report.createdAt">
            生成时间: {{ report.createdAt }}
          </div>
          <el-divider />
          <div class="markdown-body" v-html="report.content || report.summary || '暂无内容'" />
        </div>
        <el-empty v-else-if="!loading" description="报告未找到" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { getAiReport } from '@/api/ai'

const route = useRoute()
const loading = ref(false)
const report = ref(null)

onMounted(async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getAiReport(id)
    report.value = res.data || res
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.report-content h2 { margin-top: 0; }
.report-meta { color: #909399; font-size: 13px; margin-bottom: 8px; }
.markdown-body { line-height: 1.8; }
</style>
