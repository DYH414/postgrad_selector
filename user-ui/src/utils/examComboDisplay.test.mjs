import assert from 'node:assert/strict'
import { examComboCode, formatExamCombo, hasExamCombo, parseExamSubjects } from './examComboDisplay.mjs'

assert.deepEqual(parseExamSubjects('101,201,301,408'), ['101', '201', '301', '408'])
assert.equal(examComboCode('101,201,301,408'), '11408')
assert.equal(examComboCode('11408'), '11408')
assert.equal(examComboCode('101,204,302,408'), '22408')
assert.equal(formatExamCombo('101,201,301,408'), '11408（101/201/301/408）')
assert.equal(formatExamCombo('11408'), '11408（101/201/301/408）')
assert.equal(formatExamCombo('22408'), '22408（101/204/302/408）')
assert.equal(formatExamCombo('101,204,302,810'), '101/204/302/810')
assert.equal(formatExamCombo(null), '暂无')
assert.equal(formatExamCombo('-'), '暂无')
assert.equal(formatExamCombo(''), '暂无')
assert.equal(hasExamCombo('11408'), true)
assert.equal(hasExamCombo('-'), false)
