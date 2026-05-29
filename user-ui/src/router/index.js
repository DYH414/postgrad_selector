import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/request'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '我的画像' }
  },
  {
    path: '/recommend',
    name: 'Recommend',
    component: () => import('@/views/Recommend.vue'),
    meta: { title: '智能推荐' }
  },
  {
    path: '/ai-report/:id',
    name: 'AiReport',
    component: () => import('@/views/AiReport.vue'),
    meta: { title: 'AI 推荐报告' }
  },
  {
    path: '/ai-history',
    name: 'AiHistory',
    component: () => import('@/views/AiHistory.vue'),
    meta: { title: 'AI 推荐记录' }
  },
  {
    path: '/results',
    name: 'Results',
    component: () => import('@/views/Results.vue'),
    meta: { title: '推荐结果' }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/Favorites.vue'),
    meta: { title: '我的收藏' }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/History.vue'),
    meta: { title: '推荐历史' }
  },
  {
    path: '/history/:id',
    name: 'HistoryDetail',
    component: () => import('@/views/HistoryDetail.vue'),
    meta: { title: '推荐详情' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  if (to.meta.public) return next()

  const token = getToken()
  if (!token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  next()
})

export default router
