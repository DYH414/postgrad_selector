import request from './request'

export function getProfile() {
  return request({ url: '/app/profile', method: 'get' })
}

export function saveProfile(data) {
  return request({ url: '/app/profile', method: 'post', data })
}
