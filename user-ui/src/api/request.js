import axios from 'axios'
import router from '@/router'
import { ElMessageBox } from 'element-plus'
import { API_BASE_URL } from './base-url'

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
  baseURL: API_BASE_URL,
  timeout: 120000
})

let loginPromptVisible = false

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
      if (isUnauthorizedResponse(data) && !response.config?.suppressUnauthorizedPrompt) {
        handleUnauthorized()
      }
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return data
  },
  error => {
    // HTTP 401
    if (error.response && error.response.status === 401 && !error.config?.suppressUnauthorizedPrompt) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  removeToken()
  if (loginPromptVisible) return
  const current = router.currentRoute?.value
  if (!current || current.path === '/login') return

  loginPromptVisible = true
  ElMessageBox.confirm(
    '请先登录或注册后再使用该功能。',
    '需要登录',
    {
      confirmButtonText: '去登录/注册',
      cancelButtonText: '暂不登录',
      type: 'warning'
    }
  ).then(() => {
    router.push({ path: '/login', query: { redirect: current.fullPath } })
  }).catch(() => {
    // 用户选择继续浏览当前页面
  }).finally(() => {
    loginPromptVisible = false
  })
}

function isUnauthorizedResponse(data) {
  if (data.code === 401) return true
  const message = data.msg || data.message || ''
  return data.code !== 200 && /未登录|请先登录|登录后|Unauthorized|unauthorized/i.test(message)
}

export function requireLoginPrompt(redirect) {
  if (loginPromptVisible) return
  const current = router.currentRoute?.value
  loginPromptVisible = true
  ElMessageBox.confirm(
    '请先登录或注册后再使用该功能。',
    '需要登录',
    {
      confirmButtonText: '去登录/注册',
      cancelButtonText: '暂不登录',
      type: 'warning'
    }
  ).then(() => {
    router.push({ path: '/login', query: { redirect: redirect || current?.fullPath || '/' } })
  }).catch(() => {
    // 用户选择继续浏览当前页面
  }).finally(() => {
    loginPromptVisible = false
  })
}

export default service
