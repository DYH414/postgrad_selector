import request from '@/utils/request'

export function listWorkspaceStats(params) {
  return request({
    url: '/postgrad/workspace/stats',
    method: 'get',
    params
  })
}

export function listWorkspaceSchools(params) {
  return request({
    url: '/postgrad/workspace/schools',
    method: 'get',
    params
  })
}

export function getSchoolWorkspace(id, params) {
  return request({
    url: '/postgrad/school/' + id + '/workspace',
    method: 'get',
    params
  })
}
