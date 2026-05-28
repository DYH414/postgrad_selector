<template>
  <div class="ai-report-page">
    <AppHeader current-page="recommend" />

    <div class="report-container" v-if="report">
      <div v-if="report.status === 'PENDING'" class="pending-state">
        <i class="el-icon-loading" />
        <p>AI 正在为你生成推荐报告...</p>
        <p style="color:#909399;font-size:12px">预计需要 10-30 秒</p>
      </div>

      <template v-else>
        <div class="report-header">
          <h2>你的 AI 择校推荐报告</h2>
          <p class="summary">{{ result.summary }}</p>
        </div>

        <div v-for="tier in result.tiers" :key="tier.level" class="tier-section">
          <h3 class="tier-label" :class="tier.level">
            {{ tier.label }} ({{ tier.schools.length }}所)
          </h3>
          <el-row :gutter="16">
            <el-col :span="8" v-for="school in tier.schools" :key="school.programId">
              <el-card class="school-card" shadow="hover">
                <div class="card-header">
                  <strong>{{ school.schoolName }}</strong>
                  <el-tag :type="riskType(school.risk)" size="mini">{{ riskLabel(school.risk) }}</el-tag>
                </div>
                <p class="program-name">{{ school.programName }}</p>
                <el-divider />
                <p class="reason">{{ school.reason }}</p>
                <div class="pros-cons">
                  <div v-if="school.pros && school.pros.length">
                    <strong>优势：</strong>
                    <span v-for="p in school.pros" :key="p" class="tag green">{{ p }}</span>
                  </div>
                  <div v-if="school.cons && school.cons.length" style="margin-top:4px">
                    <strong>注意：</strong>
                    <span v-for="c in school.cons" :key="c" class="tag orange">{{ c }}</span>
                  </div>
                </div>
                <div class="match-bar">
                  <span>匹配度</span>
                  <el-progress :percentage="school.matchScore || 0"
                    :color="matchColor(school.matchScore)" />
                </div>

                <div v-if="school.scoreLine || school.avgAdmittedScore" class="stats-grid">
                  <div class="stat-item" v-if="school.scoreLine">
                    <span class="stat-label">复试线</span>
                    <span class="stat-val">{{ school.scoreLine }}</span>
                  </div>
                  <div class="stat-item" v-if="school.avgAdmittedScore">
                    <span class="stat-label">录取均分</span>
                    <span class="stat-val">{{ school.avgAdmittedScore }}</span>
                  </div>
                  <div class="stat-item" v-if="school.admissionLow">
                    <span class="stat-label">最低分</span>
                    <span class="stat-val">{{ school.admissionLow }}</span>
                  </div>
                  <div class="stat-item" v-if="school.admissionHigh">
                    <span class="stat-label">最高分</span>
                    <span class="stat-val">{{ school.admissionHigh }}</span>
                  </div>
                  <div class="stat-item" v-if="school.planCount">
                    <span class="stat-label">招生计划</span>
                    <span class="stat-val">{{ school.planCount }}</span>
                  </div>
                  <div class="stat-item" v-if="school.admittedCount">
                    <span class="stat-label">录取人数</span>
                    <span class="stat-val">{{ school.admittedCount }}</span>
                  </div>
                </div>

                <div v-if="school.dataYear || school.dataCompleteness" class="data-meta">
                  <span v-if="school.dataYear">{{ school.dataYear }}年数据</span>
                  <span v-if="school.dataCompleteness" class="completeness-tag"
                    :class="'completeness-' + school.dataCompleteness">
                    完整度 {{ school.dataCompleteness }}
                  </span>
                </div>

                <a v-if="school.sourceUrl" class="source-link"
                  :href="school.sourceUrl" target="_blank" @click.stop>
                  数据来源: {{ school.sourceOwner || 'N诺' }} →
                </a>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <div class="report-actions">
          <el-button type="primary" @click="$router.back()">返回</el-button>
          <el-button @click="restartRecommend">重新推荐</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader'
