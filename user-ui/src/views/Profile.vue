<template>
  <div class="app-page">
    <AppHeader current-page="profile" />
    <div class="app-body">
      <div class="page-title">
        <h3>我的考研画像</h3>
        <el-button v-if="!editing" type="primary" size="small" icon="el-icon-edit"
          @click="startEdit">编辑画像</el-button>
        <el-button v-if="editing" size="small" @click="editing = false">取消编辑</el-button>
      </div>

      <!-- 查看模式 -->
      <el-card v-if="!editing" v-loading="loading" class="profile-view-card">
        <div v-if="isEmpty" class="empty-profile">
          <i class="el-icon-user"></i>
          <p>还没有填写考研画像</p>
          <el-button type="primary" @click="startEdit">立即填写</el-button>
        </div>
        <div v-else class="profile-detail">
          <div class="profile-row">
            <span class="label">预估总分</span>
            <span class="value highlight">{{ profile.estimatedScore || '-' }} 分</span>
          </div>
          <el-divider />
          <div class="profile-row">
            <span class="label">目标地区</span>
            <span class="value">{{ regionText }}</span>
          </div>
          <el-divider />
          <div class="profile-row">
            <span class="label">本科层次</span>
            <span class="value">{{ tierLabel(profile.undergradTier) }}</span>
          </div>
          <el-divider />
          <div class="profile-row">
            <span class="label">本科专业</span>
            <span class="value">{{ profile.undergraduateMajor || '-' }}</span>
          </div>
          <el-divider />
          <div class="profile-row">
            <span class="label">跨考</span>
            <span class="value">{{ profile.isCrossMajor ? '是' : '否' }}</span>
          </div>
          <el-divider />
          <div class="profile-row">
            <span class="label">学位类型</span>
            <span class="value">{{ profile.acceptAcademic ? '接受学硕' : '仅专硕' }}</span>
          </div>
        </div>
      </el-card>

      <!-- 编辑模式 -->
      <el-card v-if="editing" class="profile-edit-card">
        <el-form ref="formRef" :model="form" label-width="120px" style="max-width:500px">
          <el-form-item label="预估总分" required>
            <el-input-number v-model="form.estimatedScore" :min="100" :max="500" />
            <span style="margin-left:8px;color:#909399">满分 500</span>
          </el-form-item>
          <el-form-item label="目标省份">
            <el-select v-model="form.targetRegions" multiple filterable placeholder="不限（默认全国）"
              style="width:100%">
              <el-option v-for="p in provinces" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-form-item label="接受学硕">
            <el-switch v-model="form.acceptAcademic" />
            <span style="margin-left:8px;color:#909399">关闭则只看专硕</span>
          </el-form-item>
          <el-divider>以下选填</el-divider>
          <el-form-item label="本科层次">
            <el-select v-model="form.undergradTier" clearable placeholder="请选择" style="width:100%">
              <el-option label="985" value="985" />
              <el-option label="211" value="211" />
              <el-option label="双一流" value="DOUBLE_FIRST" />
              <el-option label="普通一本" value="PUBLIC_REGULAR" />
              <el-option label="二本/民办" value="PRIVATE" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="本科专业">
            <el-input v-model="form.undergraduateMajor" placeholder="如 计算机科学与技术" />
          </el-form-item>
          <el-form-item label="跨考">
            <el-switch v-model="form.isCrossMajor" />
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
import { useUserStore } from '@/stores/user'
import { getProfile, saveProfile } from '@/api/profile'

const userStore = useUserStore()

const Tiers = { '985':'985', '211':'211', 'DOUBLE_FIRST':'双一流',
  'PUBLIC_REGULAR':'普通一本', 'PRIVATE':'二本/民办', 'OTHER':'其他' }

const provinces = ['北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江','上海','江苏',
  '浙江','安徽','福建','江西','山东','河南','湖北','湖南','广东','广西','海南','重庆','四川',
  '贵州','云南','西藏','陕西','甘肃','青海','宁夏','新疆']

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)

const profile = reactive({
  estimatedScore: null, targetRegions: [], undergradTier: null,
  undergraduateMajor: '', isCrossMajor: false, acceptAcademic: false
})

const form = reactive({
  estimatedScore: null, targetRegions: [], undergradTier: null,
  undergraduateMajor: '', isCrossMajor: false, acceptAcademic: false
})

const isEmpty = computed(() => {
  return !profile.estimatedScore
})

const regionText = computed(() => {
  const r = profile.targetRegions
  return (r && r.length) ? r.join('、') : '不限'
})

function tierLabel(v) { return Tiers[v] || v || '-' }

function fetchProfile() {
  loading.value = true
  getProfile().then(res => {
    if (res.data && res.data.estimatedScore) {
      const p = res.data
      let regions = p.targetRegions
      if (typeof regions === 'string') {
        try { regions = JSON.parse(regions) } catch(e) { regions = [] }
      }
      profile.estimatedScore = p.estimatedScore
      profile.targetRegions = regions || []
      profile.acceptAcademic = p.acceptAcademic === 1
      profile.undergradTier = p.undergradTier
      profile.undergraduateMajor = p.undergraduateMajor || ''
      profile.isCrossMajor = p.isCrossMajor === 1
    }
  }).finally(() => { loading.value = false })
}

function startEdit() {
  form.estimatedScore = profile.estimatedScore || null
  form.targetRegions = [...(profile.targetRegions || [])]
  form.acceptAcademic = profile.acceptAcademic || false
  form.undergradTier = profile.undergradTier || null
  form.undergraduateMajor = profile.undergraduateMajor || ''
  form.isCrossMajor = profile.isCrossMajor || false
  editing.value = true
}

function handleSave() {
  if (!form.estimatedScore) {
    ElMessage.warning('请输入预估总分')
    return
  }
  saving.value = true
  const data = {
    estimatedScore: form.estimatedScore,
    targetRegions: JSON.stringify(form.targetRegions),
    acceptPartTime: false,
    acceptAcademic: form.acceptAcademic,
    undergradTier: form.undergradTier,
    undergraduateMajor: form.undergraduateMajor,
    isCrossMajor: form.isCrossMajor
  }
  saveProfile(data).then(() => {
    ElMessage.success('保存成功')
    Object.assign(profile, { ...form })
    editing.value = false
    userStore.setProfile(data)
  }).finally(() => { saving.value = false })
}

onMounted(() => { fetchProfile() })
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 600px; margin: 24px auto; padding: 0 16px; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h3 { margin: 0; }
.empty-profile { text-align: center; padding: 60px 0; color: #909399; }
.empty-profile i { font-size: 48px; display: block; margin-bottom: 16px; }
.profile-row { display: flex; align-items: center; padding: 4px 0; }
.profile-row .label { width: 100px; color: #909399; font-size: 14px; flex-shrink: 0; }
.profile-row .value { color: #303133; font-size: 14px; }
.profile-row .value.highlight { font-size: 20px; font-weight: 700; color: #409eff; }
.profile-view-card .el-divider { margin: 12px 0; }
</style>
