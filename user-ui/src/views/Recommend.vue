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
          <button class="link-more" type="button" @click="router.push('/results?tab=compare')">查看完整数据说明 <i class="el-icon-right"></i></button>
        </aside>
      </section>

      <section class="feature-band">
        <div class="feature-item">
          <span class="feature-icon target"><i class="el-icon-aim"></i></span>
          <div>
            <h3>冲稳保推荐</h3>
            <p>基于目标分数，智能生成"冲、稳、保"三档院校推荐。</p>
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

    <AiChatPanel :visible="aiChatVisible" :candidateIds="candidateResultIds"
      @close="aiChatVisible = false" @fallback="handleAiFallback" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import AiChatPanel from '@/components/AiChatPanel.vue'
import { generateRecommendation, getRecommendationOptions } from '@/api/recommendation'

const router = useRouter()
const userStore = useUserStore()

const generating = ref(false)
const loadingOptions = ref(false)
const regions = ref(['福建', '广东', '浙江', '上海'])
const form = reactive({
  score: 300,
  exam: '22408',
  regions: [],
  majorDirections: [],
  risk: 'balanced',
  scoreRange: 15
})
const aiChatVisible = ref(false)
const candidateResultIds = ref([])

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

function handleAiFallback() {
  // Fallback to rule-based recommendation
}

onMounted(() => {
  loadOptions()
})
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

.input-shell :deep(.el-input__inner),
.input-shell :deep(.el-input-group__append) {
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
  .segmented.wide,
  .segmented.five {
    grid-template-columns: 1fr;
    padding-top: 12px;
  }
}
</style>
