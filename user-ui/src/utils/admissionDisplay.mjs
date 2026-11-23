function positiveCount(value) {
  const count = Number(value)
  return Number.isFinite(count) && count > 0 ? Math.trunc(count) : null
}

function isBlankLabel(value) {
  if (value == null) return true
  const text = String(value).trim()
  return text === '' || text === '-' || text === '—' || text === '暂无' || /^-\s*-\s*-?$/.test(text)
}

export function displayMetric(value) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '' && !isBlankLabel(value)) {
    return value
  }
  if (value == null || value === '' || isBlankLabel(value)) return '暂无'
  return value
}

export function formatAdmissionRange(low, high, fallbackLabel) {
  const hasLow = low != null && low !== '' && !isBlankLabel(low)
  const hasHigh = high != null && high !== '' && !isBlankLabel(high)
  if (hasLow && hasHigh) {
    if (Number(low) === Number(high)) return String(low)
    return `${low} - ${high}`
  }
  if (!hasLow && hasHigh) return `最高分 ${high}`
  if (hasLow && !hasHigh) return `最低分 ${low}`
  if (fallbackLabel != null && !isBlankLabel(fallbackLabel)) return String(fallbackLabel)
  return '暂无'
}

export function presentationSourceUrl(url) {
  if (!url) return url
  const text = String(url)
  let decoded = text
  try {
    decoded = decodeURI(text)
  } catch {
    decoded = text
  }
  if (/dai408_scores\.json/i.test(text) || /dai408_scores\.json/i.test(decoded)) {
    return 'https://laz8noy.github.io/kaoyan408-share/06-院校数据库/院校数据浏览器.html'
  }
  return url
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
    value: '暂无',
    hint: ''
  }
}
