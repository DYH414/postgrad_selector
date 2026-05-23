import request from '@/utils/request'

// 审核列表
export function listReview(params) {
  return request({ url: '/postgrad/review/list', method: 'get', params })
}

// 详情
export function getReview(id) {
  return request({ url: '/postgrad/review/' + id, method: 'get' })
}

// 通过
export function approveReview(id, data) {
  return request({ url: '/postgrad/review/' + id + '/approve', method: 'post', data })
}

// 驳回
export function rejectReview(id, data) {
  return request({ url: '/postgrad/review/' + id + '/reject', method: 'post', data })
}

// 跳过
export function skipReview(id) {
  return request({ url: '/postgrad/review/' + id + '/skip', method: 'post' })
}

// 批量通过
export function batchApproveReview(data) {
  return request({ url: '/postgrad/review/batch-approve', method: 'post', data })
}

// 统计
export function reviewStats() {
  return request({ url: '/postgrad/review/stats', method: 'get' })
}

// 一键通过学校/专业目录数据
export function autoApproveDirectory() {
  return request({ url: '/postgrad/review/auto-approve-directory', method: 'post' })
}
