<template>
  <div class="prototype-page">
    <AppHeader :current-page="headerPage" />

    <main class="home-wrap">
      <section class="hero">
        <div class="hero-copy">
          <span class="hero-kicker">408 统考择校工作台</span>
          <h1>用 <span>408</span> 数据，快速圈定目标院校</h1>
          <p>先用规则筛出候选范围，再结合数据完整度和个人目标继续对比。</p>
        </div>
        <div class="hero-metrics">
          <div class="metric-card">
            <span>当前目标分</span>
            <strong>{{ form.score || '-' }}</strong>
            <em>满分 500</em>
          </div>
          <div class="metric-card">
            <span>冲刺范围</span>
            <strong>{{ form.scoreRange === null ? '不限' : `+${form.scoreRange}` }}</strong>
            <em>按拟录取均分</em>
          </div>
          <div class="metric-card">
            <span>考试组合</span>
            <strong>{{ form.exam }}</strong>
            <em>计算机 408</em>
          </div>
        </div>
      </section>

      <section class="main-grid">
        <div class="recommend-panel">
          <div class="panel-title">
            <strong>快速筛选</strong>
            <span>填写你的报考意向，筛出更匹配的候选院校</span>
          </div>

          <div class="form-row score-row">
            <div class="row-label"><i class="el-icon-aim"></i>预计初试总分</div>
            <div class="row-control input-shell">
              <el-input v-model.number="form.score" placeholder="请输入你的预计初试总分">
                <template #append>分（满分500）</template>
              </el-input>
            </div>
            <button class="plain-help" type="button" @click="showScoreHelp">如何设定目标分数?</button>
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
            <div class="row-control input-shell">
              <el-select v-model="form.regions" multiple collapse-tags filterable clearable placeholder="不限地区，可选择报考地区">
                <el-option v-for="region in regions" :key="region" :label="region" :value="region" />
              </el-select>
            </div>
          </div>

          <div class="form-row">
            <div class="row-label"><i class="el-icon-collection-tag"></i>专业方向</div>
            <div class="row-control input-shell">
              <el-select v-model="form.majorDirections" multiple collapse-tags filterable placeholder="选择专业方向（可多选）">
                <el-option label="计算机科学与技术（081200）" value="081200" />
                <el-option label="软件工程（083500）" value="083500" />
                <el-option label="电子信息-计算机方向（085404）" value="085404" />
                <el-option label="电子信息-软件工程方向（085405）" value="085405" />
              </el-select>
            </div>
          </div>

          <div class="form-row">
            <div class="row-label range-label">
              <i class="el-icon-odometer"></i>
              筛选范围
              <el-tooltip
                effect="dark"
                content="按拟录取平均分筛学校。数字表示允许候选均分比你的预计分最多高多少。"
                placement="top"
              >
                <i class="el-icon-question range-help"></i>
              </el-tooltip>
            </div>
            <div class="segmented five">
              <el-tooltip effect="dark" content="拟录取平均分不高于预计分+5的候选都会进入，范围较窄。" placement="top">
                <button :class="{ active: form.scoreRange === 5 }" @click="form.scoreRange = 5">均分+5</button>
              </el-tooltip>
              <el-tooltip effect="dark" content="拟录取平均分不高于预计分+10的候选都会进入，适合小幅冲刺。" placement="top">
                <button :class="{ active: form.scoreRange === 10 }" @click="form.scoreRange = 10">均分+10</button>
              </el-tooltip>
              <el-tooltip effect="dark" content="拟录取平均分不高于预计分+15的候选都会进入，默认冲刺范围。" placement="top">
                <button :class="{ active: form.scoreRange === 15 }" @click="form.scoreRange = 15">均分+15</button>
              </el-tooltip>
              <el-tooltip effect="dark" content="拟录取平均分不高于预计分+20的候选都会进入，范围更宽、风险更高。" placement="top">
                <button :class="{ active: form.scoreRange === 20 }" @click="form.scoreRange = 20">均分+20</button>
              </el-tooltip>
              <el-tooltip effect="dark" content="不按拟录取平均分限制范围，候选更多，需要自己再判断风险。" placement="top">
                <button :class="{ active: form.scoreRange === null }" @click="form.scoreRange = null">不限</button>
              </el-tooltip>
            </div>
          </div>

          <el-button class="primary-cta" type="primary" :loading="generating" @click="startRecommend">
            <i class="el-icon-magic-stick"></i> 开始筛选
          </el-button>
          <div class="privacy-note"><i class="el-icon-lock"></i> 信息仅用于筛选，不会泄露或用于其他用途</div>
        </div>

        <aside class="notice-panel">
          <h3><i class="el-icon-message-solid"></i>数据可信提示</h3>
          <div class="notice-card blue">
            <div class="notice-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" focusable="false">
                <ellipse cx="12" cy="6" rx="7" ry="3" />
                <path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6" />
                <path d="M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6" />
              </svg>
            </div>
            <div>
              <h4>当前数据来源：N诺（第三方整理）</h4>
              <p>数据可能遗漏或错误，最终请以院校官网和招生公告为准。</p>
              <span>第三方数据</span>
            </div>
          </div>
          <div class="notice-card orange">
            <div class="notice-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" focusable="false">
                <path d="M12 3 22 20H2L12 3Z" />
                <path d="M12 9v5" />
                <path d="M12 17h.01" />
              </svg>
            </div>
            <div>
              <h4>复试线不等于最低录取分</h4>
              <p>复试线只是进入复试的最低要求，实际录取最低分通常更高。</p>
              <span>重要提示</span>
            </div>
          </div>
          <div class="notice-card green">
            <div class="notice-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" focusable="false">
                <path d="M4 19V5" />
                <path d="M4 19h16" />
                <path d="m7 15 4-4 3 3 5-7" />
                <path d="M16 7h3v3" />
              </svg>
            </div>
            <div>
              <h4>推荐结果仅供参考</h4>
              <p>推荐学校不代表只有这些学校可以报，需结合个人目标继续扩展筛选。</p>
              <span>仅供参考</span>
            </div>
          </div>
          <button class="link-more" type="button" @click="router.push('/results?tab=compare')">查看完整数据说明 <i class="el-icon-right"></i></button>
        </aside>
      </section>

      <section class="feature-band">
        <div class="feature-item">
          <span class="feature-icon target" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false">
              <circle cx="12" cy="12" r="8" />
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v4" />
              <path d="M12 18v4" />
              <path d="M2 12h4" />
              <path d="M18 12h4" />
            </svg>
          </span>
          <div>
            <h3>冲稳保推荐</h3>
            <p>基于目标分数，智能生成"冲、稳、保"三档院校推荐。</p>
            <em>更合理的志愿策略</em>
          </div>
        </div>
        <div class="feature-item">
          <span class="feature-icon shield" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false">
              <path d="M12 3 19 6v5c0 5-3.2 8.2-7 10-3.8-1.8-7-5-7-10V6l7-3Z" />
              <path d="m8.5 12 2.3 2.3 4.9-5" />
            </svg>
          </span>
          <div>
            <h3>N诺数据完整度标签</h3>
            <p>按复试线、拟录取区间、人数等字段完整程度标注 A/B/C。</p>
            <em>完整度说明清晰</em>
          </div>
        </div>
        <div class="feature-item">
          <span class="feature-icon bot" aria-hidden="true">
            <svg viewBox="0 0 24 24" focusable="false">
              <rect x="5" y="7" width="14" height="11" rx="3" />
              <path d="M12 7V4" />
              <path d="M8.5 12h.01" />
              <path d="M15.5 12h.01" />
              <path d="M9 16h6" />
              <path d="M3 11v3" />
              <path d="M21 11v3" />
            </svg>
          </span>
          <div>
            <h3>AI 推荐独立使用</h3>
            <p>需要 AI 顾问时请进入顶部「AI 推荐」，系统会按你的画像说明推荐依据。</p>
            <em>筛选页只保留规则筛选</em>
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

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import { generateRecommendation, getRecommendationOptions } from '@/api/recommendation'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const generating = ref(false)
const loadingOptions = ref(false)
const headerPage = computed(() => route.path === '/' ? 'home' : 'recommend')
const regions = ref(['福建', '广东', '浙江', '上海'])
const form = reactive({
  score: 300,
  exam: '22408',
  regions: [],
  majorDirections: [],
  risk: 'balanced',
  scoreRange: 15
})
function loadOptions() {
  loadingOptions.value = true
  getRecommendationOptions().then(res => {
    const data = res.data || {}
    regions.value = data.regions && data.regions.length ? data.regions : regions.value
    if (data.defaultProfile) {
      form.score = data.defaultProfile.estimatedScore || form.score
      form.exam = data.defaultProfile.examCombo || form.exam
    }
  }).finally(() => { loadingOptions.value = false })
}

