import request from '@/utils/request'

export function getRagStatus() {
  return request({
    url: '/postgrad/rag/status',
    method: 'get'
  })
}

export function reindexRag() {
  return request({
    url: '/postgrad/rag/reindex',
    method: 'post'
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
