<template>
  <div class="app-page">
    <AppHeader current-page="results" />
    <div class="app-body">
      <div v-if="!result" style="text-align:center;padding:60px">
        <p style="color:#909399;font-size:16px">暂无筛选结果</p>
        <el-button type="primary" @click="$router.push('/app/recommend')">去开始筛选</el-button>
      </div>
      <template v-else-if="allItems.length === 0">
        <div style="text-align:center;padding:60px">
          <p style="font-size:16px;color:#909399">无匹配结果</p>
          <p style="color:#c0c4cc;margin-top:8px">请扩大筛选范围或调整地区</p>
          <el-button style="margin-top:16px" type="primary" @click="$router.push('/app/recommend')">调整筛选条件</el-button>
        </div>
      </template>
      <template v-else>
        <h3>筛选结果
          <span style="font-size:14px;color:#909399;font-weight:400">
            共 {{ schoolCount }} 所学校 · {{ allItems.length }} 个专业方向
          </span>
        </h3>
        <div v-if="scoreRangeTip" style="padding:8px 16px;margin-bottom:16px;background:#f0f9ff;border-radius:4px;color:#1890ff;font-size:13px">
          拟录取最低分 ≤ 预估分 +{{ scoreRangeTip }} · 数据年份：各专业最新可用年份
        </div>

        <template v-for="(prov, provName) in groupedByProvince">
          <div class="province-divider">{{ provName }}
            <span style="font-weight:400;color:#909399;font-size:13px">（{{ prov.schools.length }} 所学校 · {{ prov.totalPrograms }} 个方向）</span>
          </div>

          <!-- 学校卡片 -->
          <div v-for="school in prov.schools" :key="school.name" class="school-card">
            <div class="school-header" @click="toggleSchool(school.name)">
              <div class="school-left">
                <span class="school-name">{{ school.name }}</span>
                <el-tag v-if="school.is985" size="mini" type="danger">985</el-tag>
                <el-tag v-if="school.is211" size="mini" type="warning">211</el-tag>
                <el-tag v-if="school.isDoubleFirst" size="mini" type="success">双一流</el-tag>
                <span class="school-tier">{{ school.tier }}</span>
                <span class="school-city">{{ school.city }}</span>
              </div>
              <div class="school-right">
                <span class="program-count">{{ school.colleges.length }} 个学院 · {{ school.totalPrograms }} 个方向</span>
                <span class="expand-arrow" :class="{ open: expandedSchools.has(school.name) }">▾</span>
              </div>
            </div>

            <div v-show="expandedSchools.has(school.name)" class="school-body">
              <!-- 学院分组 -->
              <div v-for="college in school.colleges" :key="college.name" class="college-group">
                <div class="college-name">{{ college.name }}</div>
                <!-- 专业表格 -->
                <table class="program-table">
                  <thead>
                    <tr>
                      <th>专业方向</th>
                      <th>代码</th>
                      <th>类型</th>
                      <th class="num-col">复试线</th>
                      <th class="num-col">招生人数</th>
                      <th class="num-col">复试人数</th>
                      <th class="num-col">录取最低分</th>
                      <th class="num-col">录取均分</th>
                      <th class="num-col">分差</th>
                      <th>历年</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="p in college.programs" :key="p.programId || p.programCode + '_' + p.programName + '_' + p.collegeName"
                        :class="{ 'row-no-data': !p.scoreLine && !p.avgAdmittedScore }">
                      <td class="prog-name">{{ p.programName }}</td>
                      <td><code class="code-tag">{{ p.programCode || '-' }}</code></td>
                      <td>
                        <span class="type-tag" :class="p.studyMode === 'full_time' ? 'full' : 'part'">{{ p.studyMode === 'full_time' ? '全日制' : '非全' }}</span>
                        <span class="type-tag degree">{{ p.degreeType === 'professional' ? '专硕' : '学硕' }}</span>
                      </td>
                      <td class="num-col">{{ p.scoreLine || '-' }}</td>
                      <td class="num-col">{{ p.planCount || '-' }}</td>
                      <td class="num-col">{{ p.retestCount || '-' }}</td>
                      <td class="num-col score-highlight">{{ p.minAdmittedScore || '-' }}</td>
                      <td class="num-col score-highlight">{{ p.avgAdmittedScore || '-' }}</td>
                      <td class="num-col" :class="{ gapPos: (p.scoreGap||0) > 0, gapNeg: (p.scoreGap||0) < 0 }">
                        {{ (p.scoreGap||0) > 0 ? '+' : '' }}{{ p.scoreGap||0 }}
                      </td>
                      <td>
                        <el-popover placement="left" width="380" trigger="click" v-if="p.historyScores && p.historyScores.length > 0">
                          <table class="history-pop">
                            <tr><th>年份</th><th>复试线</th><th>录取最低</th><th>录取均分</th><th>招生</th><th>复试人数</th></tr>
                            <tr v-for="h in p.historyScores" :key="h.year">
                              <td :class="{ 'cur': h.year === 2025 }">{{ h.year }}</td>
                              <td>{{ h.score_line || '-' }}</td>
                              <td>{{ h.min_admitted_score || '-' }}</td>
                              <td>{{ h.avg_admitted_score || '-' }}</td>
                              <td>{{ h.unified_exam_quota || '-' }}</td>
                              <td>{{ h.retest_count || '-' }}</td>
                            </tr>
                          </table>
                          <el-button slot="reference" size="mini" type="text">历史</el-button>
                        </el-popover>
                        <span v-else style="color:#c0c4cc;font-size:12px">-</span>
                      </td>
                      <td>
                        <el-button
                          :type="isFavorited(p.programId) ? 'warning' : 'default'"
                          size="mini" circle
                          :icon="isFavorited(p.programId) ? 'el-icon-star-on' : 'el-icon-star-off'"
                          @click.stop="toggleFavorite(p)" :loading="favLoading === p.programId">
                        </el-button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <!-- 学校级数据来源链接 -->
              <div v-if="school.sourceUrl" style="padding:8px 0 0;text-align:right">
                <a :href="school.sourceUrl" target="_blank" style="font-size:12px;color:#1890ff;text-decoration:none">
                  📎 数据来源：N诺考研
                </a>
              </div>
            </div>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script>
