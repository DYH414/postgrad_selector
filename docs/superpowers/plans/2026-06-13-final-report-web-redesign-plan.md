# Final Report Web Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `/ai-report-v2/:id` as a formal read-only web report that looks like a deliverable, without adding PDF/Excel/share functionality.

**Architecture:** Keep `ReportView.vue` as the route-level data loader and move report rendering into focused components under `user-ui/src/views/ai-recommend-v2/components/report/`. Do not reuse `DraftCandidateCard` in the final report; create report-only read components that consume the existing `ReportVO -> tiers -> candidates -> fact/opinion` structure with null-safe fallbacks.

**Tech Stack:** Vue 3 Composition API, Vue Router, Element Plus, existing `user-ui` Vite build, Node-based layout checks.

---

## Files

Create:

- `user-ui/src/views/ai-recommend-v2/components/report/ReportDocument.vue`
- `user-ui/src/views/ai-recommend-v2/components/report/ReportCover.vue`
- `user-ui/src/views/ai-recommend-v2/components/report/ReportExecutiveSummary.vue`
- `user-ui/src/views/ai-recommend-v2/components/report/ReportProfileBasis.vue`
- `user-ui/src/views/ai-recommend-v2/components/report/ReportTierSection.vue`
- `user-ui/src/views/ai-recommend-v2/components/report/ReportCandidateCard.vue`
- `user-ui/src/views/ai-recommend-v2/components/report/ReportDataNotice.vue`

Modify:

- `user-ui/src/views/ai-recommend-v2/components/ReportView.vue`
- `user-ui/src/views/AiRecommend.layout.test.mjs`

Do not modify:

- Backend report API.
- `user-ui/src/views/ai-recommend-v2/components/DraftCandidateCard.vue` except if a separate unrelated task requires it.
- `sql/backups/`.

---

## Task 1: Add Report Layout Contract Tests

**Files:**

- Modify: `user-ui/src/views/AiRecommend.layout.test.mjs`

- [ ] **Step 1: Add file reads for the final report files**

Add these reads near the existing `workspace`, `profile`, `chat`, and `candidates` reads:

```js
const reportView = readFileSync(join(currentDir, 'ai-recommend-v2/components/ReportView.vue'), 'utf8')
const reportDocument = readFileSync(join(currentDir, 'ai-recommend-v2/components/report/ReportDocument.vue'), 'utf8')
const reportCandidate = readFileSync(join(currentDir, 'ai-recommend-v2/components/report/ReportCandidateCard.vue'), 'utf8')
```

- [ ] **Step 2: Add route-level assertions**

Add these assertions after the AI workbench route assertions:

```js
assert.match(router, /path:\s*'\/ai-report-v2\/:id'/)
assert.match(router, /component:\s*\(\)\s*=>\s*import\('@\/views\/ai-recommend-v2\/components\/ReportView\.vue'\)/)
```

- [ ] **Step 3: Add final-report component assertions**

Add these assertions before the final `console.log`:

```js
assert.match(reportView, /import ReportDocument from '\.\/report\/ReportDocument\.vue'/)
assert.match(reportView, /<ReportDocument/)
assert.doesNotMatch(reportView, /DraftCandidateCard/)
assert.match(reportDocument, /<ReportCover/)
assert.match(reportDocument, /<ReportExecutiveSummary/)
assert.match(reportDocument, /<ReportProfileBasis/)
assert.match(reportDocument, /<ReportTierSection/)
assert.match(reportDocument, /<ReportDataNotice/)
assert.match(reportCandidate, /const fact = computed\(\(\) => props\.candidate\?\.fact \|\| \{\}\)/)
assert.match(reportCandidate, /const opinion = computed\(\(\) => props\.candidate\?\.opinion \|\| \{\}\)/)
assert.doesNotMatch(reportCandidate, /问 AI|移出草稿|replace|remove/)
```

- [ ] **Step 4: Run the test and confirm it fails**

