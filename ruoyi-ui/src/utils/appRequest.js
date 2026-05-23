import axios from 'axios'
import { Message } from 'element-ui'
import { getAppToken, removeAppToken } from '@/utils/appAuth'

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
  if (code === 401) {
    removeAppToken()
    Message({ message: '请先登录', type: 'warning' })
    return Promise.reject('unauthorized')
  } else if (code !== 200) {
    Message({ message: res.data.msg || '请求失败', type: 'error' })
    return Promise.reject(new Error(res.data.msg))
  }
  return res.data
}, error => {
  Message({ message: error.message || '网络异常', type: 'error', duration: 5000 })
  return Promise.reject(error)
})

export default service
