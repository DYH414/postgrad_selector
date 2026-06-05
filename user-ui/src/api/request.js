import axios from 'axios'
import router from '@/router'

const TOKEN_KEY = 'App-Token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

const service = axios.create({
  baseURL: '/dev-api',
  timeout: 120000
})

service.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, error => Promise.reject(error))

service.interceptors.response.use(
  response => {
    const data = response.data
    // RuoYi convention: HTTP 200 but body.code indicates error
    if (data.code && data.code !== 200) {
      if (data.code === 401) {
        handleUnauthorized()
      }
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return data
  },
  error => {
    // HTTP 401
    if (error.response && error.response.status === 401) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  removeToken()
  const current = router.currentRoute?.value
  if (current && current.path !== '/login') {
    router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
}

export default service