Run:

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui\src\views\AiRecommend.layout.test.mjs
```

Expected: FAIL because `components/report/ReportDocument.vue` and `ReportCandidateCard.vue` do not exist yet.

- [ ] **Step 5: Commit only after Task 2-4 make the test pass**

Do not commit this failing test by itself.

---

## Task 2: Refactor ReportView Into A Route Shell

**Files:**

- Modify: `user-ui/src/views/ai-recommend-v2/components/ReportView.vue`
- Create later in Task 3: `user-ui/src/views/ai-recommend-v2/components/report/ReportDocument.vue`

- [ ] **Step 1: Replace the current ReportView template**

Replace the current template with this route shell:

```vue
<template>
  <div class="report-page">
    <AppHeader current-page="ai" />

    <main class="report-shell">
      <div class="report-toolbar">
        <el-button text class="back-btn" @click="goBack">← 返回工作台</el-button>
      </div>

      <section v-if="loading" class="report-state">
        <i class="el-icon-loading"></i>
        <span>报告加载中...</span>
      </section>

      <section v-else-if="error" class="report-state error">
        <strong>报告加载失败</strong>
        <p>请返回工作台后重试。</p>
        <el-button type="primary" @click="goBack">返回工作台</el-button>
      </section>

      <section v-else-if="!report" class="report-state">
        <strong>报告暂不可用</strong>
        <p>请返回工作台重新生成报告。</p>
        <el-button type="primary" @click="goBack">返回工作台</el-button>
      </section>

      <ReportDocument v-else :report="report" />
    </main>
  </div>
</template>
```

- [ ] **Step 2: Replace the current script**

Use this script:

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getReport } from '@/api/recommend-v2'
import AppHeader from '@/components/AppHeader.vue'
import ReportDocument from './report/ReportDocument.vue'

const route = useRoute()
const router = useRouter()
const report = ref(null)
const loading = ref(false)
const error = ref(false)

function goBack() {
  router.push('/ai-recommend')
}

onMounted(async () => {
  const id = route.params.id
  if (!id) {
    error.value = true
    return
  }

  loading.value = true
  error.value = false
  try {
    const res = await getReport(id)
    report.value = res.data || null
  } catch (e) {
    console.error('Failed to load report', e)
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>
```

- [ ] **Step 3: Replace the current ReportView styles**

Use this style block:

```vue
<style scoped>
.report-page {
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f6f9fe 0%, #eef4fb 100%);
  color: #10213f;
}

.report-shell {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 24px 56px;
}

.report-toolbar {
  margin-bottom: 14px;
}

.back-btn {
  color: #425b7c;
  font-weight: 700;
}

.report-state {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(39, 86, 166, .08);
  color: #607592;
}

.report-state strong {
  color: #10213f;
  font-size: 18px;
}

.report-state p {
  margin: 0;
}

.report-state.error {
  color: #b55c00;
}

@media (max-width: 768px) {
  .report-shell {
    padding: 16px 12px 36px;
  }
}
</style>
```

- [ ] **Step 4: Run the layout test**

Run:

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui\src\views\AiRecommend.layout.test.mjs
```

Expected: still FAIL until Task 3 and Task 4 create report components.

---

## Task 3: Build Report Document, Cover, Summary, Basis, And Data Notice

**Files:**

- Create: `user-ui/src/views/ai-recommend-v2/components/report/ReportDocument.vue`
- Create: `user-ui/src/views/ai-recommend-v2/components/report/ReportCover.vue`
- Create: `user-ui/src/views/ai-recommend-v2/components/report/ReportExecutiveSummary.vue`
- Create: `user-ui/src/views/ai-recommend-v2/components/report/ReportProfileBasis.vue`
- Create: `user-ui/src/views/ai-recommend-v2/components/report/ReportDataNotice.vue`

- [ ] **Step 1: Create ReportDocument.vue**

Create the file with this structure:

```vue
<template>
  <article class="report-document">
    <ReportCover
      :report-id="report?.reportId"
      :created-at="report?.createdAt"
      :status="report?.status"
      :summary="report?.summary"
    />

    <ReportExecutiveSummary
      :summary="report?.summary"
      :tiers="tiers"
    />

    <ReportProfileBasis :profile-basis="report?.profileBasis" />

    <ReportTierSection
      v-for="(tier, index) in tiers"
      :key="tier.level || index"
      :tier="tier"
      :index="index"
    />

    <ReportDataNotice
      :tiers="tiers"
      :profile-basis="report?.profileBasis"
    />
  </article>
</template>

