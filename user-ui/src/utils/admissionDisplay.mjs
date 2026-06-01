function positiveCount(value) {
  const count = Number(value)
  return Number.isFinite(count) && count > 0 ? Math.trunc(count) : null
}

export function formatAdmissionQuota(item = {}) {
  const unifiedExamQuota = positiveCount(item.unifiedExamQuota)
  if (unifiedExamQuota != null) {
    return {
      label: '预计招生',
      value: unifiedExamQuota,
      hint: '统考名额'
    }
  }

  const planCount = positiveCount(item.planCount)
  if (planCount != null) {
    return {
      label: '预计招生',
      value: planCount,
      hint: '计划人数'
    }
  }

  return {
    label: '预计招生',
    value: '-',
    hint: '待补'
  }
}
