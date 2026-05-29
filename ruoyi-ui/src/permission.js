import router from './router'
import store from './store'
import { Message } from 'element-ui'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'

import { isRelogin } from '@/utils/request'

NProgress.configure({ showSpinner: false })

/**
 * 路由守卫 — 分三个域处理:
 *   1. 管理后台 (有 admin token)
 *   2. 用户端 /app/* (有 app token)
 *   3. 未认证 (重定向到对应登录页)
 */
router.beforeEach((to, from, next) => {
  NProgress.start()

  const adminToken = getToken()
  // ── 管理后台 ──
  if (adminToken) {
    handleAdminRoute(to, from, next)
  }
  // ── 未认证 ──
  else {
    handleGuestRoute(to, next)
  }
})

router.afterEach(() => {
  NProgress.done()
})

// ═══════════════════════════════════════════
// Admin Route Handler
// ═══════════════════════════════════════════
function handleAdminRoute(to, from, next) {
  to.meta.title && store.dispatch('settings/setTitle', to.meta.title)

  // 已登录跳到首页
  if (to.path === '/login') return done(next, { path: '/' })
  // 锁屏
  const isLock = store.getters.isLock
  if (isLock && to.path !== '/lock') return done(next, { path: '/lock' })
  if (!isLock && to.path === '/lock') return done(next, { path: '/' })

  // 动态路由已加载
  if (store.getters.roles.length > 0) return next()

  // 首次加载：拉角色 → 生成路由 → addRoutes
  isRelogin.show = true
  store.dispatch('GetInfo')
    .then(() => {
      isRelogin.show = false
      return store.dispatch('GenerateRoutes')
    })
    .then(accessRoutes => {
      router.addRoutes(accessRoutes)
      next({ ...to, replace: true })
    })
    .catch(err => {
      store.dispatch('LogOut').then(() => {
        Message.error(err)
        next({ path: '/' })
      })
    })
}

// ═══════════════════════════════════════════
// Guest Route Handler
// ═══════════════════════════════════════════
function handleGuestRoute(to, next) {
  const whiteList = ['/login', '/register']
  if (whiteList.includes(to.path)) return next()
  next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
}

function done(next, destination) {
  next(destination)
  NProgress.done()
}