<script setup>
import { computed } from 'vue'
import ReportCover from './ReportCover.vue'
import ReportExecutiveSummary from './ReportExecutiveSummary.vue'
import ReportProfileBasis from './ReportProfileBasis.vue'
import ReportTierSection from './ReportTierSection.vue'
import ReportDataNotice from './ReportDataNotice.vue'

const props = defineProps({
  report: { type: Object, default: () => ({}) }
})

const tiers = computed(() => Array.isArray(props.report?.tiers) ? props.report.tiers : [])
</script>

<style scoped>
.report-document {
  max-width: 1040px;
  margin: 0 auto;
  padding: 34px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 24px 56px rgba(39, 86, 166, .12);
}

@media (max-width: 768px) {
  .report-document {
    padding: 20px 14px;
  }
}
</style>
```

- [ ] **Step 2: Create ReportCover.vue**

Create the file with title, metadata, and status:

```vue
<template>
  <header class="report-cover">
    <div>
      <p class="eyebrow">408 考研筛选平台</p>
      <h1>AI 择校推荐报告</h1>
      <p class="subtitle">基于当前画像与候选池生成的最终推荐快照</p>
    </div>
    <dl class="meta-grid">
      <div>
        <dt>报告编号</dt>
        <dd>{{ reportId || '-' }}</dd>
      </div>
      <div>
        <dt>生成时间</dt>
        <dd>{{ createdAt || '-' }}</dd>
      </div>
      <div>
        <dt>报告状态</dt>
        <dd>{{ statusLabel }}</dd>
      </div>
    </dl>
  </header>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  reportId: { type: [String, Number], default: '' },
  createdAt: { type: String, default: '' },
  status: { type: String, default: '' },
  summary: { type: String, default: '' }
})

const statusLabel = computed(() => {
  if (!props.status) return '已生成'
  const map = { COMPLETED: '已生成', FAILED: '生成失败', PROCESSING: '生成中' }
  return map[props.status] || props.status
})
</script>

