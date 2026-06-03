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
        <template v-if="compareIds.length > 0">
          <el-tag type="success" size="small">已加入对比 {{ compareIds.length }}/{{ COMPARE_MAX_ITEMS }}</el-tag>
          <el-button size="small" @click="clearAllCompare">清空对比</el-button>
        </template>
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
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button type="text" size="small" @click="openDetail(row)">详情</el-button>
            <el-button type="text" size="small" @click="addToCompare(row)">
              {{ compareIds.includes(row.programId) ? '已加入对比' : '加入对比' }}
            </el-button>
            <el-button type="text" size="small" @click="handleRemove(row)">取消收藏</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 对比面板 -->
      <div v-if="compareIds.length >= 2" class="compare-section" v-loading="compareLoading">
        <div class="compare-section-head">
          <h4>院校对比</h4>
          <span>共 {{ compareSchools.length }} 所</span>
        </div>
        <table v-if="compareSchools.length" class="compare-table">
          <thead>
            <tr>
              <th></th>
              <th v-for="s in compareSchools" :key="s.compareKey">{{ s.name }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>专业</td>
              <td v-for="s in compareSchools" :key="s.compareKey">{{ s.program }}</td>
            </tr>
            <tr>
              <td>学院</td>
              <td v-for="s in compareSchools" :key="s.compareKey">{{ s.college || '-' }}</td>
            </tr>
            <tr>
              <td>考试组合</td>
              <td v-for="s in compareSchools" :key="s.compareKey">{{ s.exam || '-' }}</td>
            </tr>
            <tr>
              <td>复试线</td>
              <td v-for="s in compareSchools" :key="s.compareKey">{{ s.score || '-' }}</td>
            </tr>
            <tr>
              <td>最低录取分</td>
              <td v-for="s in compareSchools" :key="s.compareKey">{{ s.admissionLow || '-' }}</td>
            </tr>
            <tr>
              <td>与预估分差距</td>
              <td v-for="s in compareSchools" :key="s.compareKey">
                <span class="score-diff" :class="{ positive: s.admissionLowGap >= 0, negative: s.admissionLowGap < 0 }">
                  {{ s.admissionLowGap > 0 ? '+' : '' }}{{ s.admissionLowGap }}
                </span>
              </td>
            </tr>
            <tr>
              <td>拟录取区间</td>
              <td v-for="s in compareSchools" :key="s.compareKey">{{ s.range || '-' }}</td>
            </tr>
            <tr>
              <td>招生人数</td>
              <td v-for="s in compareSchools" :key="s.compareKey">{{ s.quota || '-' }}</td>
            </tr>
            <tr>
              <td>数据完整度</td>
              <td v-for="s in compareSchools" :key="s.compareKey">
                <span class="grade" :class="'grade-' + s.confidence?.toLowerCase()">{{ s.confidence || '-' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="!compareLoading" style="text-align:center;padding:24px;color:#909399">
          正在加载对比数据...
        </div>
      </div>

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
            <span>{{ detail.basic.examCombo || '-' }}</span>
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
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { useUserStore } from '@/stores/user'
import { listFavorites, removeFavorite } from '@/api/favorites'
import { getProgramDetail, comparePrograms } from '@/api/programs'
import { COMPARE_STORAGE_KEY, COMPARE_SCORE_KEY, COMPARE_MAX_ITEMS } from '@/api/compare-constants'
import { getProfile } from '@/api/profile'

const router = useRouter()
const userStore = useUserStore()

const favorites = ref([])
const selectedRows = ref([])
const compareIds = ref([])
const profileScore = ref(null)
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

// 对比面板
const compareLoading = ref(false)
const compareSchools = ref([])

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

function loadCompareState() {
  try {
    compareIds.value = JSON.parse(localStorage.getItem(COMPARE_STORAGE_KEY) || '[]')
  } catch (_) { compareIds.value = [] }
}

function addToCompare(row) {
  const id = row.programId
  if (!id) return
  const idx = compareIds.value.indexOf(id)
  if (idx >= 0) {
    compareIds.value.splice(idx, 1)
    localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(compareIds.value))
    ElMessage.success('已从对比列表移除')
    return
  }
  if (compareIds.value.length >= COMPARE_MAX_ITEMS) {
    ElMessage.warning(`最多对比 ${COMPARE_MAX_ITEMS} 所学校`)
    return
  }
  compareIds.value.push(id)
  localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(compareIds.value))
  if (profileScore.value) {
    localStorage.setItem(COMPARE_SCORE_KEY, String(profileScore.value))
  }
  ElMessage.success(`已加入对比 (${compareIds.value.length}/${COMPARE_MAX_ITEMS})`)
}

function clearAllCompare() {
  compareIds.value = []
  compareSchools.value = []
  localStorage.removeItem(COMPARE_STORAGE_KEY)
  ElMessage.success('已清空对比列表')
}

function fetchCompare() {
  if (compareIds.value.length < 2) {
    compareSchools.value = []
    return
  }
  compareLoading.value = true
  const score = profileScore.value || localStorage.getItem(COMPARE_SCORE_KEY) || ''
  comparePrograms({ programIds: compareIds.value.join(','), estimatedScore: score })
    .then(res => {
      const items = (res.data && res.data.items) || []
      compareSchools.value = items.map(item => ({
        compareKey: `p:${item.programId || item.program_id}`,
        name: item.schoolName || '-',
        program: item.programName || '-',
        college: item.collegeName || '-',
        exam: item.examSubjectsLabel || '-',
        score: item.scoreLine ?? '-',
        admissionLow: item.admissionLow ?? '-',
        admissionLowGap: item.admissionLowGap ?? 0,
        range: item.admissionRangeLabel || '-',
        quota: item.planCount || '-',
        confidence: item.dataCompleteness || '-'
      }))
    })
    .catch(() => {
      compareSchools.value = []
      ElMessage.error('加载对比数据失败')
    })
    .finally(() => { compareLoading.value = false })
}

// 监听对比列表变化，自动刷新对比面板
watch(compareIds, () => {
  fetchCompare()
}, { deep: true })

function compareSelected() {
  const ids = selectedRows.value.map(row => row.programId).filter(Boolean)
  if (ids.length < 2) {
    ElMessage.warning('至少选择 2 个项目进行对比')
    return
  }
  ids.forEach(id => { if (!compareIds.value.includes(id)) compareIds.value.push(id) })
  localStorage.setItem(COMPARE_STORAGE_KEY, JSON.stringify(compareIds.value))
  if (profileScore.value) {
    localStorage.setItem(COMPARE_SCORE_KEY, String(profileScore.value))
  }
  ElMessage.success(`已加入 ${ids.length} 所学校到对比列表`)
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

onMounted(() => {
  fetchList()
  loadCompareState()
  getProfile().then(res => {
    if (res.data?.estimatedScore) profileScore.value = res.data.estimatedScore
  }).catch(() => {})
  // 初始加载时如果有对比项，自动获取对比数据
  if (compareIds.value.length >= 2) fetchCompare()
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }

.app-body { max-width: 1200px; margin: 24px auto; padding: 0 16px; }
.app-body h3 { margin-bottom: 16px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; color: #909399; }

/* 对比面板 */
.compare-section {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #dce9fb;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(45,87,143,.06);
}
.compare-section-head {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 14px;
}
.compare-section-head h4 { margin: 0; font-size: 18px; color: #10203f; }
.compare-section-head span { color: #8793a7; font-size: 13px; }
.compare-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.compare-table th, .compare-table td {
  padding: 10px 14px; border: 1px solid #e5edf8; text-align: center;
}
.compare-table thead th { background: #f8fbff; font-weight: 700; color: #10203f; }
.compare-table tbody td:first-child {
  background: #f8fbff; font-weight: 600; color: #65758d; text-align: left; white-space: nowrap;
}

.score-diff { font-weight: 700; }
.score-diff.positive { color: #16a34a; }
.score-diff.negative { color: #dc2626; }

.grade { padding: 2px 8px; border-radius: 4px; font-weight: 700; font-size: 12px; }
.grade-a { background: #dcfce7; color: #16a34a; }
.grade-b { background: #fef3c7; color: #d97706; }
.grade-c { background: #fee2e2; color: #dc2626; }

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
