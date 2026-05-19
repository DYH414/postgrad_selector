import request from '@/utils/request'

export function listCrud(module, query) {
  return request({
    url: `/postgrad/${module}/list`,
    method: 'get',
    params: query
  })
}

export function getCrud(module, id) {
  return request({
    url: `/postgrad/${module}/${id}`,
    method: 'get'
  })
}

export function optionselectCrud(module, query) {
  return request({
    url: `/postgrad/${module}/optionselect`,
    method: 'get',
    params: query
  })
}

export function addCrud(module, data) {
  return request({
    url: `/postgrad/${module}`,
    method: 'post',
    data
  })
}

export function updateCrud(module, data) {
  return request({
    url: `/postgrad/${module}`,
    method: 'put',
    data
  })
}

export function delCrud(module, id) {
  return request({
    url: `/postgrad/${module}/${id}`,
    method: 'delete'
  })
}
