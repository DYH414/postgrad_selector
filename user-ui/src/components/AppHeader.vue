<template>
  <header class="app-header">
    <div class="brand" @click="router.push('/')">
      <span class="brand-mark">
        <strong>408</strong>
      </span>
      <span class="brand-text">
        <strong>408 考研筛选平台</strong>
        <em>智能择校 · 精准筛选</em>
      </span>
    </div>

    <nav class="nav">
      <router-link to="/" class="nav-link" :class="{ active: currentPage === 'home' }">首页</router-link>
      <router-link to="/results" class="nav-link" :class="{ active: currentPage === 'results' }">筛选结果</router-link>
      <router-link to="/results?tab=compare" class="nav-link" :class="{ active: currentPage === 'compare' }">对比与备选</router-link>
      <router-link to="/ai-recommend" class="nav-link" :class="{ active: currentPage === 'ai' }">AI 推荐</router-link>
      <router-link to="/profile" class="nav-link" :class="{ active: currentPage === 'profile' }">我的</router-link>
    </nav>

    <div class="header-actions">
      <div class="search-box" :class="{ 'search-box--focused': searchFocused }">
        <i class="el-icon-search" @click="doSearch"></i>
        <el-input
          ref="searchInputRef"
          v-model="searchKeyword"
          placeholder="搜索院校 / 专业 / 关键词"
          clearable
          @focus="searchFocused = true"
          @blur="searchFocused = false"
          @keyup.enter="doSearch"
          @clear="searchKeyword = ''"
        />
      </div>
      <el-badge :value="12" class="notice-badge">
        <el-button icon="el-icon-bell" circle size="small"></el-button>
      </el-badge>
      <button v-if="!hasToken" class="login-entry" type="button" @click="goLogin">登录/注册</button>
      <div v-else class="user-chip" @click="router.push('/profile')">
        <span class="avatar">408</span>
        <span>我的账号</span>
        <i class="el-icon-arrow-down"></i>
      </div>
      <button v-if="hasToken" class="logout-entry" type="button" @click="logout">退出</button>
    </div>
  </header>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/api/request'
import { removeToken } from '@/api/request'
import { getAiReport } from '@/api/ai'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const props = defineProps({
  currentPage: { type: String, default: '' }
})

const hasToken = ref(!!getToken())

const searchKeyword = ref('')
const searchFocused = ref(false)
const searchInputRef = ref(null)

function doSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  router.push({ path: '/results', query: { keyword } })
}

watch(
  () => route.fullPath,
  () => {
    hasToken.value = !!getToken()
  }
)

watch(
  () => route.query.keyword,
  (kw) => {
    searchKeyword.value = kw || ''
  },
  { immediate: true }
)

function goLogin() {
  router.push({
    path: '/login',
    query: { redirect: route.fullPath }
  })
}

function logout() {
  removeToken()
  userStore.logoutAction()
  hasToken.value = false
  ElMessage.success('已退出登录')
  if (route.path !== '/') {
    goLogin()
  }
}

// ---- 全局报告完成通知（跨页面） ----
const reportPollTimer = ref(null)
const notifiedIds = new Set()

function pollPendingReports() {
  try {
    const pending = JSON.parse(sessionStorage.getItem('pending_reports') || '[]')
    if (!pending.length) return
    const stillPending = []
    Promise.allSettled(pending.map(p => getAiReport(p.id))).then(results => {
      results.forEach((r, i) => {
        const info = pending[i]
        if (r.status === 'fulfilled' && r.value?.data) {
          const d = r.value.data
          if (d.status !== 'PENDING' && !notifiedIds.has(info.id)) {
            notifiedIds.add(info.id)
            ElNotification({
              title: 'AI 推荐报告已生成',
              message: '点击查看你的择校推荐报告',
              type: 'success',
              duration: 8000,
              onClick: () => router.push({ name: 'AiReport', params: { id: info.id } })
            })
            return // 已完成，不保留
          }
          if (d.status === 'PENDING' && Date.now() - info.ts < 10 * 60_000) {
            stillPending.push(info) // 10分钟内的继续等
          }
        }
      })
      sessionStorage.setItem('pending_reports', JSON.stringify(stillPending))
    })
  } catch (_) {}
}

onMounted(() => {
  pollPendingReports()
  reportPollTimer.value = setInterval(pollPendingReports, 10_000)
})

onBeforeUnmount(() => {
  if (reportPollTimer.value) clearInterval(reportPollTimer.value)
})
</script>

<style scoped>
.app-header {
  height: 62px;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid var(--line);
  box-shadow: 0 12px 32px rgba(36, 78, 156, 0.08);
  backdrop-filter: blur(14px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  position: sticky;
  top: 0;
  z-index: 20;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 200px;
  cursor: pointer;
  color: var(--ink-1);
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--brand-gradient);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.24);
}

.brand-mark strong {
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
  font-family: var(--font-mono);
}

.brand-text strong {
  display: block;
  font-size: 16px;
  line-height: 1.1;
  font-weight: 700;
}

.brand-text em {
  display: block;
  font-style: normal;
  margin-top: 2px;
  color: var(--ink-3);
  font-size: 12px;
}

.nav {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 1;
}

.nav-link {
  height: 100%;
  display: inline-flex;
  align-items: center;
  color: var(--ink-2);
  font-weight: 500;
  font-size: 15px;
  text-decoration: none;
  border-bottom: 3px solid transparent;
  transition: color var(--t-fast) var(--ease), border-color var(--t-fast) var(--ease);
}

.nav-link:hover {
  color: var(--brand);
}

.nav-link.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.search-box {
  width: 240px;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  font-size: 13px;
  transition: border-color var(--t-fast) var(--ease), box-shadow var(--t-fast) var(--ease);
}

.search-box--focused {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px rgba(23, 105, 246, 0.1);
}

.search-box i {
  color: var(--ink-4);
  flex-shrink: 0;
}

.search-box :deep(.el-input) { flex: 1; }
.search-box :deep(.el-input__wrapper) {
  border: 0;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0;
}
.search-box :deep(.el-input__inner) {
  font-size: 13px;
  color: var(--ink-1);
}
.search-box :deep(.el-input__inner::placeholder) { color: var(--ink-5); }
.search-box :deep(.el-input__clear) { color: var(--ink-4); }

.notice-badge :deep(.el-badge__content) {
  border: 0;
  background: var(--danger) !important;
  color: #fff;
  font-size: 10px;
  font-family: var(--font-mono);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink-2);
  white-space: nowrap;
  cursor: pointer;
  transition: color var(--t-fast) var(--ease);
}
.user-chip:hover { color: var(--brand); }

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--font-mono);
}

.login-entry {
  height: 34px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: var(--brand-gradient);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22);
  transition: box-shadow var(--t-fast) var(--ease), transform var(--t-fast) var(--ease);
}
.login-entry:hover {
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.32);
}

.logout-entry {
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--bg-elev);
  color: var(--ink-3);
  font-size: 12px;
  cursor: pointer;
  transition: color var(--t-fast) var(--ease), border-color var(--t-fast) var(--ease);
}
.logout-entry:hover {
  color: var(--brand);
  border-color: var(--brand-soft-2);
}

@media (max-width: 960px) {
  .search-box { display: none; }
}

@media (max-width: 1160px) {
  .app-header {
    height: auto;
    min-height: 62px;
    flex-wrap: wrap;
    padding: 12px 18px;
  }
  .brand { min-width: auto; }
  .nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
    gap: 20px;
  }
  .nav-link { height: 40px; white-space: nowrap; }
}
</style>