import { getAiReport } from '@/api/postgrad/ai'

export default {
  name: 'AiReport',
  components: { AppHeader },
  data() {
    return { report: null, pollTimer: null }
  },
  computed: {
    result() {
      if (!this.report) return { summary: '', tiers: [] }
      const data = this.report.result || this.report
      if (typeof data === 'string') {
        try { return JSON.parse(data) } catch (e) { return { summary: '', tiers: [] } }
      }
      return data
    }
  },
  created() {
    this.fetchReport()
  },
  beforeDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },
  methods: {
    async fetchReport() {
      try {
        const res = await getAiReport(this.$route.params.id)
        this.report = res.data
        if (res.data.status === 'PENDING') {
          this.pollTimer = setInterval(async () => {
            const r = await getAiReport(this.$route.params.id)
            this.report = r.data
            if (r.data.status !== 'PENDING') clearInterval(this.pollTimer)
          }, 3000)
        }
      } catch (e) {
        this.$message.error('加载报告失败')
      }
    },

    riskType(risk) {
      return risk === 'high' ? 'danger' : risk === 'medium' ? 'warning' : 'success'
    },

    riskLabel(risk) {
      return risk === 'high' ? '高风险' : risk === 'medium' ? '中等风险' : '低风险'
    },

    matchColor(score) {
      return score >= 70 ? '#67c23a' : score >= 50 ? '#e6a23c' : '#f56c6c'
    },

    restartRecommend() {
      this.$router.push({ name: 'AppRecommend' })
    }
  }
}
</script>

<style scoped>
.ai-report-page { min-height: 100vh; background: #f5f7fa; }
.report-container { max-width: 1100px; margin: 0 auto; padding: 24px; }
.pending-state { text-align: center; padding: 120px 0; }
.pending-state i { font-size: 48px; color: #409eff; }
.report-header { margin-bottom: 32px; }
.report-header h2 { margin-bottom: 8px; }
.summary { color: #606266; font-size: 15px; }
.tier-section { margin-bottom: 32px; }
.tier-label { padding: 6px 0; border-bottom: 2px solid #ebeef5; margin-bottom: 16px; }
.tier-label.reach { color: #f56c6c; }
.tier-label.steady { color: #e6a23c; }
.tier-label.safe { color: #67c23a; }
.school-card { margin-bottom: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.program-name { color: #909399; font-size: 13px; margin: 4px 0; }
.reason { color: #303133; font-size: 13px; line-height: 1.6; }
.tag { display: inline-block; padding: 1px 6px; border-radius: 4px;
  font-size: 12px; margin-right: 4px; }
.tag.green { background: #f0f9eb; color: #67c23a; }
.tag.orange { background: #fdf6ec; color: #e6a23c; }
.match-bar { margin-top: 12px; display: flex; align-items: center; gap: 8px; }
.match-bar span { font-size: 12px; color: #909399; white-space: nowrap; }
.report-actions { text-align: center; padding: 24px 0 48px; }

.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 12px; }
.stat-item { text-align: center; padding: 4px 2px; background: #f8fafc; border-radius: 4px; }
.stat-label { display: block; font-size: 11px; color: #909399; }
.stat-val { font-size: 15px; font-weight: 700; color: #303133; }

.data-meta { margin-top: 8px; font-size: 12px; color: #909399; display: flex; gap: 8px; align-items: center; }
.completeness-tag { display: inline-block; padding: 0 4px; border-radius: 3px; font-size: 11px; }
.completeness-A { background: #f0f9eb; color: #67c23a; }
.completeness-B { background: #fdf6ec; color: #e6a23c; }
.completeness-C { background: #fef0f0; color: #f56c6c; }

.source-link { display: block; margin-top: 8px; font-size: 11px; color: #409eff; text-decoration: none; }
.source-link:hover { text-decoration: underline; }
</style>
