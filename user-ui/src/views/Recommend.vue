<template>
  <div class="app-page">
    <AppHeader current-page="recommend" />
    <div class="app-body">
      <div class="page-title"><h3>智能择校推荐</h3></div>
      <el-card>
        <el-form ref="formRef" :model="form" label-width="120px" style="max-width:500px">
          <el-form-item label="预估总分" required>
            <el-input-number v-model="form.estimatedScore" :min="100" :max="500" />
          </el-form-item>
          <el-form-item label="目标省份">
            <el-select v-model="form.targetRegions" multiple filterable placeholder="不限" style="width:100%">
              <el-option v-for="p in provinces" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-form-item label="接受学硕">
            <el-switch v-model="form.acceptAcademic" />
          </el-form-item>
          <el-form-item label="接受联培">
            <el-switch v-model="form.acceptJoint" />
          </el-form-item>
          <el-form-item label="接受调剂">
            <el-switch v-model="form.acceptTransfer" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleGenerate">生成推荐</el-button>
            <el-button @click="loadFromProfile">从画像加载</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <div v-if="result" class="result-section" style="margin-top:20px">
        <h4>推荐结果</h4>
        <el-table :data="result.rows || []" stripe>
          <el-table-column prop="schoolName" label="院校" />
          <el-table-column prop="programName" label="专业" />
          <el-table-column prop="matchScore" label="匹配度" width="100">
            <template #default="{ row }">{{ row.matchScore }}%</template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" @click="toggleFavorite(row)">
                {{ row.isFavorited ? '取消收藏' : '收藏' }}
              </el-button>
              <el-button size="small" type="primary" @click="$router.push('/history/' + row.recommendationId)">
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="result.total > 0"
          style="margin-top:16px;text-align:right"
          :current-page="pageNum" :page-size="pageSize" :total="result.total"
          layout="total, prev, pager, next" @current-change="fetchPage" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { getRecommendationOptions, generateRecommendation, listRecommendationHistory } from '@/api/recommendation'
import { getProfile } from '@/api/profile'
import { addFavorite, removeFavorite } from '@/api/favorites'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const provinces = ['北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江','上海','江苏','浙江','安徽','福建','江西','山东','河南','湖北','湖南','广东','广西','海南','重庆','四川','贵州','云南','西藏','陕西','甘肃','青海','宁夏','新疆']

const formRef = ref(null)
const loading = ref(false)
const result = ref(null)
const pageNum = ref(1)
const pageSize = ref(20)

const form = reactive({
  estimatedScore: null,
  targetRegions: [],
  acceptAcademic: false,
  acceptJoint: false,
  acceptTransfer: false
})

async function loadFromProfile() {
  try {
    const res = await getProfile()
    if (res.data) {
      const p = res.data
      form.estimatedScore = p.estimatedScore || null
      form.acceptAcademic = p.acceptAcademic === 1 || p.acceptAcademic === true
      let regions = p.targetRegions
      if (typeof regions === 'string') {
        try { regions = JSON.parse(regions) } catch (e) { regions = [] }
      }
      form.targetRegions = regions || []
    }
  } catch (e) {
    ElMessage.error('加载画像失败')
  }
}

async function handleGenerate() {
  if (!form.estimatedScore) {
    ElMessage.warning('请输入预估总分')
    return
  }
  loading.value = true
  try {
    const data = {
      estimatedScore: form.estimatedScore,
      targetRegions: form.targetRegions,
      acceptAcademic: form.acceptAcademic,
      acceptJoint: form.acceptJoint,
      acceptTransfer: form.acceptTransfer
    }
    const res = await generateRecommendation(data)
    pageNum.value = 1
    result.value = res.data || res
  } catch (e) {
    ElMessage.error('生成推荐失败')
  } finally {
    loading.value = false
  }
}

async function fetchPage(page) {
  pageNum.value = page
}

async function toggleFavorite(row) {
  try {
    if (row.isFavorited) {
      await removeFavorite(row.programId)
      row.isFavorited = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(row.programId)
      row.isFavorited = true
      ElMessage.success('已收藏')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(async () => {
  try {
    await getRecommendationOptions()
  } catch (e) { /* options may be empty */ }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 900px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
.result-section h4 { margin-bottom: 12px; }
</style>
