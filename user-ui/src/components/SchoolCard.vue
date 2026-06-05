<template>
  <article class="school-card" :class="cardClasses">
    <!-- ===== Top: seal + name + actions ===== -->
    <div class="card-top">
      <div class="school-seal">{{ seal }}</div>
      <div>
        <h3>{{ schoolName }} <small v-if="badge">{{ badge }}</small></h3>
        <p>
          <span :class="{ 'pending-field': isPendingCollege(collegeName) }">{{ displayCollegeName(collegeName) }}</span>
          / {{ programName }}
        </p>
        <button v-if="directionCount > 1" class="direction-count" type="button" @click="expanded = !expanded">
          共 {{ directionCount }} 个符合方向
        </button>
        <div class="school-meta-line">
          <span>{{ examDisplay }} | {{ province || '-' }}</span>
          <em v-if="dataYear" class="data-year-badge">{{ dataYear }}年数据</em>
          <em v-else class="data-year-badge muted">年份待补</em>
        </div>
      </div>
      <!-- Judgement pill (AI report) or star button (results) -->
      <span v-if="judgement" class="judgement-pill" :class="'judgement-' + judgementTone">
        {{ judgement }}
      </span>
      <button v-else-if="showFavorite"
        class="star-btn" :class="{ favorited: isFav, loading: favLoading }"
        type="button" @click.stop="$emit('toggle-favorite', programId)"
        :aria-label="isFav ? '移出备选' : '加入备选'">
        <i :class="isFav ? 'el-icon-star-on' : 'el-icon-star-off'"></i>
      </button>
    </div>

    <!-- ===== Score metrics grid ===== -->
    <div class="score-line">
      <div class="score-metric" tabindex="0" data-tip="该专业近年拟录取名单中的平均总分，是判断冲稳保的主依据。">
        <span>拟录取均分</span><strong>{{ valueOrDash(avgAdmittedScore) }}</strong>
      </div>
      <div class="score-metric" tabindex="0" data-tip="你的预计初试总分减去拟录取均分，负数代表低于历史均分。">
        <span>均分差距</span><strong :class="gap >= 0 ? 'positive' : 'negative'">{{ formatGap(gap) }}</strong>
      </div>
      <div class="score-metric" tabindex="0" data-tip="该专业近年拟录取名单中的最低总分，仅作风险边界参考。">
        <span>最低录取分</span><strong>{{ valueOrDash(admissionLow) }}</strong>
      </div>
      <div class="score-metric" tabindex="0" data-tip="该专业近年拟录取最低分到最高分的范围，仅作历史参考。">
        <span>拟录取区间</span><strong>{{ admissionRange }}</strong>
      </div>
      <div class="score-metric" tabindex="0" data-tip="优先展示该专业统考名额；缺少统考名额时展示计划人数，计划人数可能包含推免。">
        <span>{{ quotaLabel }}</span>
        <strong>{{ quotaValue }}<small>{{ quotaHint }}</small></strong>
      </div>
      <div v-if="showCompleteness" class="score-metric" tabindex="0" data-tip="数据完整度越高说明复试线、录取区间、人数等关键字段越齐全。">
        <span>数据完整度</span><strong>{{ completenessLevel }}</strong>
      </div>
    </div>

    <!-- ===== Data completeness badge ===== -->
    <span v-if="!showCompleteness" class="grade completeness-tip" :class="'grade-' + completenessLevel.toLowerCase()"
      tabindex="0" :data-tip="completenessTip">完整度 {{ completenessLevel }}</span>

    <!-- ===== AI evidence / risks (report mode) ===== -->
    <div v-if="evidence && evidence.length" class="evidence-list">
      <strong>推荐依据</strong>
      <p v-for="item in evidence.slice(0, 2)" :key="item">{{ item }}</p>
    </div>
    <div v-if="risks && risks.length" class="risk-list">
      <strong>需要注意</strong>
      <p v-for="item in risks.slice(0, 2)" :key="item">{{ item }}</p>
    </div>

    <!-- ===== Source link ===== -->
    <a v-if="sourceUrl" class="source-link" :href="sourceUrl" target="_blank" rel="noopener noreferrer" @click.stop>
      <i class="el-icon-link"></i> {{ sourceOwner || 'N诺' }}来源
    </a>
    <span v-else class="source-link source-missing"><i class="el-icon-link"></i> 本地数据（来源待补）</span>

    <!-- ===== Actions ===== -->
    <div class="card-actions">
      <button class="detail-link" type="button" @click.stop="openDetail">查看详情</button>
      <button v-if="showCompare" class="backup-card-btn" type="button" @click.stop="$emit('toggle-compare', programId)">
        {{ inCompare ? '移出对比' : '加入对比' }}
      </button>
      <button v-if="showFavorite" class="backup-card-btn" type="button"
        :disabled="favLoading" @click.stop="$emit('toggle-favorite', programId)">
        {{ isFav ? '已加入备选' : '加入备选' }}
      </button>
    </div>

    <!-- ===== Expanded direction list ===== -->
    <div v-if="directionCount > 1 && expanded" class="direction-list">
      <div v-for="d in directions" :key="d.programId || d.cardKey" class="direction-row">
        <div class="direction-main">
          <strong>
            <span :class="{ 'pending-field': isPendingCollege(d.collegeName) }">{{ displayCollegeName(d.collegeName) }}</span>
            / {{ d.programName }}
          </strong>
          <small>{{ d.exam || examDisplay }} | {{ (d.dataYear || dataYear) ? ((d.dataYear || dataYear) + '年数据') : '年份待补' }}</small>
        </div>
        <div class="direction-stats">
          <span>均分 {{ d.avgAdmittedScore || d.avgScore || '-' }}</span>
          <span :class="{ positive: (d.avgScoreGap ?? d.gap) >= 0, negative: (d.avgScoreGap ?? d.gap) < 0 }">
            差距 {{ formatGap(d.avgScoreGap ?? d.gap) }}
          </span>
          <span>区间 {{ d.admissionLow || '-' }}-{{ d.admissionHigh || '-' }}</span>
          <span>{{ d.quotaLabel || '招生' }} {{ d.quotaValue || d.quota || '-' }}</span>
        </div>
        <div class="direction-actions">
          <button type="button" :disabled="favLoading" @click="$emit('toggle-favorite', d.programId)">
            {{ isFav ? '移出备选' : '加入备选' }}
          </button>
          <button type="button" @click="openDirectionDetail(d)">详情</button>
        </div>
      </div>
    </div>

    <!-- ===== Detail Drawer ===== -->
    <el-drawer title="院校专业详情" v-model="detailVisible" size="520px" append-to-body>
      <div class="detail-drawer" v-loading="detailLoading">
        <template v-if="detail">
          <h2>{{ detail.basic.schoolName }}</h2>
          <p>{{ detail.basic.collegeName }} / {{ detail.basic.programName }}</p>
          <div class="detail-tags">
            <span>{{ detail.basic.examCombo || '-' }}</span>
            <span>{{ detail.dataCompleteness?.label || completenessLevel }}</span>
          </div>
          <div class="detail-score-grid">
            <div><small>复试线</small><strong>{{ detail.recommendationOverview?.scoreLine || '-' }}</strong></div>
            <div><small>最低录取分</small><strong>{{ detail.recommendationOverview?.admissionLow || '-' }}</strong></div>
            <div><small>与最低录取分差距</small><strong>{{ formatDiff(detail.recommendationOverview?.admissionLowGap) }}</strong></div>
            <div><small>拟录取区间</small><strong>{{ detail.recommendationOverview?.admissionRangeLabel || '-' }}</strong></div>
            <div><small>与拟录取均分差距</small><strong>{{ formatDiff(detail.recommendationOverview?.avgScoreGap) }}</strong></div>
          </div>
          <div v-if="detail.riskWarnings && detail.riskWarnings.length" class="drawer-warning">
            <p v-for="w in detail.riskWarnings" :key="w">· {{ w }}</p>
          </div>
          <a v-if="detail.source?.sourceUrl" class="drawer-source-link"
            :href="detail.source.sourceUrl" target="_blank" rel="noopener noreferrer">
            <i class="el-icon-link"></i> 查看 N诺来源
          </a>
          <table class="trend-table">
            <thead><tr><th>年份</th><th>复试线</th><th>拟录取均分</th><th>录取区间</th></tr></thead>
            <tbody>
              <tr v-for="item in detail.trends" :key="item.year">
                <td>{{ item.year }}</td>
                <td>{{ item.scoreLine }}</td>
                <td>{{ item.avgAdmittedScore || '-' }}</td>
                <td>{{ item.admissionLow || '-' }} - {{ item.admissionHigh || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </template>
      </div>
    </el-drawer>
  </article>
</template>

<script setup>
import { ref, computed } from 'vue'
import { getProgramDetail } from '@/api/programs'

const props = defineProps({
  programId: { type: [Number, String], required: true },
  schoolName: { type: String, default: '' },
  badge: { type: String, default: '' },
  collegeName: { type: String, default: '' },
  programName: { type: String, default: '' },
  province: { type: String, default: '' },
  examDisplay: { type: String, default: '考试组合待补' },
  dataYear: { type: [Number, String], default: null },
  avgAdmittedScore: { default: null },
  gap: { type: Number, default: 0 },
  admissionLow: { default: null },
  admissionHigh: { default: null },
  quotaLabel: { type: String, default: '招生' },
  quotaValue: { type: [String, Number], default: '-' },
  quotaHint: { type: String, default: '' },
  completenessLevel: { type: String, default: 'C' },
  sourceUrl: { type: String, default: '' },
  sourceOwner: { type: String, default: '' },
  // AI report mode
  judgement: { type: String, default: '' },
  evidence: { type: Array, default: () => [] },
  risks: { type: Array, default: () => [] },
  // Interaction state
  isFav: { type: Boolean, default: false },
  favLoading: { type: Boolean, default: false },
  inCompare: { type: Boolean, default: false },
  // Feature flags
  showFavorite: { type: Boolean, default: false },
  showCompare: { type: Boolean, default: false },
  showCompleteness: { type: Boolean, default: false },
  // Direction grouping (from results page)
  directionCount: { type: Number, default: 0 },
  directions: { type: Array, default: () => [] },
  // For detail API call
  estimatedScore: { type: Number, default: 0 }
})

const emit = defineEmits(['toggle-favorite', 'toggle-compare'])

const expanded = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailProgramId = ref(null)

const seal = computed(() => (props.schoolName || '?').slice(0, 1))
const admissionRange = computed(() => {
  const l = props.admissionLow, h = props.admissionHigh
  if (l == null && h == null) return '-'
  return `${l ?? '-'}-${h ?? '-'}`
})
const completenessTip = computed(() => {
  const m = { A: '含复试线、拟录取区间、人数等字段', B: '含主要分数字段，部分字段缺失', C: '仅有复试线或基础字段' }
  return m[props.completenessLevel] || m.C
})
const judgementTone = computed(() => {
  if (/高风险|冲刺|风险偏高/.test(props.judgement)) return 'danger'
  if (/保底|低风险/.test(props.judgement)) return 'safe'
  return 'warning'
})
const cardClasses = computed(() => ({
  'has-judgement': !!props.judgement,
  'has-evidence': !!(props.evidence && props.evidence.length)
}))

function valueOrDash(v) { return (v === null || v === undefined || v === '') ? '-' : v }
function formatGap(v) {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return n > 0 ? `+${n}` : String(n)
}
function formatDiff(v) {
  if (v === null || v === undefined) return '-'
  return (v > 0 ? '+' : '') + v
}
function isPendingCollege(n) { return !n || n === '-' || n === '待补充' || n === '学院待定' }
function displayCollegeName(n) { return isPendingCollege(n) ? '学院待补' : n }

async function openDetail() {
  await fetchDetail(props.programId)
}
async function openDirectionDetail(d) {
  await fetchDetail(d.programId)
}
async function fetchDetail(pid) {
  if (!pid) return
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  detailProgramId.value = pid
  try {
    const res = await getProgramDetail(pid, { estimatedScore: props.estimatedScore })
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}
</script>

<style scoped>
.school-card { background: #fff; border: 1px solid #dbe5f4; border-radius: 8px; box-shadow: 0 10px 28px rgba(34,73,135,.06); padding: 16px; }
.card-top { display: grid; grid-template-columns: 48px 1fr auto; gap: 12px; align-items: start; }
.school-seal { width: 42px; height: 42px; border-radius: 50%; border: 2px solid #80aaff; color: #1769f6; display: flex; align-items: center; justify-content: center; font-weight: 800; }
.school-card h3 { margin: 0 0 7px; font-size: 18px; }
.school-card h3 small { margin-left: 8px; color: #1769f6; background: #edf4ff; border-radius: 4px; padding: 2px 7px; font-size: 12px; }
.school-card p, .school-card span { margin: 0; color: #6b778a; line-height: 1.6; }
.school-meta-line { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 4px; color: #607089; font-size: 14px; }
.data-year-badge { display: inline-flex; align-items: center; min-height: 22px; padding: 0 8px; border-radius: 999px; background: #eef6ff; color: #1769f6; font-style: normal; font-size: 12px; font-weight: 700; white-space: nowrap; }
.data-year-badge.muted { background: #f3f6fa; color: #8a97aa; }
.pending-field { color: #d46b08; font-weight: 600; }
.direction-count { border: 0; background: #eef4ff; color: #1769f6; border-radius: 999px; padding: 3px 9px; margin-top: 4px; font-size: 12px; font-weight: 700; cursor: pointer; }

.judgement-pill { flex-shrink: 0; min-height: 24px; padding: 3px 10px; border-radius: 6px; font-size: 13px; line-height: 18px; font-weight: 700; background: #fff7e8; color: #a85d00; border: 1px solid #ffe0a6; }
.judgement-safe { background: #edfdf5; color: #087443; border-color: #c9f1dc; }
.judgement-danger { background: #fff1f1; color: #bf2f2f; border-color: #ffd1d1; }

.star-btn { border: 0; background: transparent; color: #42526b; cursor: pointer; font-size: 22px; transition: color .18s,transform .18s; }
.star-btn:hover { color: #f5a400; transform: scale(1.08); }
.star-btn.favorited,.star-btn.favorited i { color: #f5a400; }
.star-btn.loading { pointer-events: none; opacity: .72; }

.score-line { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid #eef2f7; border-bottom: 1px solid #eef2f7; margin: 16px 0 12px; padding: 10px 0; }
.score-metric { position: relative; display: flex; justify-content: space-between; gap: 10px; padding-right: 18px; cursor: help; border-radius: 6px; outline: none; }
.score-metric:hover,.score-metric:focus,.score-metric:focus-visible { background: #f5f8ff; }
.score-metric::after { content: attr(data-tip); position: absolute; left: 0; bottom: calc(100% + 8px); z-index: 8; width: max-content; max-width: min(280px,78vw); padding: 7px 9px; border: 1px solid #dbe7ff; border-radius: 6px; background: #fff; box-shadow: 0 8px 22px rgba(15,37,76,.14); color: #526278; font-size: 12px; line-height: 1.45; font-weight: 400; white-space: normal; opacity: 0; visibility: hidden; transform: translateY(4px); transition: opacity .16s,transform .16s,visibility .16s; pointer-events: none; }
.score-metric:nth-child(2n)::after { right: 18px; left: auto; }
.score-metric:hover::after,.score-metric:focus::after,.score-metric:focus-visible::after { opacity: 1; visibility: visible; transform: translateY(0); }
.score-line strong { font-size: 17px; }
.score-line strong small { display: block; margin-top: 2px; color: #7a879a; font-size: 11px; font-weight: 600; text-align: right; }
.score-line .positive { color: #087443; }
.score-line .negative { color: #bf2f2f; }

.grade { display: inline-flex; align-items: center; justify-content: center; min-width: 34px; height: 24px; padding: 0 8px; border-radius: 4px; font-style: normal; font-weight: 700; margin-bottom: 8px; }
.grade-a { color: #15803d; background: #e7f7ed; border: 1px solid #a9e5bf; }
.grade-b { color: #1769f6; background: #eaf2ff; border: 1px solid #b7d1ff; }
.grade-c { color: #a85d00; background: #fff8ed; border: 1px solid #ffd9a0; }
.completeness-tip { cursor: help; }

.evidence-list,.risk-list { margin-bottom: 10px; }
.evidence-list strong { display: block; color: #15803d; font-size: 13px; margin-bottom: 4px; }
.evidence-list p { margin: 2px 0; color: #445a76; font-size: 13px; line-height: 1.55; }
.risk-list strong { display: block; color: #bf2f2f; font-size: 13px; margin-bottom: 4px; }
.risk-list p { margin: 2px 0; color: #445a76; font-size: 13px; line-height: 1.55; }

.source-link { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: #607592; text-decoration: none; margin-bottom: 8px; }
.source-link:hover { color: #1769f6; }
.source-missing { opacity: .6; }

.card-actions { display: flex; gap: 8px; margin-top: 10px; }
.detail-link { border: 1px solid #d7e4f5; border-radius: 5px; background: #fff; color: #1769f6; cursor: pointer; font-weight: 700; padding: 4px 10px; font-size: 13px; }
.detail-link:hover { background: #f0f5ff; }
.backup-card-btn { border: 1px solid #d7e4f5; border-radius: 5px; background: #fff; color: #1769f6; cursor: pointer; font-weight: 700; padding: 4px 7px; font-size: 13px; white-space: nowrap; }
.backup-card-btn:hover { background: #f0f5ff; }
.backup-card-btn:disabled { opacity: .6; cursor: not-allowed; }

.direction-list { margin-top: 14px; border-top: 1px solid #eef2f7; padding-top: 10px; }
.direction-row { display: grid; grid-template-columns: minmax(0,1.25fr) minmax(0,1fr) auto; gap: 10px; align-items: center; padding: 9px 0; border-bottom: 1px solid #f1f5fb; }
.direction-row:last-child { border-bottom: 0; }
.direction-main strong,.direction-main small,.direction-stats span { display: block; }
.direction-main strong { color: #1f2937; font-size: 13px; line-height: 1.45; }
.direction-main small { color: #7a879a; margin-top: 2px; }
.direction-stats { color: #5f6f85; font-size: 12px; line-height: 1.7; }
.direction-stats .positive { color: #087443; }
.direction-stats .negative { color: #bf2f2f; }
.direction-actions { display: flex; gap: 6px; }
.direction-actions button { border: 1px solid #d7e4f5; border-radius: 5px; background: #fff; color: #1769f6; cursor: pointer; font-weight: 700; padding: 4px 7px; white-space: nowrap; }
.direction-actions button:disabled { opacity: .6; cursor: not-allowed; }

/* Drawer */
.detail-drawer { padding: 0 24px 28px; }
.detail-drawer h2 { margin: 0 0 6px; font-size: 22px; }
.detail-drawer p { margin: 0 0 12px; color: #64748b; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0 18px; }
.detail-tags span { padding: 5px 9px; border-radius: 5px; color: #1769f6; background: #eef4ff; font-weight: 700; font-size: 12px; }
.detail-score-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 16px; }
.detail-score-grid div { border: 1px solid #e5edf8; border-radius: 8px; padding: 12px; background: #fbfdff; }
.detail-score-grid small { display: block; color: #7a879a; margin-bottom: 4px; }
.detail-score-grid strong { font-size: 22px; }
.drawer-warning { border: 1px solid #ffd7a8; border-radius: 8px; padding: 12px; margin-bottom: 16px; background: #fff8e8; }
.drawer-warning p { color: #9a4d00; margin: 4px 0; }
.drawer-source-link { display: block; margin: 0 0 18px; color: #1769f6; text-decoration: none; }
.drawer-source-link:hover { text-decoration: underline; }
.trend-table { width: 100%; border-collapse: collapse; }
.trend-table th,.trend-table td { border-bottom: 1px solid #e5edf8; padding: 8px; text-align: left; }
</style>
