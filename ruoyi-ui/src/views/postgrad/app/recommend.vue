<template>
  <div class="prototype-page">
    <AppHeader current-page="recommend" />

    <main class="home-wrap">
      <section class="hero">
        <div class="hero-copy">
          <h1>用 <span>408</span> 数据，科学选择你的研究生院校</h1>
          <p>基于历年复试数据与录取情况，智能推荐更适合你的目标院校</p>
        </div>
      </section>

      <section class="main-grid">
        <div class="recommend-panel">
          <div class="panel-title">
            <strong>快速推荐</strong>
            <span>填写你的报考意向，获取个性化择校建议</span>
          </div>

          <div class="form-row score-row">
            <div class="row-label"><i class="el-icon-aim"></i>预计初试总分</div>
            <div class="row-control input-shell">
              <el-input v-model.number="form.score" placeholder="请输入你的预计初试总分">
                <template slot="append">分（满分500）</template>
              </el-input>
            </div>
            <button class="plain-help" type="button">如何设定目标分数?</button>
          </div>

          <div class="form-row">
            <div class="row-label"><i class="el-icon-edit-outline"></i>考试组合</div>
            <div class="segmented wide">
              <button :class="{ active: form.exam === '11408' }" @click="form.exam = '11408'">
                <i class="el-icon-success"></i>11408（数学一 + 英语一 + 408）
              </button>
              <button :class="{ active: form.exam === '22408' }" @click="form.exam = '22408'">22408（数学二 + 英语二 + 408）</button>
            </div>
          </div>

          <div class="form-row">
            <div class="row-label"><i class="el-icon-location-outline"></i>地区</div>
            <div class="row-control select-shell">福建、广东、浙江、上海 <i class="el-icon-arrow-down"></i></div>
          </div>

          <div class="form-row">
            <div class="row-label"><i class="el-icon-notebook-2"></i>学习方式</div>
            <div class="segmented">
              <button :class="{ active: form.studyMode === '不限' }" @click="form.studyMode = '不限'">不限</button>
              <button :class="{ active: form.studyMode === '全日制' }" @click="form.studyMode = '全日制'">全日制</button>
              <button :class="{ active: form.studyMode === '非全日制' }" @click="form.studyMode = '非全日制'">非全日制</button>
            </div>
          </div>

          <div class="form-row">
            <div class="row-label"><i class="el-icon-collection-tag"></i>专业方向</div>
            <div class="row-control select-shell">计算机科学与技术、软件工程、人工智能 <i class="el-icon-arrow-down"></i></div>
          </div>

          <div class="form-row">
            <div class="row-label"><i class="el-icon-success"></i>风险偏好</div>
            <div class="segmented risk">
              <button :class="{ active: form.risk === '稳妥优先' }" @click="form.risk = '稳妥优先'">
                稳妥优先<small>录取把握更大</small>
              </button>
              <button :class="{ active: form.risk === '平衡兼顾' }" @click="form.risk = '平衡兼顾'">
                平衡兼顾<small>冲稳保组合</small>
              </button>
              <button :class="{ active: form.risk === '冲刺优先' }" @click="form.risk = '冲刺优先'">
                冲刺优先<small>挑战更高院校</small>
              </button>
            </div>
          </div>

          <el-button class="primary-cta" type="primary" :loading="generating" @click="startRecommend">
            <i class="el-icon-magic-stick"></i> 开始推荐
          </el-button>
          <div class="privacy-note"><i class="el-icon-lock"></i> 信息仅用于推荐，不会泄露或用于其他用途</div>
        </div>

        <aside class="notice-panel">
          <h3><i class="el-icon-message-solid"></i>使用须知</h3>
          <div class="notice-card blue">
            <div class="notice-icon"><i class="el-icon-coin"></i></div>
            <div>
              <h4>当前数据来源：N诺（第三方整理）</h4>
              <p>数据可能遗漏或错误，最终请以院校官网和招生公告为准。</p>
              <span>第三方数据</span>
            </div>
          </div>
          <div class="notice-card orange">
            <div class="notice-icon"><i class="el-icon-warning-outline"></i></div>
            <div>
              <h4>复试线不等于最低录取分</h4>
              <p>复试线只是进入复试的最低要求，实际录取最低分通常更高。</p>
              <span>重要提示</span>
            </div>
          </div>
          <div class="notice-card green">
            <div class="notice-icon"><i class="el-icon-data-analysis"></i></div>
            <div>
              <h4>推荐结果仅供参考</h4>
              <p>推荐学校不代表只有这些学校可以报，需结合个人目标继续扩展筛选。</p>
              <span>仅供参考</span>
            </div>
          </div>
          <button class="link-more" type="button" @click="$router.push('/app/results?tab=compare')">查看完整数据说明 <i class="el-icon-right"></i></button>
        </aside>
      </section>

      <section class="feature-band">
        <div class="feature-item">
          <span class="feature-icon target"><i class="el-icon-aim"></i></span>
          <div>
            <h3>冲稳保推荐</h3>
            <p>基于目标分数，智能生成“冲、稳、保”三档院校推荐。</p>
            <em>更合理的志愿策略</em>
          </div>
        </div>
        <div class="feature-item">
          <span class="feature-icon shield"><i class="el-icon-success"></i></span>
          <div>
            <h3>N诺数据完整度标签</h3>
            <p>按复试线、拟录取区间、人数等字段完整程度标注 A/B/C。</p>
            <em>完整度说明清晰</em>
          </div>
        </div>
        <div class="feature-item">
          <span class="feature-icon bot"><i class="el-icon-cpu"></i></span>
          <div>
            <h3>AI 推荐解读</h3>
            <p>不仅给出推荐结果，更提供录取概率分析、分数趋势和报考建议。</p>
            <em>更懂你的 AI 助手</em>
          </div>
        </div>
      </section>

      <footer class="data-footer">
        <i class="el-icon-info"></i>
        本平台专注计算机考研（408统考）数据分析与择校推荐，覆盖院校、专业、分数、人数等核心维度。
      </footer>
    </main>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader'

