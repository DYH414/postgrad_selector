import request from '@/utils/appRequest'

export function generate() {
  return request({ url: '/app/recommendation/generate', method: 'post' })
}

export function filter(data) {
  return request({ url: '/app/recommendation/filter', method: 'post', data })
}

export function history() {
  return request({ url: '/app/recommendation/history', method: 'get' })
}

export function historyDetail(id) {
  return request({ url: '/app/recommendation/history/' + id, method: 'get' })
}
