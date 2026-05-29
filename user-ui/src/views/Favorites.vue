<template>
  <div class="app-page">
    <AppHeader current-page="favorites" />
    <div class="app-body">
      <h3>我的收藏</h3>
      <div class="toolbar">
        <el-button type="primary" size="small" :disabled="selectedRows.length < 2" @click="compareSelected">
          对比所选
        </el-button>
        <span>已选择 {{ selectedRows.length }} 项</span>
      </div>
      <el-table :data="favorites" v-loading="loading" style="width:100%" @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="schoolName" label="学校" />
        <el-table-column prop="tier" label="层次" width="80" />
        <el-table-column prop="collegeName" label="学院" />
        <el-table-column prop="programName" label="专业" />
        <el-table-column prop="programCode" label="专业代码" width="100" />
        <el-table-column prop="degreeType" label="学位" width="70">
          <template #default="{ row }">
            {{ row.degreeType === 'professional' ? '专硕' : '学硕' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="text" size="small" @click="openDetail(row)">详情</el-button>
            <el-button type="text" size="small" @click="handleRemove(row)">取消收藏</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && favorites.length === 0" style="text-align:center;padding:60px;color:#909399">
        暂无收藏，去推荐结果页收藏意向专业吧
        <br /><br />
        <el-button type="primary" @click="$router.push('/recommend')">去生成推荐</el-button>
      </div>
    </div>
    <el-drawer title="收藏专业详情" :visible.sync="detailVisible" size="520px" append-to-body>
      <div class="detail-drawer" v-loading="detailLoading">
        <template v-if="detail">
          <h2>{{ detail.basic.schoolName }}</h2>
          <p>{{ detail.basic.collegeName }} / {{ detail.basic.programName }}</p>
          <div class="detail-tags">
            <span>{{ detail.basic.examCombo }}：{{ detail.basic.examSubjectsLabel }}</span>
            <span>{{ detail.basic.studyModeLabel }}</span>
            <span>{{ detail.dataCompleteness.label }}</span>
          </div>
          <div class="detail-score-grid">
            <div><small>复试线</small><strong>{{ detail.recommendationOverview.scoreLine }}</strong></div>
            <div><small>拟录取区间</small><strong>{{ detail.recommendationOverview.admissionRangeLabel || '-' }}</strong></div>
          </div>
          <div class="drawer-warning">
            <p v-for="warning in detail.riskWarnings" :key="warning">· {{ warning }}</p>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { useUserStore } from '@/stores/user'
import { listFavorites, removeFavorite } from '@/api/favorites'
import { getProgramDetail } from '@/api/programs'

const router = useRouter()
const userStore = useUserStore()

const favorites = ref([])
const selectedRows = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

function fetchList() {
  loading.value = true
  listFavorites().then(res => {
    favorites.value = res.data || []
  }).finally(() => { loading.value = false })
}

function handleRemove(row) {
  removeFavorite(row.programId).then(() => {
    ElMessage.success('已取消收藏')
    favorites.value = favorites.value.filter(f => f.programId !== row.programId)
  })
}

function compareSelected() {
  const ids = selectedRows.value.map(row => row.programId).filter(Boolean)
  if (ids.length < 2) {
    ElMessage.warning('至少选择 2 个项目进行对比')
    return
  }
  router.push({
    path: '/results',
    query: { tab: 'compare', programIds: ids.join(',') }
  })
}

function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  getProgramDetail(row.programId).then(res => {
    detail.value = res.data
  }).finally(() => { detailLoading.value = false })
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
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; color: #909399; }
.detail-drawer { padding: 0 24px 28px; }
.detail-drawer h2 { margin: 0 0 6px; font-size: 22px; }
.detail-drawer p { margin: 0 0 12px; color: #64748b; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0 18px; }
.detail-tags span { padding: 5px 9px; border-radius: 5px; color: #1769f6; background: #eef4ff; font-weight: 700; font-size: 12px; }
.detail-score-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 16px; }
.detail-score-grid div { border: 1px solid #e5edf8; border-radius: 8px; padding: 12px; background: #fbfdff; }
.detail-score-grid small { display: block; color: #7a879a; margin-bottom: 4px; }
.detail-score-grid strong { font-size: 22px; }
.drawer-warning { border: 1px solid #ffd7a8; border-radius: 8px; padding: 12px; background: #fff8e8; }
.drawer-warning p { color: #9a4d00; margin: 4px 0; }
</style>
