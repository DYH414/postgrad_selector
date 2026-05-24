import request from '@/utils/appRequest'

export function history() {
  return request({ url: '/app/recommendation/history', method: 'get' })
}

export function historyDetail(id) {
  return request({ url: '/app/recommendation/history/' + id, method: 'get' })
}
