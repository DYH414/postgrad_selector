import assert from 'node:assert/strict'
import { formatAdmissionQuota } from './admissionDisplay.mjs'

assert.deepEqual(
  formatAdmissionQuota({ unifiedExamQuota: 12, planCount: 20 }),
  { label: '预计招生', value: 12, hint: '统考名额' }
)

assert.deepEqual(
  formatAdmissionQuota({ planCount: 20 }),
  { label: '预计招生', value: 20, hint: '计划人数' }
)

assert.deepEqual(
  formatAdmissionQuota({ unifiedExamQuota: 0, planCount: null }),
  { label: '预计招生', value: '-', hint: '待补' }
)
