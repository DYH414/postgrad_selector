<template>
  <div class="prototype-page">
    <AppHeader :current-page="currentHeader" />

    <main class="result-layout">
      <aside class="filter-sidebar">
        <div class="sidebar-title">
          <strong>筛选条件</strong>
          <button type="button">清空</button>
        </div>
        <div v-for="item in filters" :key="item.label" class="filter-group">
          <label>{{ item.label }}</label>
          <div class="filter-select">
            <span>{{ item.value }}</span>
            <i class="el-icon-arrow-down"></i>
          </div>
        </div>
        <el-button class="filter-button" type="primary">应用筛选</el-button>
        <div class="tip-box">
          <i class="el-icon-info"></i>
          <strong>小贴士</strong>
          <p>推荐结果不是唯一可报范围，复试线也不是最低录取分；N诺数据可能遗漏或错误，请结合院校官网复核。</p>
        </div>
      </aside>

      <section class="result-main">
        <div class="result-head">
          <div>
            <h1>{{ activeTab === 'compare' ? '对比与备选' : '推荐结果' }}</h1>
            <span v-if="activeTab !== 'compare'">共 24 所院校</span>
          </div>
          <div class="chips">
            <span>分数 {{ result.score }} <i class="el-icon-close"></i></span>
            <span>考试组合 {{ result.exam }} <i class="el-icon-close"></i></span>
            <span>地区 {{ result.region }} <i class="el-icon-close"></i></span>
            <span>学习方式 {{ result.studyMode }} <i class="el-icon-close"></i></span>
          </div>
          <el-button plain type="primary" icon="el-icon-download">导出结果</el-button>
        </div>

        <div class="data-alert">
          <i class="el-icon-warning"></i>
          复试线不是最低录取分；推荐学校不代表只有这些学校可以报。当前数据主要来源于 N诺（第三方整理），可能存在遗漏或错误，请以院校官网为准。
          <button type="button" @click="activeTab = 'compare'">查看说明 <i class="el-icon-info"></i></button>
        </div>

        <template v-if="activeTab === 'compare'">
          <section class="compare-panel">
            <div class="tabs">
              <button class="active">院校对比</button>
              <button>我的备选</button>
            </div>
            <div class="compare-actions">
              <span>已选择 4 个项目</span>
              <div>
                <el-button icon="el-icon-download" size="small">导出清单</el-button>
                <el-button type="primary" icon="el-icon-data-line" size="small">一键对比</el-button>
                <el-button icon="el-icon-setting" size="small">调整列</el-button>
              </div>
            </div>
            <table class="compare-table">
              <tbody>
                <tr v-for="row in compareRows" :key="row.label">
                  <th>{{ row.label }}</th>
                  <td v-for="school in compareSchools" :key="school.name + row.label">
                    <template v-if="row.type === 'name'"><strong>{{ school.name }}</strong></template>
                    <template v-else-if="row.type === 'fit'">
                      <span class="fit-tag" :class="'fit-' + school.fitLevelClass">{{ school.fitLevel }}</span>
                      <span class="fit-stars" aria-hidden="true">
                        <span v-for="n in 5" :key="n" class="star" :class="{ light: n <= school.star }">★</span>
                      </span>
                    </template>
                    <template v-else-if="row.type === 'diff'">
                      <span class="score-diff" :class="{ positive: school[row.key] >= 0, negative: school[row.key] < 0 }">
                        {{ school[row.key] > 0 ? '+' : '' }}{{ school[row.key] }}
                      </span>
                    </template>
                    <template v-else-if="row.type === 'confidence'">
                      <span class="grade" :class="'grade-' + school.confidence.toLowerCase()">完整度 {{ school.confidence }}</span>
                      <small class="completeness-note">{{ dataCompletenessText(school.confidence) }}</small>
                    </template>
                    <template v-else-if="row.type === 'action'"><button class="detail-link" type="button">查看详情</button></template>
                    <template v-else>{{ school[row.key] }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
            <p class="table-note">注：拟录取区间为近三年拟录取总分范围，仅供参考；招生人数含推免，具体以院校当年公告为准。</p>
          </section>

          <section class="backup-panel">
            <div class="section-title">
              <strong>我的备选</strong>
              <span>共 11 个项目</span>
            </div>
            <div class="backup-grid">
              <div v-for="group in backupGroups" :key="group.name" class="backup-card" :class="group.theme">
                <div class="backup-title">
                  <div>
                    <strong>{{ group.name }}（{{ group.items.length }}）</strong>
                    <p>{{ group.desc }}</p>
                  </div>
                  <i class="el-icon-plus"></i>
                </div>
                <ul>
                  <li v-for="item in group.items" :key="item.name">
                    <span>{{ item.name }}</span>
                    <em>{{ item.grade }}</em>
                  </li>
                </ul>
                <button type="button">查看全部 {{ group.items.length }} 个项目 <i class="el-icon-right"></i></button>
              </div>
            </div>
          </section>
        </template>

        <template v-else>
          <section v-for="group in result.groups" :key="group.name" class="school-section">
            <div class="section-title">
              <strong>{{ group.name }}</strong>
              <span>（{{ group.schools.length }} 所） {{ group.desc }}</span>
              <button type="button">展开全部 <i class="el-icon-right"></i></button>
            </div>

            <div class="school-grid">
              <article v-for="school in group.schools" :key="school.schoolName" class="school-card">
                <div class="card-top">
                  <div class="school-seal">{{ school.schoolName.slice(0, 1) }}</div>
                  <div>
                    <h3>{{ school.schoolName }} <small>{{ school.badge }}</small></h3>
                    <p>{{ school.collegeName }} / {{ school.programName }}</p>
                    <span>{{ school.exam }} | {{ school.province }}</span>
                  </div>
                  <button class="star-btn" type="button"><i class="el-icon-star-off"></i></button>
                </div>

                <div class="score-line">
                  <div><span>复试线</span><strong>{{ school.scoreLine }}</strong></div>
                  <div><span>拟录取区间</span><strong>{{ school.range }}</strong></div>
                </div>

                <div class="tags">
                  <em class="source">N诺</em>
                  <em :class="'grade-' + school.confidence.toLowerCase()">完整度{{ school.confidence }}</em>
                  <em class="risk-tag">{{ school.tag }}</em>
                </div>

                <p class="card-note">{{ school.note }}</p>
              </article>
            </div>
          </section>
        </template>
      </section>
    </main>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader'

const fallbackResult = {
  score: 300,
  exam: '22408',
  region: '福建',
  studyMode: '全日制',
  groups: [
    {
      name: '冲刺',
      desc: '录取概率较低，但仍有机会',
      schools: [
        { schoolName: '厦门大学', badge: '985 211 双一流', collegeName: '信息学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 320, range: '315-336', confidence: 'B', tag: '冲刺', note: '近年复试线较高，报考热度大，300分有机会进入复试。', star: 4 },
        { schoolName: '福州大学', badge: '211 双一流', collegeName: '计算机与大数据学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 305, range: '295-322', confidence: 'B', tag: '冲刺', note: '专业实力较强，竞争激烈，建议冲刺尝试。', star: 4 },
        { schoolName: '华侨大学', badge: '中央部属', collegeName: '信息科学与工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 300, range: '290-316', confidence: 'B', tag: '冲刺', note: '复试线接近300分，存在机会，需关注复试表现。', star: 4 }
      ]
    },
    {
      name: '稳中偏冲',
      desc: '有一定机会，需合理评估',
      schools: [
        { schoolName: '福建师范大学', badge: '省属重点', collegeName: '计算机与网络空间安全学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 285, range: '276-304', confidence: 'B', tag: '稳中偏冲', note: '复试线与分数匹配，近年录取较稳，建议作为稳中偏冲选择。', star: 4 },
        { schoolName: '福建农林大学', badge: '省属重点', collegeName: '计算机与信息学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 280, range: '270-298', confidence: 'B', tag: '稳中偏冲', note: '录取区间与分数匹配，机会较大，建议重点关注。', star: 4 },
        { schoolName: '集美大学', badge: '省属重点', collegeName: '信息工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 275, range: '265-290', confidence: 'C', tag: '稳中偏冲', note: '复试线偏低，录取较稳定，建议作为稳中偏冲志愿。', star: 3 }
      ]
    },
    {
      name: '稳妥候选',
      desc: '录取概率较高，适合作为保底',
      schools: [
        { schoolName: '福建工程学院', badge: '普通本科', collegeName: '计算机与数学学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 260, range: '250-275', confidence: 'C', tag: '稳妥候选', note: '录取门槛较低，录取机会较大，适合作为保底选择。', star: 3 },
        { schoolName: '闽江学院', badge: '普通本科', collegeName: '计算机与大数据学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 252, range: '242-268', confidence: 'C', tag: '稳妥候选', note: '近年录取较稳定，分数匹配度高，建议保底。', star: 3 },
        { schoolName: '莆田学院', badge: '普通本科', collegeName: '机电与信息工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 248, range: '238-262', confidence: 'C', tag: '稳妥候选', note: '录取门槛较低，录取机会较大，建议保底。', star: 3 }
      ]
    }
  ]
}

export default {
  name: 'AppResults',
  components: { AppHeader },
  data() {
    return {
      activeTab: this.$route.query.tab === 'compare' ? 'compare' : 'result',
      result: fallbackResult,
      filters: [
        { label: '地区', value: '福建' },
        { label: '学校层次', value: '全部层次' },
        { label: '考试组合', value: '22408' },
        { label: '是否有拟录取区间', value: '不限' },
        { label: 'N诺数据完整度', value: '全部' }
      ],
      compareRows: [
        { label: '学校', type: 'name' },
        { label: '专业', key: 'program' },
        { label: '考试组合', key: 'exam' },
        { label: '复试线（2025）', key: 'score' },
        { label: '与复试线差距', type: 'diff', key: 'scoreLineGap' },
        { label: '拟录取区间（总分）', key: 'range' },
        { label: '拟录取均分', key: 'avgScore' },
        { label: '与拟录取均分差距', type: 'diff', key: 'avgScoreGap' },
        { label: '招生人数（含推免）', key: 'quota' },
        { label: 'N诺数据完整度', type: 'confidence' },
        { label: '推荐等级', type: 'fit' },
        { label: '操作', type: 'action' }
      ],
      compareSchools: [
        { name: '复旦大学', program: '计算机科学与技术', exam: '11408：政治 + 英语一 + 数学一 + 408', score: 355, scoreLineGap: -55, range: '355-405', avgScore: 382, avgScoreGap: -82, quota: '120（含推免）', confidence: 'A', fitLevel: '冲刺', fitLevelClass: 'sprint', star: 5 },
        { name: '上海交通大学', program: '计算机科学与技术', exam: '11408：政治 + 英语一 + 数学一 + 408', score: 350, scoreLineGap: -50, range: '350-402', avgScore: 376, avgScoreGap: -76, quota: '160（含推免）', confidence: 'A', fitLevel: '冲刺', fitLevelClass: 'sprint', star: 5 },
        { name: '华东师范大学', program: '计算机科学与技术', exam: '22408：政治 + 英语二 + 数学二 + 408', score: 330, scoreLineGap: -30, range: '330-380', avgScore: 352, avgScoreGap: -52, quota: '85（含推免）', confidence: 'B', fitLevel: '稳中偏冲', fitLevelClass: 'balanced', star: 4 },
        { name: '华中科技大学', program: '计算机科学与技术', exam: '22408：政治 + 英语二 + 数学二 + 408', score: 325, scoreLineGap: -25, range: '325-375', avgScore: 346, avgScoreGap: -46, quota: '110（含推免）', confidence: 'B', fitLevel: '稳中偏冲', fitLevelClass: 'balanced', star: 4 }
      ],
      backupGroups: [
        { name: '冲刺清单', desc: '挑战更高目标，冲一冲更好的院校', theme: 'green', items: [{ name: '复旦大学 · 计算机科学与技术', grade: '完整度A' }, { name: '上海交通大学 · 计算机科学与技术', grade: '完整度A' }, { name: '南京大学 · 计算机科学与技术', grade: '完整度A' }, { name: '浙江大学 · 计算机科学与技术', grade: '完整度A' }] },
        { name: '稳妥清单', desc: '匹配度较高，录取希望较大', theme: 'blue', items: [{ name: '华东师范大学 · 计算机科学与技术', grade: '完整度B' }, { name: '华中科技大学 · 计算机科学与技术', grade: '完整度B' }, { name: '北京邮电大学 · 计算机科学与技术', grade: '完整度B' }, { name: '西安电子科技大学 · 计算机科学与技术', grade: '完整度B' }] },
        { name: '保底候选', desc: '确保有学可上，降低风险', theme: 'orange', items: [{ name: '杭州电子科技大学 · 计算机科学与技术', grade: '完整度C' }, { name: '成都信息工程大学 · 计算机科学与技术', grade: '完整度C' }, { name: '燕山大学 · 计算机科学与技术', grade: '完整度C' }] }
      ]
    }
  },
  computed: {
    currentHeader() {
      return this.activeTab === 'compare' ? 'compare' : 'results'
    }
  },
  created() {
    const cached = window.sessionStorage.getItem('app-recommend-result')
    if (cached) {
      try {
        const parsed = JSON.parse(cached)
        if (parsed && parsed.groups) this.result = parsed
      } catch (e) {}
    }
  },
  watch: {
    '$route.query.tab'(tab) {
      this.activeTab = tab === 'compare' ? 'compare' : 'result'
    }
  },
  methods: {
    dataCompletenessText(level) {
      const map = {
        A: '含复试线、拟录取区间、人数等字段',
        B: '含主要分数字段，部分字段缺失',
        C: '仅有复试线或基础字段'
      }
      return map[level] || '字段完整度待核验'
    }
  }
}
</script>

<style scoped>
.prototype-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f6f9ff 0, #f8fbff 100%);
  color: #111827;
}

.result-layout {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: 24px;
}

.filter-sidebar {
  min-height: calc(100vh - 68px);
  background: #fff;
  border-right: 1px solid #e5edf8;
  padding: 26px 20px;
}

.sidebar-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.sidebar-title strong {
  font-size: 20px;
}

.sidebar-title button,
.section-title button,
.data-alert button,
.detail-link {
  border: 0;
  background: transparent;
  color: #1769f6;
  cursor: pointer;
}

.filter-group {
  margin-bottom: 20px;
}

.filter-group label {
  display: block;
  margin-bottom: 8px;
  color: #273247;
  font-weight: 600;
}

.filter-select {
  height: 44px;
  border: 1px solid #dce5f3;
  border-radius: 6px;
  background: #fbfdff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
}

.filter-button {
  width: 100%;
  height: 40px;
  margin: 10px 0 34px;
}

.tip-box {
  background: #f4f8ff;
  border-radius: 8px;
  padding: 16px;
  color: #627089;
  line-height: 1.7;
}

.tip-box i,
.tip-box strong {
  color: #1769f6;
}

.tip-box p {
  margin: 8px 0 0;
}

.result-main {
  padding: 28px 28px 34px 0;
}

.result-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 24px;
  align-items: center;
  margin-bottom: 20px;
}

.result-head h1 {
  display: inline-block;
  margin: 0 12px 0 0;
  font-size: 28px;
}

.result-head span {
  color: #6b778a;
}

.chips {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.chips span {
  height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: #eef4ff;
  color: #253044;
  border-radius: 6px;
  padding: 0 12px;
}

.data-alert {
  min-height: 42px;
  border: 1px solid #ffdca8;
  border-radius: 6px;
  background: #fff8e8;
  color: #bf6814;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  margin-bottom: 24px;
  line-height: 1.5;
}

.data-alert button {
  margin-left: auto;
  color: #bf6814;
}

.school-section {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 14px;
}

.section-title strong {
  color: #1769f6;
  font-size: 23px;
}

.section-title span {
  color: #6b778a;
}

.section-title button {
  margin-left: auto;
}

.school-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.school-card,
.compare-panel,
.backup-panel {
  background: #fff;
  border: 1px solid #dbe5f4;
  border-radius: 8px;
  box-shadow: 0 10px 28px rgba(34, 73, 135, 0.06);
}

.school-card {
  padding: 16px;
}

.card-top {
  display: grid;
  grid-template-columns: 48px 1fr 28px;
  gap: 12px;
  align-items: start;
}

.school-seal {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 2px solid #80aaff;
  color: #1769f6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
}

.school-card h3 {
  margin: 0 0 7px;
  font-size: 18px;
}

.school-card h3 small {
  margin-left: 8px;
  color: #1769f6;
  background: #edf4ff;
  border-radius: 4px;
  padding: 2px 7px;
  font-size: 12px;
}

.school-card p,
.school-card span {
  margin: 0;
  color: #6b778a;
  line-height: 1.6;
}

.star-btn {
  border: 0;
  background: transparent;
  color: #42526b;
  cursor: pointer;
  font-size: 22px;
}

.score-line {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-top: 1px solid #eef2f7;
  border-bottom: 1px solid #eef2f7;
  margin: 16px 0 12px;
  padding: 10px 0;
}

.score-line div {
  display: flex;
  justify-content: space-between;
  padding-right: 18px;
}

.score-line strong {
  font-size: 17px;
}

.tags {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.tags em,
.grade {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  font-style: normal;
  font-weight: 700;
}

.source {
  color: #1769f6;
  background: #eaf2ff;
}

.grade-a {
  color: #15803d;
  background: #e7f7ed;
  border: 1px solid #a9e5bf;
}

.grade-b {
  color: #1769f6;
  background: #eaf2ff;
  border: 1px solid #b7d1ff;
}

.grade-c {
  color: #d46b08;
  background: #fff3dd;
}

.completeness-note {
  display: block;
  margin-top: 5px;
  color: #6b778a;
  font-size: 12px;
  line-height: 1.35;
}

.risk-tag {
  color: #ef4444;
  background: #fff0f0;
}

.card-note {
  font-size: 14px;
}

.compare-panel,
.backup-panel {
  padding: 16px;
  margin-bottom: 18px;
}

.tabs {
  display: flex;
  border-bottom: 1px solid #e6edf8;
  margin: -16px -16px 14px;
}

.tabs button {
  height: 50px;
  min-width: 126px;
  border: 0;
  background: transparent;
  font-weight: 700;
  cursor: pointer;
}

.tabs button.active {
  color: #1769f6;
  border-bottom: 3px solid #1769f6;
  background: #f8fbff;
}

.compare-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.compare-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.compare-table th,
.compare-table td {
  height: 48px;
  border: 1px solid #e0e7f2;
  text-align: center;
  padding: 0 10px;
}

.compare-table th {
  width: 155px;
  background: #f8fbff;
  text-align: left;
  color: #1f2937;
}

.star {
  color: #d5dbe7;
  font-size: 12px;
}

.star.light {
  color: #f5b51b;
}

.fit-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 70px;
  height: 26px;
  padding: 0 10px;
  border-radius: 4px;
  font-weight: 700;
}

.fit-sprint {
  color: #d93025;
  background: #fff0f0;
}

.fit-balanced {
  color: #d97706;
  background: #fff7e6;
}

.fit-steady {
  color: #1769f6;
  background: #eaf2ff;
}

.fit-safe {
  color: #15803d;
  background: #e7f7ed;
}

.fit-stars {
  display: block;
  margin-top: 3px;
  line-height: 1;
}

.score-diff {
  font-family: "Cascadia Code", "Consolas", monospace;
  font-weight: 800;
}

.score-diff.positive {
  color: #15803d;
}

.score-diff.negative {
  color: #d93025;
}

.table-note {
  color: #7a879a;
  margin: 12px 0 0;
}

.backup-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.backup-card {
  border: 1px solid;
  border-radius: 8px;
  overflow: hidden;
}

.backup-card.green {
  border-color: #bfe9c9;
}

.backup-card.blue {
  border-color: #bed4ff;
}

.backup-card.orange {
  border-color: #ffd7a8;
}

.backup-title {
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  background: #fbfdff;
}

.backup-title strong {
  font-size: 17px;
}

.backup-card.green strong,
.backup-card.green button,
.backup-card.green i {
  color: #169b47;
}

.backup-card.blue strong,
.backup-card.blue button,
.backup-card.blue i {
  color: #1769f6;
}

.backup-card.orange strong,
.backup-card.orange button,
.backup-card.orange i {
  color: #e07818;
}

.backup-title p {
  margin: 6px 0 0;
  color: #6b778a;
}

.backup-card ul {
  list-style: none;
  padding: 12px 16px 8px;
  margin: 0;
}

.backup-card li {
  display: flex;
  justify-content: space-between;
  min-height: 36px;
  align-items: center;
}

.backup-card li em {
  font-style: normal;
  color: #1769f6;
  background: #eef4ff;
  border-radius: 4px;
  padding: 2px 8px;
}

.backup-card button {
  width: 100%;
  height: 42px;
  border: 0;
  border-top: 1px solid #edf2f8;
  background: #fbfdff;
  cursor: pointer;
  font-weight: 700;
}

@media (max-width: 1180px) {
  .result-layout {
    grid-template-columns: 1fr;
  }

  .filter-sidebar {
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid #e5edf8;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
  }

  .sidebar-title,
  .filter-button,
  .tip-box {
    grid-column: 1 / -1;
  }

  .result-main {
    padding: 24px;
  }

  .school-grid,
  .backup-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .filter-sidebar {
    grid-template-columns: 1fr;
    padding: 18px 14px;
  }

  .result-main {
    padding: 18px 14px;
  }

  .result-head {
    grid-template-columns: 1fr;
  }

  .data-alert {
    height: auto;
    min-height: 42px;
    align-items: flex-start;
    padding: 12px;
  }

  .school-grid,
  .backup-grid {
    grid-template-columns: 1fr;
  }

  .compare-panel {
    overflow-x: auto;
  }

  .compare-table {
    min-width: 850px;
  }
}
</style>
