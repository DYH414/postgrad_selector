import request from '@/utils/request'

const RAG_LONG_TASK_TIMEOUT = 5 * 60 * 1000

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

export function generateDatabaseDocuments(params) {
  return request({
    url: '/postgrad/rag/generate-documents',
    method: 'post',
    params,
    timeout: RAG_LONG_TASK_TIMEOUT
  })
}

export function reindexDatabaseRag(params) {
  return request({
    url: '/postgrad/rag/reindex-database',
    method: 'post',
    params,
    timeout: RAG_LONG_TASK_TIMEOUT
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
