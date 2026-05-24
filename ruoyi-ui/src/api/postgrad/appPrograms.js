import request from '@/utils/appRequest'

export function getProgramDetail(programId, params) {
  return request({
    url: '/app/programs/' + programId + '/detail',
    method: 'get',
    params
  })
}

export function comparePrograms(params) {
  return request({
    url: '/app/programs/compare',
    method: 'get',
    params
  })
}