import { addFavorite, removeFavorite, listFavorites } from '@/api/postgrad/appFavorites'
import AppHeader from './components/AppHeader'
import { mapActions } from 'vuex'

export default {
  name: 'AppResults',
  components: { AppHeader },
  data() {
    return {
      result: null,
      scoreRangeTip: '',
      favoriteIds: [],
      favLoading: null,
      expandedSchools: new Set()
    }
  },
  computed: {
    allItems() {
      if (!this.result) return []
      return (this.result.steady || [])
    },
    schoolCount() {
      const names = new Set()
      this.allItems.forEach(i => { if (i.schoolName) names.add(i.schoolName) })
      return names.size
    },
    groupedByProvince() {
      // Province → School → College → Program
      const provs = {}
      for (const item of this.allItems) {
        const prov = item.province || '其他'
        if (!provs[prov]) provs[prov] = { schools: {}, totalPrograms: 0 }
        const pd = provs[prov]

        const sn = item.schoolName || '未知学校'
        if (!pd.schools[sn]) {
          pd.schools[sn] = {
            name: sn,
            city: item.city || '',
            tier: item.tier || '',
            is985: item.is985,
            is211: item.is211,
            isDoubleFirst: item.isDoubleFirst,
            sourceUrl: item.sourceUrl || '',
            colleges: {},
            totalPrograms: 0
          }
        }
        const sc = pd.schools[sn]

        const cn = item.collegeName || '未知学院'
        if (!sc.colleges[cn]) sc.colleges[cn] = { name: cn, programs: [] }
        sc.colleges[cn].programs.push(item)
        sc.totalPrograms++
        pd.totalPrograms++
      }

      // Sort and flatten
      const result = {}
      const provNames = Object.keys(provs).sort((a, b) => provs[b].totalPrograms - provs[a].totalPrograms)
      for (const pn of provNames) {
        const pd = provs[pn]
        const schoolList = Object.values(pd.schools).sort((a, b) => b.totalPrograms - a.totalPrograms)
        // Sort colleges within each school
        for (const sc of schoolList) {
          const collegeList = Object.values(sc.colleges).sort((a, b) => b.programs.length - a.programs.length)
          // Sort programs within each college
          for (const cl of collegeList) {
            cl.programs.sort((a, b) => (a.minAdmittedScore || a.scoreLine || 0) - (b.minAdmittedScore || b.scoreLine || 0))
          }
          sc.colleges = collegeList
        }
        result[pn] = { schools: schoolList, totalPrograms: pd.totalPrograms }
      }
      return result
    }
  },
  created() {
    const cached = sessionStorage.getItem('app-recommend-result')
    if (cached) {
      try { this.result = JSON.parse(cached) } catch(e) {}
    }
    this.scoreRangeTip = sessionStorage.getItem('app-filter-scoreRange') || ''

    // Expand first 5 schools by default
    let count = 0
    for (const item of this.allItems) {
      if (count >= 5) break
      if (!this.expandedSchools.has(item.schoolName)) {
        this.expandedSchools.add(item.schoolName)
        count++
      }
    }

    this.fetchFavorites()
  },
  methods: {
    ...mapActions('appUser', ['Logout']),
    fetchFavorites() {
      listFavorites().then(res => {
        this.favoriteIds = (res.data || []).map(f => f.program_id)
      }).catch(() => {})
    },
    isFavorited(programId) {
      return this.favoriteIds.includes(programId)
    },
    toggleFavorite(item) {
      this.favLoading = item.programId
      if (this.isFavorited(item.programId)) {
        removeFavorite(item.programId).then(() => {
          this.favoriteIds = this.favoriteIds.filter(id => id !== item.programId)
          this.$message.success('已取消收藏')
        }).finally(() => { this.favLoading = null })
      } else {
        addFavorite(item.programId).then(() => {
          this.favoriteIds.push(item.programId)
          this.$message.success('收藏成功')
        }).finally(() => { this.favLoading = null })
      }
    },
    toggleSchool(name) {
      if (this.expandedSchools.has(name)) {
        this.expandedSchools.delete(name)
      } else {
        this.expandedSchools.add(name)
      }
      // Force reactivity
      this.expandedSchools = new Set(this.expandedSchools)
    },
    handleLogout() {
      this.Logout().then(() => { this.$router.push('/app/login') })
    }
  }
}
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }

</style>