function showScoreHelp() {
  ElMessageBox.alert(
    '筛选范围按拟录取平均分判断。比如预计分300、选择"均分+15"，系统会筛出拟录取平均分315及以下的候选；数字越大，能进入的候选越多，也更适合找冲刺学校。',
    '筛选范围说明',
    { confirmButtonText: '知道了' }
  )
}

function startRecommend() {
  if (!form.score) {
    ElMessage.warning('请输入预计初试总分')
    return
  }
  generating.value = true
  const payload = {
    estimatedScore: form.score || 300,
    examCombo: form.exam,
    targetRegions: form.regions,
    majorDirections: form.majorDirections,
    riskPreference: form.risk,
    scoreRange: form.scoreRange,
    includeIncompleteData: true,
    pageSizePerGroup: 12
  }
  generateRecommendation(payload).then(res => {
    const data = res.data || {}
    window.sessionStorage.setItem('app-recommend-result', JSON.stringify(data))
    if (data.recommendationId) {
      window.sessionStorage.setItem('app-recommend-id', data.recommendationId)
    }
    router.push({ path: '/results', query: data.recommendationId ? { id: data.recommendationId } : {} })
  }).finally(() => { generating.value = false })
}

onMounted(() => {
  loadOptions()
})
</script>

<style scoped>
.prototype-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 12% 8%, rgba(37, 99, 235, 0.18), transparent 30%),
    radial-gradient(circle at 86% 0%, rgba(14, 165, 233, 0.18), transparent 32%),
    linear-gradient(180deg, #f7fbff 0, #eef6ff 360px, #f8fbff 100%);
  color: #111827;
}

