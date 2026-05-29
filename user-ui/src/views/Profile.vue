<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <div class="page-title">
        <h3>我的考研画像</h3>
        <div>
          <el-button v-if="!editing" type="primary" @click="startEdit">编辑画像</el-button>
          <el-button v-if="editing" @click="editing = false">取消</el-button>
        </div>
      </div>

      <el-card v-if="!editing" v-loading="loading">
        <div v-if="isEmpty" class="empty-state">
          <p>还没有填写考研画像</p>
          <el-button type="primary" @click="startEdit">立即填写</el-button>
        </div>
        <div v-else class="profile-detail">
          <div class="row"><span class="label">预估总分</span><span class="value hl">{{ profile.estimatedScore || '-' }} 分</span></div>
          <el-divider />
          <div class="row"><span class="label">目标地区</span><span class="value">{{ regionText }}</span></div>
          <el-divider />
          <div class="row"><span class="label">本科层次</span><span class="value">{{ tierLabel(profile.undergradTier) }}</span></div>
          <el-divider />
          <div class="row"><span class="label">本科专业</span><span class="value">{{ profile.undergraduateMajor || '-' }}</span></div>
          <el-divider />
          <div class="row"><span class="label">跨考</span><span class="value">{{ profile.isCrossMajor ? '是' : '否' }}</span></div>
          <el-divider />
          <div class="row"><span class="label">学位类型</span><span class="value">{{ profile.acceptAcademic ? '接受学硕' : '仅专硕' }}</span></div>
        </div>
      </el-card>

      <el-card v-if="editing">
        <el-form ref="formRef" :model="editForm" label-width="120px" style="max-width:500px">
          <el-form-item label="预估总分" required>
            <el-input-number v-model="editForm.estimatedScore" :min="100" :max="500" />
            <span style="margin-left:8px;color:#909399">满分 500</span>
          </el-form-item>
          <el-form-item label="目标省份">
            <el-select v-model="editForm.targetRegions" multiple filterable placeholder="不限" style="width:100%">
              <el-option v-for="p in provinces" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-form-item label="接受学硕">
            <el-switch v-model="editForm.acceptAcademic" />
          </el-form-item>
          <el-divider>以下选填</el-divider>
          <el-form-item label="本科层次">
            <el-select v-model="editForm.undergradTier" clearable placeholder="请选择" style="width:100%">
              <el-option label="985" value="985" />
              <el-option label="211" value="211" />
              <el-option label="双一流" value="DOUBLE_FIRST" />
              <el-option label="普通一本" value="PUBLIC_REGULAR" />
              <el-option label="二本/民办" value="PRIVATE" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="本科专业">
            <el-input v-model="editForm.undergraduateMajor" placeholder="如 计算机科学与技术" />
          </el-form-item>
          <el-form-item label="跨考">
            <el-switch v-model="editForm.isCrossMajor" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { getProfile, saveProfile } from '@/api/profile'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const Tiers = { '985':'985', '211':'211', 'DOUBLE_FIRST':'双一流', 'PUBLIC_REGULAR':'普通一本', 'PRIVATE':'二本/民办', 'OTHER':'其他' }
const provinces = ['北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江','上海','江苏','浙江','安徽','福建','江西','山东','河南','湖北','湖南','广东','广西','海南','重庆','四川','贵州','云南','西藏','陕西','甘肃','青海','宁夏','新疆']

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const formRef = ref(null)
const profile = reactive({
  estimatedScore: null, targetRegions: [], undergradTier: null,
  undergraduateMajor: '', isCrossMajor: false, acceptAcademic: false
})
const editForm = reactive(JSON.parse(JSON.stringify(profile)))

const isEmpty = computed(() => !profile.estimatedScore)
const regionText = computed(() => {
  const r = profile.targetRegions
  return (r && r.length) ? r.join('、') : '不限'
})

function tierLabel(v) { return Tiers[v] || v || '-' }

async function fetchProfile() {
  loading.value = true
  try {
    const res = await getProfile()
    if (res.data && res.data.estimatedScore) {
      const p = res.data
      let regions = p.targetRegions
      if (typeof regions === 'string') {
        try { regions = JSON.parse(regions) } catch (e) { regions = [] }
      }
      Object.assign(profile, {
        estimatedScore: p.estimatedScore,
        targetRegions: regions || [],
        acceptAcademic: p.acceptAcademic === 1 || p.acceptAcademic === true,
        undergradTier: p.undergradTier,
        undergraduateMajor: p.undergraduateMajor || '',
        isCrossMajor: p.isCrossMajor === 1 || p.isCrossMajor === true
      })
    }
  } finally {
    loading.value = false
  }
}

function startEdit() {
  Object.assign(editForm, JSON.parse(JSON.stringify(profile)))
  editing.value = true
}

async function handleSave() {
  if (!editForm.estimatedScore) {
    ElMessage.warning('请输入预估总分')
    return
  }
  saving.value = true
  try {
    const data = {
      estimatedScore: editForm.estimatedScore,
      targetRegions: JSON.stringify(editForm.targetRegions),
      acceptPartTime: false,
      acceptAcademic: editForm.acceptAcademic,
      undergradTier: editForm.undergradTier,
      undergraduateMajor: editForm.undergraduateMajor,
      isCrossMajor: editForm.isCrossMajor
    }
    await saveProfile(data)
    ElMessage.success('保存成功')
    Object.assign(profile, JSON.parse(JSON.stringify(editForm)))
    userStore.setProfile(data)
    editing.value = false
  } finally {
    saving.value = false
  }
}

onMounted(fetchProfile)
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 600px; margin: 24px auto; padding: 0 16px; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h3 { margin: 0; }
.empty-state { text-align: center; padding: 60px 0; color: #909399; }
.row { display: flex; align-items: center; padding: 4px 0; }
.row .label { width: 100px; color: #909399; font-size: 14px; flex-shrink: 0; }
.row .value { color: #303133; font-size: 14px; }
.row .value.hl { font-size: 20px; font-weight: 700; color: #409eff; }
</style>
