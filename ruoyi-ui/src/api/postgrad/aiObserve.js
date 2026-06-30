import request from '@/utils/request'

export function getAiObserveOverview() {
  return request({
    url: '/postgrad/ai-observe/overview',
    method: 'get'
  })
}
