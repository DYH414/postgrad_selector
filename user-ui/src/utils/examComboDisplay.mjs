const KNOWN_COMBOS = {
  11408: ['101', '201', '301', '408'],
  22408: ['101', '204', '302', '408']
}

function isBlank(value) {
  if (value == null) return true
  const text = String(value).trim()
  return text === '' || text === '-' || text === '—' || text === '暂无'
}

export function parseExamSubjects(value) {
  if (isBlank(value)) return []
  const text = String(value).trim()
  if (KNOWN_COMBOS[text]) return [...KNOWN_COMBOS[text]]
  return text.split(/[,/，、\s]+/).map(part => part.trim()).filter(Boolean)
}

export function examComboCode(value) {
  const text = isBlank(value) ? '' : String(value).trim()
  if (KNOWN_COMBOS[text]) return text
  const subjects = parseExamSubjects(value)
  if (!subjects.length) return null
  const key = subjects.join(',')
  return Object.keys(KNOWN_COMBOS).find(code => KNOWN_COMBOS[code].join(',') === key) || null
}

export function formatExamCombo(value) {
  const subjects = parseExamSubjects(value)
  const code = examComboCode(value)
  if (code && subjects.length) return `${code}（${subjects.join('/')}）`
  if (subjects.length) return subjects.join('/')
  if (code) return code
  return '暂无'
}

export function hasExamCombo(value) {
  return formatExamCombo(value) !== '暂无'
}
