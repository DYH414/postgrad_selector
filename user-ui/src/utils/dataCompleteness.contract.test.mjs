import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const mapper = 'ruoyi-postgrad/src/main/resources/mapper/postgrad/'
const files = [mapper + 'RecommendationMapper.xml', mapper + 'AiDatabaseToolMapper.xml']
const search = readFileSync(mapper + 'ProgramSearchMapper.xml', 'utf8')
assert.match(search, /<include refid="com\.ruoyi\.postgrad\.mapper\.RecommendationMapper\.DynamicDataCompleteness"\/>\s+as\s+dataCompleteness/)

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

const syncSql = readFileSync('sql/90_data_quality_maintenance.sql', 'utf8')
assert.match(syncSql, /THEN\s+'A'/i, 'quality sync must be able to produce A level')

console.log('Data completeness SQL contract checks passed')
