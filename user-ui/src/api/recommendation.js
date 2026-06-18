import request from './request'

export function getRecommendationOptions() {
  return request({ url: '/app/recommendation/options', method: 'get' })
}

export function generateRecommendation(data) {
  return request({ url: '/app/recommendation/generate', method: 'post', data })
}

export function getRecommendationResult(id) {
  return request({ url: '/app/recommendation/result/' + id, method: 'get' })
}
