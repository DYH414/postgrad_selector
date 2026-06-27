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
