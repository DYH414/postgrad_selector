import request from './request'

export function postAiStart(data) {
  return request({ url: '/app/ai-recommend/start', method: 'post', data })
}

export function postAiChat(data) {
  return request({ url: '/app/ai-recommend/chat', method: 'post', data })
}

export function postAiGenerateReport(data) {
  return request({ url: '/app/ai-recommend/generate-report', method: 'post', data })
}

export function getAiReport(id) {
  return request({ url: '/app/ai-recommend/report/' + id, method: 'get' })
}

export function getAiReports() {
  return request({ url: '/app/ai-recommend/reports', method: 'get' })
}

export function postAiResume(data) {
  return request({ url: '/app/ai-recommend/resume', method: 'post', data })
}
