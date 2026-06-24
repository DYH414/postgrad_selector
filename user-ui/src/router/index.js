import { createRouter, createWebHistory } from 'vue-router'
import { getToken, removeToken } from '@/api/request'

const ONBOARDED_KEY = 'App-Onboarded'

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
    component: () => import('@/views/Recommend.vue'),
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
    meta: { title: '规则筛选' }
  },
  {
    path: '/ai-recommend',
    name: 'AiRecommend',
    component: () => import('@/views/ai-recommend-v2/AiRecommendV2Workspace.vue'),
    meta: { title: 'AI 推荐' }
  },
  {
    path: '/ai-report/:id',
    redirect: to => ({ name: 'AiReportV2', params: { id: to.params.id } })
  },
  {
    path: '/ai-report-v2/:id',
    name: 'AiReportV2',
    component: () => import('@/views/ai-recommend-v2/components/ReportView.vue'),
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
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to, from, next) => {
  if (to.meta.public) return next()

  const token = getToken()
  if (!token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  // 登录用户首次访问时检测画像 → 无画像自动引导
  try {
    const userStoreModule = await import('@/stores/user')
    const userStore = userStoreModule.useUserStore()

    if (!userStore.userId) {
      await userStore.fetchMe()
    }

    if (!userStore.profile && to.path !== '/profile') {
      if (!localStorage.getItem(ONBOARDED_KEY)) {
        localStorage.setItem(ONBOARDED_KEY, '1')
        return next({ path: '/profile' })
      }
    }

    if (userStore.profile && localStorage.getItem(ONBOARDED_KEY)) {
      localStorage.removeItem(ONBOARDED_KEY)
    }
  } catch {
    removeToken()
    return next({ path: '/login' })
  }

  next()
})

export default router
