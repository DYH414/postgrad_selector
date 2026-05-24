<template>
  <div class="app-page">
    <AppHeader current-page="history" />
    <div class="app-body">
      <div style="margin-bottom:16px">
        <el-button size="small" @click="$router.push('/app/history')">← 返回历史</el-button>
      </div>
      <div v-loading="loading">
        <template v-if="result">
          <h3>推荐结果 <span style="font-size:14px;color:#909399;font-weight:400">共 {{ result.totalCandidates }} 个候选</span></h3>
          <div class="risk-alert">
            <i class="el-icon-warning-outline"></i>
            <div>
              <strong>风险提示</strong>
              <p>复试线不是最低录取分；推荐学校不代表只有这些学校可以报。当前数据主要来源于 N诺（第三方整理），可能存在遗漏或错误，请以院校官网和招生公告为准。</p>
              <p>N诺数据完整度 A：含复试线、拟录取区间、人数等字段；B：含主要分数字段，部分字段缺失；C：仅有复试线或基础字段。</p>
            </div>
          </div>
          <el-tabs v-model="activeTab">
            <el-tab-pane :label="'稳妥 (' + (result.steady||[]).length + ')'" name="steady" />
            <el-tab-pane :label="'重点关注 (' + (result.focus||[]).length + ')'" name="focus" />
            <el-tab-pane :label="'可冲 (' + (result.reach||[]).length + ')'" name="reach" />
            <el-tab-pane :label="'数据不足 (' + (result.insufficient||[]).length + ')'" name="insufficient" />
          </el-tabs>
          <div v-if="currentList.length === 0" style="text-align:center;padding:40px;color:#909399">暂无该分组结果</div>
          <div v-for="item in currentList" :key="item.schoolId + '_' + item.programId" class="result-card">
            <div class="card-top">
              <div class="card-school">
                <span class="school-name">{{ item.schoolName }}</span>
                <el-tag v-if="item.is985" size="mini" type="danger">985</el-tag>
                <el-tag v-if="item.is211" size="mini" type="warning">211</el-tag>
                <span class="tier-tag">{{ item.tier }}</span>
              </div>
            </div>
            <div class="card-info">
              <span>{{ item.collegeName }} · {{ item.programName }} ({{ item.programCode }})</span>
            </div>
            <div class="card-scores">
              <div class="score-item">
                <div class="score-val">{{ item.effectiveScore }}</div>
                <div class="score-label">有效分数线</div>
              </div>
              <div class="score-item" :class="{ 'positive': item.scoreGap > 0, 'negative': item.scoreGap < 0 }">
                <div class="score-val">{{ item.scoreGap > 0 ? '+' : '' }}{{ item.scoreGap }}</div>
                <div class="score-label">最低录取分差</div>
              </div>
              <div class="score-item" v-if="item.minAdmittedScore">
                <div class="score-val">{{ item.minAdmittedScore }}</div>
                <div class="score-label">录取最低分</div>
              </div>
              <div class="score-item" v-if="item.avgAdmittedScore">
                <div class="score-val">{{ item.avgAdmittedScore }}</div>
                <div class="score-label">录取平均分</div>
              </div>
            </div>
            <div class="card-basis">{{ item.scoreBasis }}</div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader'
import { mapActions } from 'vuex'
import { getRecommendationHistoryDetail } from '@/api/postgrad/appRecommendation'

export default {
  name: 'AppHistoryDetail',
  components: { AppHeader },
  data() {
    return { loading: false, result: null, activeTab: 'steady' }
  },
  computed: {
    currentList() {
      if (!this.result) return []
      return this.result[this.activeTab] || []
    }
  },
  created() {
    const id = this.$route.params.id
    if (id) {
      this.loading = true
      getRecommendationHistoryDetail(id).then(res => {
        const payload = res.data ? res.data.result : null
        this.result = payload && payload.groups ? this.normalizeResult(payload) : payload
      }).finally(() => { this.loading = false })
    }
  },
  methods: {
    ...mapActions('appUser', ['Logout']),
    normalizeResult(payload) {
      const groups = payload.groups || []
      return {
        totalCandidates: payload.summary ? payload.summary.totalCandidates : 0,
        steady: this.groupItems(groups, 'steady'),
        focus: this.groupItems(groups, 'balanced_sprint'),
        reach: this.groupItems(groups, 'sprint'),
        insufficient: this.groupItems(groups, 'insufficient_data')
      }
    },
    groupItems(groups, key) {
      const group = groups.find(item => item.groupKey === key)
      return group ? (group.items || []).map(item => ({
        schoolId: item.schoolId,
        programId: item.programId,
        schoolName: item.schoolName,
        is985: item.is985,
        is211: item.is211,
        tier: item.schoolTier,
        collegeName: item.collegeName,
        programName: item.programName,
        programCode: item.programCode,
        effectiveScore: item.scoreLine,
        scoreGap: item.admissionLowGap,
        minAdmittedScore: item.admissionLow,
        avgAdmittedScore: item.avgAdmittedScore,
        scoreBasis: (item.examCombo || '') + ' ' + (item.examSubjectsLabel || '')
      })) : []
    },
    handleLogout() {
      this.Logout().then(() => { this.$router.push('/app/login') })
    }
  }
}
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }

.app-body { max-width: 1200px; margin: 24px auto; padding: 0 16px; }
.app-body h3 { margin-bottom: 16px; }
.risk-alert { display: grid; grid-template-columns: 32px 1fr; gap: 10px; padding: 12px 14px; margin-bottom: 16px; border: 1px solid #ffdca8; border-radius: 8px; background: #fff8e8; color: #8a4b10; line-height: 1.55; }
.risk-alert i { font-size: 22px; margin-top: 2px; color: #e07818; }
.risk-alert strong { color: #bf6814; }
.risk-alert p { margin: 4px 0 0; }
.result-card { background: #fff; border-radius: 8px; padding: 16px 20px; margin-bottom: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.school-name { font-size: 16px; font-weight: 600; margin-right: 8px; }
.tier-tag { color: #909399; font-size: 12px; margin-left: 8px; }
.card-info { color: #606266; font-size: 14px; margin-bottom: 8px; }
.card-scores { display: flex; gap: 20px; flex-wrap: wrap; margin-bottom: 8px; }
.score-item { text-align: center; min-width: 70px; }
.score-val { font-size: 20px; font-weight: 700; color: #303133; }
.score-val.positive { color: #67c23a; }
.score-val.negative { color: #f56c6c; }
.score-label { font-size: 12px; color: #909399; }
.card-basis { font-size: 12px; color: #909399; }
</style>
