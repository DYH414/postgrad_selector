import request from '@/utils/request'

export function getRagStatus() {
  return request({
    url: '/postgrad/rag/status',
    method: 'get'
  })
}

export function startIndexBuild(data) {
  return request({
    url: '/postgrad/rag/index-runs',
    method: 'post',
    params: data
  })
}

export function getIndexRun(runId) {
  return request({
    url: `/postgrad/rag/index-runs/${runId}`,
    method: 'get'
  })
}

export function searchTestRag(data) {
  return request({
    url: '/postgrad/rag/search-test',
    method: 'post',
    data
  })
}

export function getRagTraces(limit) {
  return request({
    url: '/postgrad/rag/traces',
    method: 'get',
    params: { limit: limit || 20 }
  })
}
