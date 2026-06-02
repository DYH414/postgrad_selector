<template>
  <header class="app-header">
    <div class="brand" @click="router.push('/')">
      <span class="brand-mark">
        <strong>408</strong>
      </span>
      <span>
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
        <i class="el-icon-search"></i>
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
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/api/request'
import { removeToken } from '@/api/request'

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
</script>

<style scoped>
.app-header {
  height: 62px;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid #e7edf8;
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
  min-width: 260px;
  cursor: pointer;
  color: #111827;
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: linear-gradient(135deg, #155eef, #1d9bf0);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.24);
}

.brand-mark strong {
  color: #fff;
  font-size: 11px;
  line-height: 1;
  letter-spacing: 0;
}

.brand strong {
  display: block;
  font-size: 20px;
  line-height: 1.1;
}

.brand em {
  display: block;
  font-style: normal;
  margin-top: 2px;
  color: #7a8aa4;
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
  color: #111827;
  font-weight: 600;
  font-size: 15px;
  text-decoration: none;
  border-bottom: 3px solid transparent;
  transition: color 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.nav-link:hover,
.nav-link.active {
  color: #1769f6;
  border-bottom-color: #1769f6;
}

.nav-link:hover {
  transform: translateY(-1px);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-box {
  width: 280px;
  height: 36px;
  border: 1px solid #dce4f2;
  border-radius: 999px;
  background: #f8fbff;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  font-size: 13px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.search-box--focused {
  border-color: #1769f6;
  box-shadow: 0 0 0 2px rgba(23, 105, 246, 0.1);
}

.search-box i {
  color: #9aa7bc;
  flex-shrink: 0;
}

.search-box :deep(.el-input) {
  flex: 1;
}

.search-box :deep(.el-input__wrapper) {
  border: 0;
  box-shadow: none;
  background: transparent;
  padding: 0;
}

.search-box :deep(.el-input__inner) {
  font-size: 13px;
  color: #1f2937;
}

.search-box :deep(.el-input__inner::placeholder) {
  color: #9aa7bc;
}

.search-box :deep(.el-input__clear) {
  color: #9aa7bc;
}

.notice-badge :deep(.el-badge__content) {
  border: 0;
  top: 5px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #1f2937;
  white-space: nowrap;
  cursor: pointer;
  transition: transform 0.18s ease;
}

.user-chip:hover {
  transform: translateY(-1px);
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e8f1ff, #d7e4f8);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.login-entry {
  height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #1769f6, #1d8cff);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.login-entry:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.28);
}

.logout-entry {
  height: 32px;
  padding: 0 12px;
  border: 1px solid #dce4f2;
  border-radius: 999px;
  background: #fff;
  color: #64748b;
  cursor: pointer;
}

.logout-entry:hover {
  color: #1769f6;
  border-color: #bfd3ff;
}

@media (max-width: 1320px) {
  .search-box {
    display: none;
  }
}

@media (max-width: 1160px) {
  .app-header {
    height: auto;
    min-height: 68px;
    flex-wrap: wrap;
    padding: 14px 18px;
  }

  .brand {
    min-width: auto;
  }

  .nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
    gap: 22px;
  }

  .nav-link {
    height: 42px;
    white-space: nowrap;
  }
}
</style>