.home-wrap {
  max-width: 1420px;
  margin: 0 auto;
  padding: 18px 32px 16px;
}

.hero {
  min-height: 132px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 26px;
  margin-bottom: 16px;
  padding: 22px 26px;
  border-radius: 18px;
  color: #fff;
  background:
    radial-gradient(circle at 82% 28%, rgba(255, 255, 255, 0.3), transparent 24%),
    linear-gradient(135deg, #155eef 0%, #1570ef 48%, #0ea5e9 100%);
  box-shadow: 0 26px 58px rgba(37, 99, 235, 0.24);
  overflow: hidden;
  position: relative;
  animation: page-rise 0.48s ease both;
}

.hero::after {
  content: "";
  width: 260px;
  height: 260px;
  border-radius: 50%;
  border: 36px solid rgba(255, 255, 255, 0.08);
  position: absolute;
  right: -96px;
  top: -74px;
}

.hero-copy {
  position: relative;
  z-index: 1;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.9);
  font-size: 13px;
  font-weight: 700;
  backdrop-filter: blur(8px);
}

.hero h1 {
  margin: 10px 0 6px;
  font-size: 34px;
  line-height: 1.18;
  font-weight: 800;
  letter-spacing: 0;
}

.hero h1 span {
  color: #fff;
  font-size: 46px;
  text-shadow: 0 10px 26px rgba(15, 23, 42, 0.18);
}

.hero p {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
}

.hero-metrics {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  gap: 10px;
  min-width: 360px;
}

.metric-card {
  min-height: 86px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 16px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.14);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(12px);
}

.metric-card span,
.metric-card em {
  display: block;
  color: rgba(255, 255, 255, 0.72);
  font-style: normal;
  font-size: 12px;
}

.metric-card strong {
  display: block;
  margin: 5px 0 2px;
  color: #fff;
  font-size: 26px;
  line-height: 1.1;
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.62fr) minmax(340px, 0.88fr);
  gap: 18px;
  align-items: stretch;
  animation: page-rise 0.54s ease 0.06s both;
}

.recommend-panel,
.notice-panel,
.feature-band,
.data-footer {
  border: 1px solid rgba(199, 213, 235, 0.78);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 20px 50px rgba(29, 78, 150, 0.1);
  backdrop-filter: blur(12px);
}

