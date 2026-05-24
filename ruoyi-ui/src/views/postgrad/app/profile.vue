<template>
  <div class="app-page">
    <AppHeader current-page="profile" />
    <div class="app-body">
      <h3>考研画像</h3>
      <el-card v-loading="loading">
        <el-form ref="form" :model="form" label-width="120px" style="max-width:500px">
          <el-form-item label="预估初试总分" required>
            <el-input-number v-model="form.estimatedScore" :min="100" :max="500" placeholder="如 350" />
            <span style="margin-left:8px;color:#909399">满分500</span>
          </el-form-item>
          <el-form-item label="目标省份">
            <el-select v-model="form.targetRegions" multiple filterable placeholder="不限（默认全国）" style="width:100%">
              <el-option v-for="p in provinces" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-form-item label="风险偏好">
            <el-radio-group v-model="form.riskPreference">
              <el-radio label="conservative">保守（只看稳妥）</el-radio>
              <el-radio label="balanced">均衡（稳妥+可冲）</el-radio>
              <el-radio label="aggressive">激进（含高风险）</el-radio>
            </el-radio-group>
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
              <el-option label="二本" value="PRIVATE" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="本科专业">
            <el-input v-model="form.undergraduateMajor" placeholder="如 计算机科学与技术" />
          </el-form-item>
          <el-form-item label="是否跨考">
            <el-switch v-model="form.isCrossMajor" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存画像</el-button>
            <el-button @click="$router.push('/app/recommend')">去生成推荐</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script>
import { getProfile, saveProfile } from '@/api/postgrad/appProfile'
import AppHeader from './components/AppHeader'
import { mapActions } from 'vuex'

const provinces = ['北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江','上海','江苏','浙江','安徽','福建','江西','山东','河南','湖北','湖南','广东','广西','海南','重庆','四川','贵州','云南','西藏','陕西','甘肃','青海','宁夏','新疆']

export default {
  name: 'AppProfile',
  components: { AppHeader },
  data() {
    return {
      loading: false, saving: false, provinces,
      form: {
        estimatedScore: null,
        targetRegions: [],
        acceptPartTime: false,
        acceptAcademic: false,
        riskPreference: 'balanced',
        undergradTier: null,
        undergraduateMajor: '',
        isCrossMajor: false
      }
    }
  },
  created() { this.fetchProfile() },
  methods: {
    ...mapActions('appUser', ['Logout', 'SetProfile']),
    fetchProfile() {
      this.loading = true
      getProfile().then(res => {
        if (res.data && res.data.estimated_score) {
          const p = res.data
          let regions = p.target_regions
          if (typeof regions === 'string') {
            try { regions = JSON.parse(regions) } catch(e) { regions = [] }
          }
          this.form = {
            estimatedScore: p.estimated_score,
            targetRegions: regions || [],
            acceptPartTime: p.accept_part_time === 1,
            acceptAcademic: p.accept_academic === 1,
            riskPreference: p.risk_preference || 'balanced',
            undergradTier: p.undergrad_tier,
            undergraduateMajor: p.undergraduate_major || '',
            isCrossMajor: p.is_cross_major === 1
          }
        }
      }).finally(() => { this.loading = false })
    },
    handleSave() {
      if (!this.form.estimatedScore) {
        this.$message.warning('请输入预估初试总分')
        return
      }
      this.saving = true
      const data = {
        estimatedScore: this.form.estimatedScore,
        targetRegions: JSON.stringify(this.form.targetRegions),
        acceptPartTime: false,
        acceptAcademic: this.form.acceptAcademic,
        riskPreference: this.form.riskPreference,
        undergradTier: this.form.undergradTier,
        undergraduateMajor: this.form.undergraduateMajor,
        isCrossMajor: this.form.isCrossMajor
      }
      saveProfile(data).then(() => {
        this.$message.success('保存成功')
        this.SetProfile(data)
      }).finally(() => { this.saving = false })
    },
    handleLogout() {
      this.Logout().then(() => {
        this.$router.push('/app/login')
      })
    }
  }
}
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }

.app-body { max-width: 600px; margin: 24px auto; padding: 0 16px; }
.app-body h3 { margin-bottom: 16px; }
</style>
