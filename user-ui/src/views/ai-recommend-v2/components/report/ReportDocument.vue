<template>
  <div class="report-layout">
    <aside class="report-outline">
      <p class="outline-kicker">Report</p>
      <strong>择校结论</strong>
      <span>{{ totalCount }} 所学校 · {{ strategyText }}</span>
      <nav>
        <a href="#report-summary">结论摘要</a>
        <a href="#report-basis">推荐依据</a>
        <a
          v-for="(tier, index) in tiers"
          :key="tier.level || index"
          :href="`#tier-${index + 1}`"
        >
          {{ String(index + 1).padStart(2, '0') }} {{ tier.label || tier.level }}
        </a>
      </nav>
    </aside>

    <article class="report-document">
      <ReportCover
        :report-id="report?.reportId"
        :created-at="report?.createdAt"
        :status="report?.status"
        :summary="report?.summary"
      />

      <div id="report-summary">
        <ReportExecutiveSummary
          :summary="report?.summary"
          :tiers="tiers"
        />
      </div>

      <div id="report-basis">
        <ReportProfileBasis :profile-basis="report?.profileBasis" />
      </div>

      <ReportTierSection
        v-for="(tier, index) in tiers"
        :id="`tier-${index + 1}`"
        :key="tier.level || index"
        :tier="tier"
        :index="index"
      />

      <ReportDataNotice
        :tiers="tiers"
        :profile-basis="report?.profileBasis"
      />
    </article>
  </div>
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
const totalCount = computed(() => tiers.value.reduce((sum, tier) => {
  return sum + (Array.isArray(tier?.candidates) ? tier.candidates.length : 0)
}, 0))
const strategyText = computed(() => {
  const v = props.report?.profileBasis?.schoolTierPreference
  const map = {
    developed_region_priority: '发达地区优先',
    developed_priority: '发达地区优先',
    developed_balanced: '发达地区优先',
    school_tier_priority: '学校层次优先',
    tier_priority: '学校层次优先',
    must_211_or_better: '学校层次优先',
    prefer_211_or_better: '学校层次优先',
    safe_admission_priority: '安全上岸优先',
    safe_first: '安全上岸优先',
    conservative: '安全上岸优先',
    balanced: '安全上岸优先',
    reach_first: '安全上岸优先',
    aggressive: '安全上岸优先'
  }
  return map[v] || '安全上岸优先'
})
</script>

<style scoped>
.report-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.report-outline {
  position: sticky;
  top: 86px;
  padding: 18px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 16px 38px rgba(39, 86, 166, .09);
}

.outline-kicker {
  margin: 0 0 6px;
  color: #1769f6;
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.report-outline strong,
.report-outline span,
.report-outline a {
  display: block;
}

.report-outline strong {
  color: #10213f;
  font-size: 17px;
  line-height: 24px;
}

.report-outline span {
  margin-top: 6px;
  color: #607592;
  font-size: 13px;
  line-height: 20px;
}

.report-outline nav {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #e4edf8;
}

.report-outline a {
  padding: 8px 0;
  color: #425b7c;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.report-outline a:hover {
  color: #1769f6;
}

.report-document {
  min-width: 0;
  padding: 42px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, .8) inset,
    0 28px 70px rgba(39, 86, 166, .14);
}

@media (max-width: 768px) {
  .report-layout {
    grid-template-columns: 1fr;
  }

  .report-outline {
    position: static;
  }

  .report-document {
    padding: 20px 14px;
  }
}
</style>
