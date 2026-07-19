import request from '@/utils/request'

export function getAiObserveOverview() {
  return request({
    url: '/postgrad/ai-observe/overview',
    method: 'get'
  })
}

export function getRecentToolTraces(limit = 50) {
  return request({ url: '/postgrad/ai-observe/tool-traces', method: 'get', params: { limit } })
}

export function getRunToolTraces(runId) {
  return request({ url: `/postgrad/ai-observe/runs/${runId}/tool-traces`, method: 'get' })
}

export function getRunDetail(runId) {
  return request({ url: `/postgrad/ai-observe/runs/${runId}`, method: 'get' })
}
