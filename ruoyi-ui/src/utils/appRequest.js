import axios from 'axios'
import { Message } from 'element-ui'
import { getAppToken, removeAppToken } from '@/utils/appAuth'
import router from '@/router'

const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API,
  timeout: 30000
})

service.interceptors.request.use(config => {
  const token = getAppToken()
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, error => {
  Promise.reject(error)
})

service.interceptors.response.use(res => {
  const code = res.data.code || 200
  const msg = res.data.msg || '请求失败'
  if (code === 401 || msg === '未登录') {
    redirectToAppLogin()
    return Promise.reject(new Error('unauthorized'))
  } else if (code !== 200) {
    Message({ message: msg, type: 'error' })
    return Promise.reject(new Error(msg))
  }
  return res.data
}, error => {
  Message({ message: error.message || '网络异常', type: 'error', duration: 5000 })
  return Promise.reject(error)
})

function redirectToAppLogin() {
  removeAppToken()
  Message({ message: '请先登录后继续操作', type: 'warning' })
  const current = router.currentRoute
  if (current.path !== '/app/login') {
    router.push({
      path: '/app/login',
      query: { redirect: current.fullPath }
    })
  }
}

export default service
