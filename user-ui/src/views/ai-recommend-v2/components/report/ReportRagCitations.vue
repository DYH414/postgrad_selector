<template>
  <section v-if="hasCitations" class="rag-citation-section">
    <div class="section-heading">
      <p>Evidence</p>
      <h2>推荐证据引用</h2>
      <span>来自知识库检索和重排后的候选证据，用于支撑报告解释。</span>
    </div>

    <div v-if="programGroups.length" class="citation-blocks">
      <article
        v-for="group in programGroups"
        :key="group.programId"
        class="citation-group"
      >
        <header>
          <strong>{{ groupTitle(group) }}</strong>
          <span>programId: {{ group.programId }}</span>
        </header>
        <ul>
          <li v-for="citation in group.items" :key="citationKey(citation)">
            <div class="citation-main">
              <strong>{{ citation.title || citation.sourceName || '知识库证据' }}</strong>
              <p>{{ citation.contentPreview || '暂无证据摘要' }}</p>
            </div>
            <div class="citation-meta">
              <span v-if="citation.docType">{{ docTypeLabel(citation.docType) }}</span>
              <span v-if="citation.section">{{ sectionLabel(citation.section) }}</span>
              <span v-if="citation.year">{{ citation.year }}</span>
              <a
                v-if="citation.sourceUrl"
                :href="citation.sourceUrl"
                target="_blank"
                rel="noreferrer"
              >
                来源
              </a>
            </div>
          </li>
        </ul>
      </article>
    </div>

    <article v-if="generalCitations.length" class="citation-group citation-group--general">
      <header>
        <strong>通用证据</strong>
        <span>未绑定具体专业的补充依据</span>
      </header>
      <ul>
        <li v-for="citation in generalCitations" :key="citationKey(citation)">
          <div class="citation-main">
            <strong>{{ citation.title || citation.sourceName || '知识库证据' }}</strong>
            <p>{{ citation.contentPreview || '暂无证据摘要' }}</p>
          </div>
          <div class="citation-meta">
            <span v-if="citation.docType">{{ docTypeLabel(citation.docType) }}</span>
            <span v-if="citation.section">{{ sectionLabel(citation.section) }}</span>
            <span v-if="citation.year">{{ citation.year }}</span>
            <a
              v-if="citation.sourceUrl"
              :href="citation.sourceUrl"
              target="_blank"
              rel="noreferrer"
            >
              来源
            </a>
          </div>
        </li>
      </ul>
    </article>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  citations: { type: Array, default: () => [] }
})

const normalizedCitations = computed(() => props.citations.filter(Boolean))
const hasCitations = computed(() => normalizedCitations.value.length > 0)

const programGroups = computed(() => {
  const groups = new Map()
  normalizedCitations.value
    .filter(item => item.programId)
    .forEach(item => {
      const programId = String(item.programId)
      if (!groups.has(programId)) {
        groups.set(programId, {
          programId,
          schoolName: item.schoolName,
          programName: item.programName,
          items: []
        })
      }
      groups.get(programId).items.push(item)
    })
  return Array.from(groups.values())
})

const generalCitations = computed(() => normalizedCitations.value.filter(item => !item.programId))

function groupTitle(group) {
  const parts = [group.schoolName, group.programName].filter(Boolean)
  return parts.length ? parts.join(' · ') : '专业证据'
}

function citationKey(citation) {
  return [
    citation.programId,
    citation.contentHash,
    citation.title,
    citation.sourceName,
    citation.section
  ].filter(Boolean).join(':')
}

function docTypeLabel(docType) {
  const map = {
    program_history: '历年数据',
    program_year_summary: '年度摘要',
    data_quality: '数据质量',
    selection_method: '择校方法',
    adjustment_policy: '调剂政策',
    score_policy: '分数政策'
  }
  return map[docType] || docType
}

function sectionLabel(section) {
  const map = {
    admission_score: '录取分数',
    score_trend: '分数趋势',
    enrollment_plan: '招生计划',
    recommendation_fact: '推荐事实',
    risk_note: '风险提示',
    data_quality: '数据质量'
  }
  return map[section] || section
}
</script>

<style scoped>
.rag-citation-section {
  margin-top: 34px;
  padding-top: 28px;
  border-top: 1px solid #dce7f6;
}

.section-heading {
  margin-bottom: 18px;
}

.section-heading p {
  margin: 0 0 6px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.section-heading h2 {
  margin: 0;
  color: #10213f;
  font-size: 22px;
  line-height: 30px;
}

.section-heading span {
  display: block;
  margin-top: 6px;
  color: #607592;
  font-size: 13px;
  line-height: 20px;
}

.citation-blocks {
  display: grid;
  gap: 14px;
}

.citation-group {
  padding: 16px;
  border: 1px solid #dce7f6;
  border-radius: 8px;
  background: #f8fbff;
}

.citation-group + .citation-group {
  margin-top: 14px;
}

.citation-group header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.citation-group header strong {
  color: #10213f;
  font-size: 15px;
  line-height: 22px;
}

.citation-group header span {
  color: #7b8da8;
  font-size: 12px;
  white-space: nowrap;
}

.citation-group ul {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.citation-group li {
  padding: 12px;
  border: 1px solid #e5edf8;
  border-radius: 8px;
  background: #fff;
}

.citation-main strong {
  display: block;
  color: #1e3558;
  font-size: 14px;
  line-height: 22px;
}

.citation-main p {
  margin: 6px 0 0;
  color: #425b7c;
  font-size: 13px;
  line-height: 21px;
}

.citation-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.citation-meta span,
.citation-meta a {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  background: #edf4ff;
  color: #275ba8;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.citation-meta a {
  background: #fff7e9;
  color: #b36a00;
}

@media (max-width: 768px) {
  .citation-group header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
