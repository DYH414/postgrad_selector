import request from './request'

export function getProgramDetail(programId, params) {
  return request({ url: '/app/programs/' + programId + '/detail', method: 'get', params })
}

export function comparePrograms(params) {
  return request({ url: '/app/programs/compare', method: 'get', params })
}

export function searchPrograms(keyword, limit = 20) {
  return request({ url: '/app/programs/search', method: 'get', params: { keyword, limit } })
}
