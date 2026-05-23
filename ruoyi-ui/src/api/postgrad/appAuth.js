import request from '@/utils/appRequest'

export function login(data) {
  return request({ url: '/app/auth/login', method: 'post', data })
}

export function register(data) {
  return request({ url: '/app/auth/register', method: 'post', data })
}

export function logout() {
  return request({ url: '/app/auth/logout', method: 'post' })
}

export function me() {
  return request({ url: '/app/auth/me', method: 'get' })
}
