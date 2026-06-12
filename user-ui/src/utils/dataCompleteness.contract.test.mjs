import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const files = [
  'ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationMapper.xml',
  'ruoyi-postgrad/src/main/resources/mapper/postgrad/ProgramSearchMapper.xml',
  'ruoyi-postgrad/src/main/resources/mapper/postgrad/AiDatabaseToolMapper.xml'
]

for (const file of files) {
  const source = readFileSync(file, 'utf8')
  assert.doesNotMatch(
    source,
    /coalesce\(q\.completeness_level,\s*'C'\)\s+as\s+dataCompleteness/i,
    `${file} must use the unified dynamic completeness expression`
  )
  assert.match(source, /q\.completeness_level\s+is\s+not\s+null/i, `${file} must respect stored quality first`)
  assert.match(source, /sc\.score_line\s+is\s+not\s+null/i, `${file} must compute fallback from score data`)
  assert.match(source, /ar\.avg_admitted_score\s+is\s+not\s+null/i, `${file} must compute fallback from result data`)
}

const syncSql = readFileSync('sql/sync_program_year_data_quality.sql', 'utf8')
assert.match(syncSql, /THEN\s+'A'/i, 'quality sync must be able to produce A level')

console.log('Data completeness SQL contract checks passed')
