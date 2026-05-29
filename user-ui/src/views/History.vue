<template>
  <div class="app-page">
    <AppHeader current-page="history" />
    <div class="app-body">
      <h3>推荐历史</h3>
      <el-table :data="logs" v-loading="loading" style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="ruleVersion" label="规则版本" width="100" />
        <el-table-column prop="createdAt" label="生成时间" width="180">
          <template #default="{ row }">{{ row.createdAt }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="text" size="small" @click="$router.push('/history/' + row.id)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && logs.length === 0" style="text-align:center;padding:60px;color:#909399">
        暂无推荐记录
        <br /><br />
        <el-button type="primary" @click="$router.push('/recommend')">去生成推荐</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { useUserStore } from '@/stores/user'
import { listRecommendationHistory } from '@/api/recommendation'

const router = useRouter()
const userStore = useUserStore()

const logs = ref([])
const loading = ref(false)

function fetchList() {
  loading.value = true
  listRecommendationHistory().then(res => {
    logs.value = res.data || []
  }).finally(() => { loading.value = false })
}

function handleLogout() {
  userStore.logoutAction().then(() => { router.push('/login') })
}

onMounted(() => { fetchList() })
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }

.app-body { max-width: 1200px; margin: 24px auto; padding: 0 16px; }
.app-body h3 { margin-bottom: 16px; }
</style>
