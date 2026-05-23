<template>
  <div class="app-page">
    <AppHeader current-page="recommend" />
    <div class="app-body">
      <h3>智能推荐</h3>
      <el-card v-loading="loadingProfile" style="margin-bottom:20px">
        <div v-if="profile">
          <el-descriptions title="当前画像" :column="3" size="small">
            <el-descriptions-item label="预估分数">{{ profile.estimatedScore || profile.estimated_score || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="目标省份">{{ (profile.targetRegions || []).join(', ') || '未选择' }}</el-descriptions-item>
            <el-descriptions-item label="风险偏好">{{ riskLabel(profile.riskPreference || profile.risk_preference) }}</el-descriptions-item>
            <el-descriptions-item label="非全日制">{{ profile.acceptPartTime ? '接受' : '不接受' }}</el-descriptions-item>
            <el-descriptions-item label="学术学位">{{ profile.acceptAcademic ? '接受' : '不接受' }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div v-else style="text-align:center;padding:20px">
          <p style="color:#909399">请先完善考研画像</p>
          <el-button type="primary" @click="$router.push('/app/profile')">去填写画像</el-button>
        </div>
      </el-card>
      <div v-if="profile" style="max-width:600px;margin:0 auto">
        <el-card style="margin-bottom:16px">
          <div slot="header" style="font-weight:600">筛选范围</div>
          <div style="text-align:center">
            <div style="font-size:28px;font-weight:700;color:#303133;margin-bottom:4px">
              ≤ {{ (profile.estimatedScore || profile.estimated_score) + scoreRange }}
            </div>
            <div style="font-size:13px;color:#909399;margin-bottom:16px">
              拟录取最低分 ≤ 预估分 +{{ scoreRange }}（低于此线的全部展示）
            </div>
            <el-slider v-model="scoreRange" :min="5" :max="60" :step="5" show-stops
              :marks="{5:'±5', 10:'±10', 20:'±20', 30:'±30', 40:'±40', 50:'±50', 60:'±60'}" />
          </div>
        </el-card>
        <div style="text-align:center">
          <el-button type="primary" size="large" :loading="generating" @click="handleGenerate">
            {{ generating ? '查询中...' : '开始筛选' }}
          </el-button>
          <el-button size="large" @click="$router.push('/app/profile')">修改画像</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { filter } from '@/api/postgrad/appRecommendation'
import AppHeader from './components/AppHeader'
import { getProfile } from '@/api/postgrad/appProfile'
import { mapActions } from 'vuex'

export default {
  name: 'AppRecommend',
  components: { AppHeader },
  data() {
    return { loadingProfile: false, generating: false, profile: null, scoreRange: 20 }
  },
  created() {
    this.loadingProfile = true
    getProfile().then(res => {
      if (res.data) {
        this.profile = res.data
        if (typeof this.profile.target_regions === 'string') {
          try { this.profile.targetRegions = JSON.parse(this.profile.target_regions) } catch(e) { this.profile.targetRegions = [] }
        }
        this.scoreRange = res.data.score_range || 20
      }
    }).finally(() => { this.loadingProfile = false })
  },
  methods: {
    ...mapActions('appUser', ['Logout']),
    riskLabel(v) {
      const m = { conservative: '保守', balanced: '均衡', aggressive: '激进' }
      return m[v] || v || '均衡'
    },
    handleGenerate() {
      if (!this.profile || (!this.profile.estimated_score && !this.profile.estimatedScore)) {
        this.$message.warning('请先填写预估分数')
        return
      }
      this.generating = true
      const reqData = {
        estimatedScore: this.profile.estimated_score || this.profile.estimatedScore,
        scoreRange: this.scoreRange,
        targetRegions: this.profile.targetRegions || [],
        acceptPartTime: !!this.profile.accept_part_time,
        acceptAcademic: !!this.profile.accept_academic
      }
      // Save scoreRange to profile in session
      sessionStorage.setItem('app-filter-scoreRange', this.scoreRange)
      filter(reqData).then(res => {
        sessionStorage.setItem('app-recommend-result', JSON.stringify(res.data))
        this.$router.push('/app/results')
      }).finally(() => { this.generating = false })
    },
    handleLogout() {
      this.Logout().then(() => { this.$router.push('/app/login') })
    }
  }
}
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-header { display: flex; justify-content: space-between; align-items: center; padding: 0 24px; height: 56px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }

.nav-link:hover, .router-link-active
.app-body { max-width: 1200px; margin: 24px auto; padding: 0 16px; }
.app-body h3 { margin-bottom: 16px; }
</style>