.recommend-panel {
  border-radius: 18px;
  padding: 18px 22px 16px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-title strong {
  font-size: 21px;
}

.panel-title span {
  color: #7b8798;
  font-size: 14px;
}

.form-row {
  min-height: 50px;
  border: 1px solid #e3ebf7;
  border-radius: 13px;
  display: grid;
  grid-template-columns: 150px 1fr;
  align-items: center;
  margin-bottom: 9px;
  background: #fff;
  overflow: hidden;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.form-row:hover {
  transform: translateY(-1px);
  border-color: #c9d9f2;
  box-shadow: 0 10px 24px rgba(29, 78, 150, 0.08);
}

.score-row {
  grid-template-columns: 150px 1fr 180px;
}

.row-label {
  height: 100%;
  padding: 0 16px;
  background: linear-gradient(90deg, #f8fbff, #fff);
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
  height: 36px;
  margin: 0 14px;
  display: flex;
  align-items: center;
}

.input-shell :deep(.el-input__inner),
.input-shell :deep(.el-input-group__append) {
  border-color: #d9e2f2;
  height: 36px;
  background: #fff;
}

.input-shell :deep(.el-select),
.input-shell :deep(.el-input) {
  width: 100%;
}

.input-shell :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #d9e2f2 inset;
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
  transition: color 0.18s ease, transform 0.18s ease;
}

.plain-help:hover {
  color: #0f55d9;
  transform: translateX(2px);
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

.segmented.five {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.range-label {
  gap: 8px;
}

.range-help {
  font-size: 15px;
  color: #7aa8ff;
  cursor: help;
}

.segmented button {
  height: 36px;
  border: 1px solid #d7e3f4;
  border-radius: 10px;
  background: linear-gradient(180deg, #fff, #fbfdff);
  color: #38445a;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.16s ease, border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.segmented button:hover {
  transform: translateY(-1px);
  border-color: #9dbdff;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.1);
}

.segmented button.active {
  border-color: #6098ff;
  color: #1769f6;
  background: #f1f6ff;
  box-shadow: 0 0 0 3px rgba(23, 105, 246, 0.09);
}

.segmented small {
  display: block;
  margin-top: 2px;
  color: #718096;
  font-size: 11px;
}

.primary-cta {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border: 0;
  border-radius: 14px;
  font-size: 18px;
  font-weight: 800;
  background: linear-gradient(135deg, #1769f6, #1d8cff);
  box-shadow: 0 18px 34px rgba(37, 99, 235, 0.26);
  transition: transform 0.18s ease, box-shadow 0.18s ease, filter 0.18s ease;
}

.primary-cta:hover {
  transform: translateY(-2px);
  box-shadow: 0 24px 42px rgba(37, 99, 235, 0.32);
  filter: saturate(1.05);
}

.privacy-note {
  margin-top: 10px;
  text-align: center;
  color: #98a4b5;
  font-size: 13px;
}

.notice-panel {
  border-radius: 18px;
  padding: 18px 18px 14px;
}

.notice-panel h3 {
  margin: 0 0 14px;
  color: #1769f6;
  font-size: 20px;
}

.notice-card {
  min-height: 82px;
  border: 1px solid #edf2fa;
  border-radius: 14px;
  padding: 12px;
  margin-bottom: 10px;
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 12px;
  align-items: center;
  background: #fff;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.notice-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(29, 78, 150, 0.08);
}

.notice-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.notice-icon svg,
.feature-icon svg {
  width: 23px;
  height: 23px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
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
  padding-top: 6px;
  transition: transform 0.18s ease, color 0.18s ease;
}

.link-more:hover {
  color: #0f55d9;
  transform: translateX(2px);
}

.feature-band {
  margin-top: 14px;
  border-radius: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  animation: page-rise 0.58s ease 0.12s both;
}

.feature-item {
  min-height: 94px;
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 18px;
  align-items: center;
  padding: 14px 24px;
  border-right: 1px solid #dbe5f4;
  transition: background 0.18s ease;
}

.feature-item:hover {
  background: rgba(239, 246, 255, 0.62);
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
}

.feature-icon svg {
  width: 30px;
  height: 30px;
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
  margin-top: 10px;
  border-radius: 14px;
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #6b778a;
}

@keyframes page-rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1100px) {
  .hero {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-metrics {
    min-width: 0;
  }

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
    padding: 14px 14px 20px;
  }

  .hero {
    padding: 20px 18px;
    border-radius: 16px;
    gap: 16px;
  }

  .hero h1 {
    font-size: 26px;
  }

  .hero h1 span {
    font-size: 34px;
  }

  .hero-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .metric-card {
    min-height: 70px;
    padding: 10px;
    border-radius: 13px;
  }

  .metric-card span,
  .metric-card em {
    font-size: 11px;
  }

  .metric-card strong {
    font-size: 22px;
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
  .segmented.wide,
  .segmented.five {
    grid-template-columns: 1fr;
    padding-top: 12px;
  }

  .feature-item {
    grid-template-columns: 58px 1fr;
    padding: 14px 16px;
  }
}
</style>