<style scoped>
.report-cover {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid #e4edf8;
}
.eyebrow { margin: 0 0 8px; color: #1769f6; font-size: 13px; font-weight: 900; }
h1 { margin: 0; color: #10213f; font-size: 30px; line-height: 38px; letter-spacing: 0; }
.subtitle { margin: 10px 0 0; color: #607592; font-size: 14px; line-height: 22px; }
.meta-grid { margin: 0; display: grid; grid-template-columns: 1fr; gap: 8px; }
.meta-grid div { padding: 10px 12px; border: 1px solid #e4edf8; border-radius: 8px; background: #f8fbff; }
dt { color: #71829a; font-size: 12px; font-weight: 800; }
dd { margin: 4px 0 0; color: #10213f; font-size: 13px; font-weight: 800; word-break: break-word; }
@media (max-width: 900px) { .report-cover { grid-template-columns: 1fr; } }
</style>
```

- [ ] **Step 3: Create ReportExecutiveSummary.vue**

Create the summary and count component:

```vue
<template>
  <section class="summary-section">
    <div class="summary-copy">
      <p class="section-kicker">结论摘要</p>
      <h2>推荐结构与核心结论</h2>
      <p>{{ summary || '报告已生成，请查看下方分档推荐与风险说明。' }}</p>
    </div>
    <div class="count-grid">
      <div v-for="item in countItems" :key="item.key" class="count-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
        <em>所</em>
      </div>
    </div>
    <p v-if="insufficientText" class="insufficient-text">{{ insufficientText }}</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  summary: { type: String, default: '' },
  tiers: { type: Array, default: () => [] }
})

function countByLevel(level) {
  const tier = props.tiers.find(item => item?.level === level || item?.label?.includes(level === 'reach' ? '冲刺' : level === 'steady' ? '稳妥' : '保底'))
  return Array.isArray(tier?.candidates) ? tier.candidates.length : 0
}

const countItems = computed(() => {
  const reach = countByLevel('reach')
  const steady = countByLevel('steady')
  const safe = countByLevel('safe')
  return [
    { key: 'total', label: '总计', count: reach + steady + safe },
    { key: 'reach', label: '冲刺档', count: reach },
    { key: 'steady', label: '稳妥档', count: steady },
    { key: 'safe', label: '保底档', count: safe }
  ]
})

const insufficientText = computed(() => {
  const names = props.tiers
    .filter(item => item?.insufficient)
    .map(item => item.label || item.level)
    .filter(Boolean)
  return names.length ? `候选不足提醒：${names.join('、')}存在候选不足，报告已保留该不确定性。` : ''
})
</script>

<style scoped>
.summary-section { margin-top: 26px; padding: 22px; border: 1px solid #dce7f6; border-radius: 10px; background: #f8fbff; }
.section-kicker { margin: 0 0 6px; color: #1769f6; font-size: 12px; font-weight: 900; }
h2 { margin: 0; color: #10213f; font-size: 20px; line-height: 28px; }
.summary-copy p:last-child { margin: 10px 0 0; color: #425b7c; font-size: 14px; line-height: 24px; }
.count-grid { margin-top: 18px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.count-card { padding: 12px; border: 1px solid #e4edf8; border-radius: 8px; background: #fff; }
.count-card span, .count-card em { display: block; color: #71829a; font-size: 12px; font-style: normal; font-weight: 800; }
.count-card strong { display: block; margin-top: 4px; color: #1769f6; font-size: 24px; line-height: 28px; }
.insufficient-text { margin: 14px 0 0; color: #b55c00; font-size: 13px; line-height: 20px; }
@media (max-width: 768px) { .count-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
```

- [ ] **Step 4: Create ReportProfileBasis.vue**

Create the basis matrix:

```vue
<template>
  <section class="basis-section">
    <div class="section-head">
      <p>推荐依据</p>
      <h2>画像与筛选口径</h2>
    </div>
    <div class="basis-grid">
      <div v-for="item in items" :key="item.key" class="basis-item">
        <span>{{ item.label }}</span>
        <strong>{{ displayValue(item.value) }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  profileBasis: { type: Object, default: () => ({}) }
})

const items = computed(() => {
  const basis = props.profileBasis || {}
  return [
    { key: 'estimatedScore', label: '预计分数', value: basis.estimatedScore },
    { key: 'targetRegions', label: '目标地区', value: basis.targetRegions },
    { key: 'undergradTier', label: '本科层次', value: basis.undergradTier },
    { key: 'isCrossMajor', label: '跨考情况', value: basis.isCrossMajor },
    { key: 'riskPreference', label: '风险偏好', value: basis.riskPreference },
    { key: 'schoolTierPreference', label: '院校层次偏好', value: basis.schoolTierPreference },
    { key: 'regionStrategy', label: '地区策略', value: basis.regionStrategy },
    { key: 'candidateScope', label: '候选范围', value: basis.candidateScope }
  ]
})

function displayValue(value) {
  if (Array.isArray(value)) return value.length ? value.join('、') : '未填写'
  if (value === true) return '是'
  if (value === false) return '否'
  if (value === null || value === undefined || value === '') return '未填写'
  return String(value)
}
</script>

<style scoped>
.basis-section { margin-top: 24px; }
.section-head { margin-bottom: 12px; }
.section-head p { margin: 0 0 4px; color: #1769f6; font-size: 12px; font-weight: 900; }
.section-head h2 { margin: 0; color: #10213f; font-size: 20px; line-height: 28px; }
.basis-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.basis-item { min-width: 0; padding: 12px; border: 1px solid #e4edf8; border-radius: 8px; background: #fff; }
.basis-item span { display: block; color: #71829a; font-size: 12px; font-weight: 800; }
.basis-item strong { display: block; margin-top: 5px; color: #10213f; font-size: 14px; line-height: 20px; word-break: break-word; }
@media (max-width: 900px) { .basis-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 520px) { .basis-grid { grid-template-columns: 1fr; } }
</style>
```

- [ ] **Step 5: Create ReportDataNotice.vue**

Create the final notice:

```vue
<template>
  <footer class="data-notice">
    <strong>数据口径说明</strong>
    <p>本报告基于当前画像、候选池与可用院校专业事实生成，是一次最终推荐快照。</p>
    <p v-if="insufficientText">{{ insufficientText }}</p>
    <p>当部分院校专业数据不完整时，报告会保留风险提示，不将候选结果包装为确定结论。</p>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tiers: { type: Array, default: () => [] },
  profileBasis: { type: Object, default: () => ({}) }
})

const insufficientText = computed(() => {
  const names = props.tiers
    .filter(item => item?.insufficient)
    .map(item => item.label || item.level)
    .filter(Boolean)
  return names.length ? `候选不足档位：${names.join('、')}。` : ''
})
</script>

<style scoped>
.data-notice { margin-top: 28px; padding: 16px; border: 1px solid #e4edf8; border-radius: 8px; background: #f8fbff; color: #607592; }
.data-notice strong { display: block; color: #10213f; font-size: 14px; }
.data-notice p { margin: 8px 0 0; font-size: 13px; line-height: 21px; }
</style>
```

---

## Task 4: Build Tier Sections And Report Candidate Cards

**Files:**

- Create: `user-ui/src/views/ai-recommend-v2/components/report/ReportTierSection.vue`
- Create: `user-ui/src/views/ai-recommend-v2/components/report/ReportCandidateCard.vue`

- [ ] **Step 1: Create ReportTierSection.vue**

Create the chapter component:

```vue
<template>
  <section class="tier-section">
    <div class="tier-title-row">
      <div>
        <p class="chapter-index">{{ chapterIndex }}</p>
        <h2>{{ tierLabel }}</h2>
        <span>{{ strategyText }}</span>
      </div>
      <div class="tier-count">
        <strong>{{ candidates.length }}</strong>
        <span>/ {{ targetCount }} 所</span>
      </div>
    </div>

    <p v-if="tier?.insufficient" class="insufficient-notice">
      {{ tier.insufficientReason || '该档位候选不足，报告已保留该风险。' }}
    </p>

    <div v-if="candidates.length" class="candidate-list">
      <ReportCandidateCard
        v-for="(candidate, candidateIndex) in candidates"
        :key="candidate?.fact?.programId || `${tier?.level || index}-${candidateIndex}`"
        :candidate="candidate"
        :tier-level="tier?.level"
        :tier-label="tierLabel"
        :index="candidateIndex"
      />
    </div>

    <p v-else class="empty-tier">该档位暂无候选学校。</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import ReportCandidateCard from './ReportCandidateCard.vue'

const props = defineProps({
  tier: { type: Object, default: () => ({}) },
  index: { type: Number, default: 0 }
})

const candidates = computed(() => Array.isArray(props.tier?.candidates) ? props.tier.candidates : [])
const tierLabel = computed(() => props.tier?.label || labelByLevel(props.tier?.level) || `第 ${props.index + 1} 档`)
const targetCount = computed(() => props.tier?.targetCount || candidates.value.length)
const chapterIndex = computed(() => String(props.index + 1).padStart(2, '0'))
const strategyText = computed(() => {
  const level = props.tier?.level
  if (level === 'reach') return '保留上限机会，重点关注风险边界。'
  if (level === 'steady') return '兼顾录取概率和学校质量，是报告核心选择。'
  if (level === 'safe') return '优先控制落榜风险，确保保底选择有效。'
  return '根据当前画像和候选池形成的分档建议。'
})

function labelByLevel(level) {
  const map = { reach: '冲刺档', steady: '稳妥档', safe: '保底档' }
  return map[level] || ''
}
</script>

<style scoped>
.tier-section { margin-top: 30px; padding-top: 24px; border-top: 1px solid #e4edf8; }
.tier-title-row { display: flex; justify-content: space-between; gap: 18px; align-items: flex-start; margin-bottom: 16px; }
.chapter-index { margin: 0 0 4px; color: #1769f6; font-size: 12px; font-weight: 900; }
h2 { margin: 0; color: #10213f; font-size: 22px; line-height: 30px; }
.tier-title-row span { display: block; margin-top: 5px; color: #607592; font-size: 13px; line-height: 20px; }
.tier-count { flex-shrink: 0; min-width: 92px; padding: 10px 12px; border: 1px solid #dce7f6; border-radius: 8px; background: #f8fbff; text-align: center; }
.tier-count strong { display: block; color: #1769f6; font-size: 24px; line-height: 28px; }
.tier-count span { margin-top: 2px; color: #71829a; font-size: 12px; font-weight: 800; }
.insufficient-notice { margin: 0 0 14px; padding: 10px 12px; border-radius: 8px; background: #fff7ed; color: #b55c00; font-size: 13px; line-height: 20px; }
.candidate-list { display: grid; gap: 14px; }
.empty-tier { margin: 0; padding: 18px; border: 1px dashed #dce7f6; border-radius: 8px; color: #71829a; text-align: center; }
@media (max-width: 640px) { .tier-title-row { flex-direction: column; } .tier-count { width: 100%; } }
</style>
```

- [ ] **Step 2: Create ReportCandidateCard.vue**

Create the read-only candidate card:

```vue
<template>
  <article class="report-candidate">
    <header class="candidate-head">
      <div>
        <p class="candidate-index">{{ tierLabel }} · {{ index + 1 }}</p>
        <h3>{{ fact.schoolName || '未知院校' }}</h3>
        <p class="program-line">
          {{ fact.collegeName || '学院信息未填写' }} · {{ fact.programName || '专业方向未填写' }}
        </p>
      </div>
      <div class="tag-row">
        <span v-if="fact.schoolTier">{{ fact.schoolTier }}</span>
        <span v-if="fact.city || fact.province">{{ fact.city || fact.province }}</span>
        <span v-if="fact.dataCompleteness" class="complete">{{ fact.dataCompleteness }}</span>
      </div>
    </header>

    <div class="metric-grid">
      <div v-for="metric in metrics" :key="metric.key">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </div>
    </div>

    <section class="opinion-block">
      <h4>推荐理由</h4>
      <p>{{ opinion.reason || finalJudgement || '暂无 AI 观点说明。' }}</p>
    </section>

    <section v-if="pros.length" class="list-block positive">
      <h4>优势</h4>
      <ul>
        <li v-for="item in pros" :key="item">{{ item }}</li>
      </ul>
    </section>

    <section v-if="risks.length" class="list-block risk">
      <h4>风险</h4>
      <ul>
        <li v-for="item in risks" :key="item">{{ item }}</li>
      </ul>
    </section>

    <footer class="candidate-foot">
      <span>{{ actionText }}</span>
      <em v-if="fact.dataYear">数据年份：{{ fact.dataYear }}</em>
      <em v-if="fact.sourceOwner || fact.sourceUrl">来源：{{ fact.sourceOwner || fact.sourceUrl }}</em>
    </footer>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  candidate: { type: Object, default: () => ({}) },
  tierLevel: { type: String, default: '' },
  tierLabel: { type: String, default: '' },
  index: { type: Number, default: 0 }
})

const fact = computed(() => props.candidate?.fact || {})
const opinion = computed(() => props.candidate?.opinion || {})
const finalJudgement = computed(() => props.candidate?.finalJudgement || '')

const metrics = computed(() => [
  { key: 'avg', label: '录取均分', value: valueOrDash(fact.value.avgAdmittedScore || fact.value.scoreLine) },
  { key: 'gap', label: '分差', value: valueOrDash(fact.value.scoreGap || fact.value.gapLabel) },
  { key: 'quota', label: '名额', value: valueOrDash(fact.value.unifiedExamQuota || fact.value.planCount || fact.value.admittedCount) },
  { key: 'code', label: '专业代码', value: valueOrDash(fact.value.programCode) }
])

const pros = computed(() => toList(opinion.value.pros))
const risks = computed(() => toList(opinion.value.risks))
const actionText = computed(() => opinion.value.recommendedAction || props.candidate?.adjustReason || '建议结合个人偏好复核后保留。')

function valueOrDash(value) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

function toList(value) {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (typeof value === 'string' && value.trim()) return [value.trim()]
  return []
}
</script>

<style scoped>
.report-candidate { padding: 16px; border: 1px solid #dce7f6; border-radius: 10px; background: #fff; box-shadow: 0 10px 26px rgba(39, 86, 166, .06); }
.candidate-head { display: flex; justify-content: space-between; gap: 16px; }
.candidate-index { margin: 0 0 4px; color: #1769f6; font-size: 12px; font-weight: 900; }
h3 { margin: 0; color: #10213f; font-size: 18px; line-height: 26px; }
.program-line { margin: 5px 0 0; color: #607592; font-size: 13px; line-height: 20px; }
.tag-row { flex-shrink: 0; display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.tag-row span { height: 24px; padding: 0 8px; border: 1px solid #dce7f6; border-radius: 6px; background: #f8fbff; color: #425b7c; font-size: 12px; line-height: 22px; font-weight: 800; }
.tag-row .complete { color: #087443; background: #ecfdf5; border-color: #b7ebcc; }
.metric-grid { margin-top: 14px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.metric-grid div { padding: 10px; border-radius: 8px; background: #f3f7fc; text-align: center; }
.metric-grid span { display: block; color: #71829a; font-size: 12px; font-weight: 800; }
.metric-grid strong { display: block; margin-top: 4px; color: #10213f; font-size: 15px; line-height: 20px; word-break: break-word; }
.opinion-block, .list-block { margin-top: 14px; padding: 12px; border-radius: 8px; background: #f8fbff; }
h4 { margin: 0 0 6px; color: #10213f; font-size: 13px; line-height: 18px; }
.opinion-block p { margin: 0; color: #425b7c; font-size: 13px; line-height: 22px; }
.list-block ul { margin: 0; padding-left: 18px; color: #425b7c; font-size: 13px; line-height: 22px; }
.list-block.positive { background: #f0fdf6; }
.list-block.risk { background: #fff7ed; }
.candidate-foot { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px 14px; color: #71829a; font-size: 12px; line-height: 18px; }
.candidate-foot span { color: #1769f6; font-weight: 900; }
.candidate-foot em { font-style: normal; }
@media (max-width: 760px) { .candidate-head { flex-direction: column; } .tag-row { justify-content: flex-start; } .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 420px) { .metric-grid { grid-template-columns: 1fr; } }
</style>
```

- [ ] **Step 3: Run the layout test**

Run:

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui\src\views\AiRecommend.layout.test.mjs
```

Expected: PASS after Tasks 2-4 are complete.

- [ ] **Step 4: Commit the report component implementation**

Run:

```powershell
rtk git add user-ui/src/views/AiRecommend.layout.test.mjs user-ui/src/views/ai-recommend-v2/components/ReportView.vue user-ui/src/views/ai-recommend-v2/components/report
rtk git commit -m "feat(ai-report): redesign final web report"
```

---

## Task 5: Verify Build And Manual Visual Behavior

**Files:**

- No additional code files unless verification reveals a concrete bug.

- [ ] **Step 1: Run streaming regression**

Run:

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui\src\views\AiRecommend.streaming.test.mjs
```

Expected: PASS. This confirms the report redesign did not disturb the AI workbench streaming compatibility checks.

- [ ] **Step 2: Run profile preference regression**

Run:

```powershell
rtk node C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui\src\views\Profile.preferences.test.mjs
```

Expected: PASS.

- [ ] **Step 3: Run production build**

Run:

```powershell
rtk proxy powershell -NoProfile -Command "cd 'C:\Users\111\Desktop\postgrad_selector\postgrad_selector\user-ui'; npm run build"
```

Expected: PASS. Existing Vite/Rollup warnings about CJS API, PURE comments, or chunk size are acceptable if no new build error appears.

- [ ] **Step 4: Manual browser check**

Open an actual generated report route:

```text
http://localhost:8082/ai-report-v2/<reportId>
```

Check:

- The page shows a white formal report document.
- The first screen clearly shows title, metadata, summary, and counts.
- No `问 AI`, `移出草稿`, `替换`, PDF, Excel, or share actions appear.
- School cards are read-only.
- Missing data displays `-`, `未填写`, or the fallback text instead of breaking the layout.
- Browser 100% zoom has no obvious overlap or clipping.

- [ ] **Step 5: Commit verification-only fixes if needed**

If Step 4 reveals a layout bug, fix only the affected report component and commit:

```powershell
rtk git add user-ui/src/views/ai-recommend-v2/components/report user-ui/src/views/ai-recommend-v2/components/ReportView.vue user-ui/src/views/AiRecommend.layout.test.mjs
rtk git commit -m "fix(ai-report): polish final report layout"
```

If no bug is found, do not create an empty commit.

---

## Self-Review Checklist

- Spec coverage: Tasks 2-4 cover page shell, cover, summary, basis, tier chapters, read-only candidate cards, and data notice.
- Non-goals: No task implements PDF, Excel, share, or backend changes.
- Component boundary: `ReportView.vue` only loads data; report rendering moves under `components/report/`.
- Null safety: `ReportCandidateCard.vue`, `ReportDocument.vue`, and `ReportProfileBasis.vue` use safe defaults.
- Regression coverage: Task 5 runs layout, streaming, profile, and build checks.
