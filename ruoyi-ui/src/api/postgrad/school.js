import request from '@/utils/request'

// 查询学校列表
export function listSchool(query) {
  return request({
    url: '/postgrad/school/list',
    method: 'get',
    params: query
  })
}

// 查询学校详细
export function getSchool(id) {
  return request({
    url: '/postgrad/school/' + id,
    method: 'get'
  })
}

// 学校选择框
export function optionselectSchool() {
  return request({
    url: '/postgrad/school/optionselect',
    method: 'get'
  })
}

// 新增学校
export function addSchool(data) {
  return request({
    url: '/postgrad/school',
    method: 'post',
    data: data
  })
}

// 修改学校
export function updateSchool(data) {
  return request({
    url: '/postgrad/school',
    method: 'put',
    data: data
  })
}

// 删除学校
export function delSchool(id) {
  return request({
    url: '/postgrad/school/' + id,
    method: 'delete'
  })
}
