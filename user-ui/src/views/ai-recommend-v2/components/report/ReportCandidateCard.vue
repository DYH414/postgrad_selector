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

    <section v-if="tradeoffs.length || cons.length" class="list-block tradeoff">
      <h4>取舍说明</h4>
      <ul>
        <li v-for="item in [...tradeoffs, ...cons]" :key="item">{{ item }}</li>
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
const cons = computed(() => toList(opinion.value.cons))
const tradeoffs = computed(() => toList(opinion.value.tradeoffs))
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
.report-candidate {
  padding: 16px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(39, 86, 166, .06);
}

.candidate-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.candidate-index {
  margin: 0 0 4px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 900;
}

h3 {
  margin: 0;
  color: #10213f;
  font-size: 18px;
  line-height: 26px;
}

.program-line {
  margin: 5px 0 0;
  color: #607592;
  font-size: 13px;
  line-height: 20px;
}

.tag-row {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.tag-row span {
  height: 24px;
  padding: 0 8px;
  border: 1px solid #dce7f6;
  border-radius: 6px;
  background: #f8fbff;
  color: #425b7c;
  font-size: 12px;
  line-height: 22px;
  font-weight: 800;
}

.tag-row .complete {
  color: #087443;
  background: #ecfdf5;
  border-color: #b7ebcc;
}

.metric-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.metric-grid div {
  padding: 10px;
  border-radius: 8px;
  background: #f3f7fc;
  text-align: center;
}

.metric-grid span {
  display: block;
  color: #71829a;
  font-size: 12px;
  font-weight: 800;
}

.metric-grid strong {
  display: block;
  margin-top: 4px;
  color: #10213f;
  font-size: 15px;
  line-height: 20px;
  word-break: break-word;
}

.opinion-block,
.list-block {
  margin-top: 14px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fbff;
}

h4 {
  margin: 0 0 6px;
  color: #10213f;
  font-size: 13px;
  line-height: 18px;
}

.opinion-block p {
  margin: 0;
  color: #425b7c;
  font-size: 13px;
  line-height: 22px;
}

.list-block ul {
  margin: 0;
  padding-left: 18px;
  color: #425b7c;
  font-size: 13px;
  line-height: 22px;
}

.list-block.positive {
  background: #f0fdf6;
}

.list-block.risk {
  background: #fff7ed;
}

.list-block.tradeoff {
  background: #f8fbff;
}

.candidate-foot {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #71829a;
  font-size: 12px;
  line-height: 18px;
}

.candidate-foot span {
  color: #1769f6;
  font-weight: 900;
}

.candidate-foot em {
  font-style: normal;
}

@media (max-width: 760px) {
  .candidate-head {
    flex-direction: column;
  }

  .tag-row {
    justify-content: flex-start;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