const mockResult = {
  groups: [
    {
      name: '冲刺',
      desc: '录取概率较低，但仍有机会',
      schools: [
        { schoolName: '厦门大学', badge: '985 211 双一流', collegeName: '信息学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 320, range: '315-336', confidence: 'B', tag: '冲刺', note: '近年复试线较高，300分有机会进入复试。', star: 4 },
        { schoolName: '福州大学', badge: '211 双一流', collegeName: '计算机与大数据学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 305, range: '295-322', confidence: 'B', tag: '冲刺', note: '专业实力较强，竞争激烈，建议冲刺尝试。', star: 4 },
        { schoolName: '华侨大学', badge: '中央部属', collegeName: '信息科学与工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 300, range: '290-316', confidence: 'B', tag: '冲刺', note: '复试线接近300分，存在机会，需关注复试表现。', star: 4 }
      ]
    },
    {
      name: '稳中偏冲',
      desc: '有一定机会，需合理评估',
      schools: [
        { schoolName: '福建师范大学', badge: '省属重点', collegeName: '计算机与网络空间安全学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 285, range: '276-304', confidence: 'B', tag: '稳中偏冲', note: '复试线与分数匹配，近年录取较稳。', star: 4 },
        { schoolName: '福建农林大学', badge: '省属重点', collegeName: '计算机与信息学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 280, range: '270-298', confidence: 'B', tag: '稳中偏冲', note: '拟录取区间与分数匹配，机会较大。', star: 4 },
        { schoolName: '集美大学', badge: '省属重点', collegeName: '信息工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 275, range: '265-290', confidence: 'C', tag: '稳中偏冲', note: '录取相对稳定，建议作为稳中偏冲志愿。', star: 3 }
      ]
    },
    {
      name: '稳妥候选',
      desc: '录取概率较高，适合作为保底',
      schools: [
        { schoolName: '福建工程学院', badge: '普通本科', collegeName: '计算机与数学学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 260, range: '250-275', confidence: 'C', tag: '稳妥候选', note: '录取门槛较低，录取机会较大。', star: 3 },
        { schoolName: '闽江学院', badge: '普通本科', collegeName: '计算机与大数据学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 252, range: '242-268', confidence: 'C', tag: '稳妥候选', note: '近年录取较稳定，分数匹配度高。', star: 3 },
        { schoolName: '莆田学院', badge: '普通本科', collegeName: '机电与信息工程学院', programName: '计算机科学与技术', exam: '22408', province: '福建', scoreLine: 248, range: '238-262', confidence: 'C', tag: '稳妥候选', note: '录取机会较大，建议保底。', star: 3 }
      ]
    }
  ]
}

export default {
  name: 'AppRecommend',
  components: { AppHeader },
  data() {
    return {
      generating: false,
      form: {
        score: 300,
        exam: '22408',
        studyMode: '不限',
        risk: '平衡兼顾'
      }
    }
  },
  methods: {
    startRecommend() {
      this.generating = true
      const payload = {
        score: this.form.score || 300,
        exam: this.form.exam,
        studyMode: this.form.studyMode,
        risk: this.form.risk,
        region: '福建',
        source: 'N诺（第三方整理）',
        generatedAt: '2026-05-23',
        groups: mockResult.groups
      }
      window.sessionStorage.setItem('app-recommend-result', JSON.stringify(payload))
      window.sessionStorage.setItem('app-filter-scoreRange', '20')
      setTimeout(() => {
        this.generating = false
        this.$router.push('/app/results')
      }, 350)
    }
  }
}
</script>

<style scoped>
.prototype-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 80% 2%, rgba(55, 133, 255, 0.16), transparent 34%),
    linear-gradient(180deg, #eff6ff 0, #f7fbff 280px, #f8fbff 100%);
  color: #111827;
}

.home-wrap {
  max-width: 1380px;
  margin: 0 auto;
  padding: 14px 32px 12px;
}

.hero {
  min-height: 58px;
  display: flex;
  align-items: flex-start;
}

.hero h1 {
  margin: 0 0 4px;
  font-size: 30px;
  line-height: 1.25;
  font-weight: 800;
  letter-spacing: 0;
}

.hero h1 span {
  color: #1769f6;
  font-size: 40px;
}

.hero p {
  margin: 0;
  color: #637083;
  font-size: 14px;
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(360px, 1fr);
  gap: 22px;
  align-items: stretch;
}

.recommend-panel,
.notice-panel,
.feature-band,
.data-footer {
  border: 1px solid rgba(199, 213, 235, 0.78);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 18px 42px rgba(34, 73, 135, 0.08);
  backdrop-filter: blur(8px);
}

.recommend-panel {
  border-radius: 12px;
  padding: 14px 20px 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 10px;
}

.panel-title strong {
  font-size: 20px;
}

.panel-title span {
  color: #7b8798;
  font-size: 14px;
}

.form-row {
  min-height: 44px;
  border: 1px solid #e7edf5;
  border-radius: 9px;
  display: grid;
  grid-template-columns: 164px 1fr;
  align-items: center;
  margin-bottom: 6px;
  background: #fff;
  overflow: hidden;
}

.score-row {
  grid-template-columns: 164px 1fr 190px;
}

.row-label {
  height: 100%;
  padding: 0 14px;
  background: linear-gradient(90deg, #fbfdff, #fff);
  border-right: 1px solid #eef2f7;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  color: #253044;
}

.row-label i {
  color: #1769f6;
  font-size: 20px;
}

.row-control {
  height: 34px;
  margin: 0 14px;
  display: flex;
  align-items: center;
}

.input-shell /deep/ .el-input__inner,
.input-shell /deep/ .el-input-group__append {
  border-color: #d9e2f2;
  height: 34px;
  background: #fff;
}

.select-shell {
  border: 1px solid #dfe7f3;
  border-radius: 7px;
  color: #9aa6b8;
  justify-content: space-between;
  padding: 0 14px;
}

.plain-help {
  border: 0;
  background: transparent;
  color: #1769f6;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.segmented {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 0 14px;
}

.segmented.wide {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.segmented button {
  height: 34px;
  border: 1px solid #dfe7f3;
  border-radius: 7px;
  background: #fff;
  color: #38445a;
  font-weight: 600;
  cursor: pointer;
}

.segmented button.active {
  border-color: #6098ff;
  color: #1769f6;
  background: #f3f7ff;
  box-shadow: 0 0 0 2px rgba(23, 105, 246, 0.08);
}

.segmented small {
  display: block;
  margin-top: 2px;
  color: #718096;
  font-size: 11px;
}

.risk button {
  height: 38px;
}

.primary-cta {
  width: 100%;
  height: 38px;
  margin-top: 4px;
  border: 0;
  border-radius: 8px;
  font-size: 18px;
  font-weight: 800;
  background: linear-gradient(90deg, #1769f6, #2f7bff);
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.24);
}

.privacy-note {
  margin-top: 8px;
  text-align: center;
  color: #98a4b5;
  font-size: 13px;
}

.notice-panel {
  border-radius: 14px;
  padding: 14px 16px 12px;
}

.notice-panel h3 {
  margin: 0 0 10px;
  color: #1769f6;
  font-size: 19px;
}

.notice-card {
  min-height: 68px;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 6px;
  display: grid;
  grid-template-columns: 52px 1fr;
  gap: 10px;
  align-items: center;
  background: #fff;
}

.notice-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.notice-card h4 {
  margin: 0 0 2px;
  font-size: 15px;
}

.notice-card p {
  margin: 0 0 4px;
  color: #637083;
  line-height: 1.35;
  font-size: 12px;
}

.notice-card span,
.feature-item em {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 5px;
  font-style: normal;
  font-size: 13px;
  font-weight: 700;
}

.notice-card.blue .notice-icon,
.notice-card.blue span {
  background: #eaf2ff;
  color: #1769f6;
}

.notice-card.orange .notice-icon,
.notice-card.orange span {
  background: #fff0e5;
  color: #e86b18;
}

.notice-card.green .notice-icon,
.notice-card.green span {
  background: #e8f8f1;
  color: #0f9b6c;
}

.link-more {
  width: 100%;
  border: 0;
  background: transparent;
  color: #1769f6;
  font-weight: 700;
  cursor: pointer;
  padding-top: 4px;
}

.feature-band {
  margin-top: 10px;
  border-radius: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.feature-item {
  min-height: 88px;
  display: grid;
  grid-template-columns: 82px 1fr;
  gap: 22px;
  align-items: center;
  padding: 10px 24px;
  border-right: 1px solid #dbe5f4;
}

.feature-item:last-child {
  border-right: 0;
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
}

.feature-icon.target {
  background: #dce9ff;
  color: #1769f6;
}

.feature-icon.shield {
  background: #daf7ef;
  color: #0f9b93;
}

.feature-icon.bot {
  background: #e9e5ff;
  color: #6650d8;
}

.feature-item h3 {
  margin: 0 0 5px;
  font-size: 16px;
}

.feature-item p {
  margin: 0 0 7px;
  color: #637083;
  line-height: 1.45;
  font-size: 13px;
}

.feature-item em {
  background: #eef5ff;
  color: #1769f6;
}

.data-footer {
  margin-top: 8px;
  border-radius: 12px;
  min-height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #6b778a;
}

@media (max-width: 1100px) {
  .main-grid,
  .feature-band {
    grid-template-columns: 1fr;
  }

  .feature-item {
    border-right: 0;
    border-bottom: 1px solid #dbe5f4;
  }

  .feature-item:last-child {
    border-bottom: 0;
  }
}

@media (max-width: 760px) {
  .home-wrap {
    padding: 28px 14px;
  }

  .hero h1 {
    font-size: 27px;
  }

  .hero h1 span {
    font-size: 35px;
  }

  .recommend-panel,
  .notice-panel {
    padding: 18px 14px;
  }

  .panel-title {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .form-row,
  .score-row {
    grid-template-columns: 1fr;
    padding-bottom: 12px;
  }

  .row-label {
    min-height: 46px;
    border-right: 0;
    border-bottom: 1px solid #eef2f7;
  }

  .plain-help {
    padding: 0 14px;
  }

  .segmented,
  .segmented.wide {
    grid-template-columns: 1fr;
    padding-top: 12px;
  }
}
</style>
