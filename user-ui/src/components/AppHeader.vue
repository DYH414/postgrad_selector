<template>
  <header class="app-header">
    <div class="brand" @click="$router.push('/')">
      <span class="brand-mark">408</span>
      <span><strong>考研择校</strong></span>
    </div>
    <nav class="nav">
      <router-link to="/recommend" class="nav-link" :class="{ active: $route.path === '/recommend' }">智能推荐</router-link>
      <router-link to="/results" class="nav-link" :class="{ active: $route.path === '/results' }">筛选结果</router-link>
      <router-link to="/ai-history" class="nav-link" :class="{ active: $route.path === '/ai-history' }">AI 记录</router-link>
      <router-link to="/favorites" class="nav-link" :class="{ active: $route.path === '/favorites' }">我的收藏</router-link>
    </nav>
    <div class="header-actions">
      <template v-if="!userStore.isLoggedIn">
        <button class="login-btn" @click="$router.push('/login')">登录/注册</button>
      </template>
      <template v-else>
        <router-link to="/profile" class="user-chip">
          <span>我的</span>
        </router-link>
        <button class="logout-btn" @click="handleLogout">退出</button>
      </template>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

async function handleLogout() {
  await userStore.logoutAction()
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  height: 56px; padding: 0 20px;
  background: rgba(255,255,255,0.96); border-bottom: 1px solid #e7edf8;
  box-shadow: 0 4px 16px rgba(36,78,156,0.06);
  display: flex; align-items: center; justify-content: space-between;
  position: sticky; top: 0; z-index: 20;
}
.brand { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.brand-mark {
  width: 30px; height: 30px; border-radius: 6px;
  background: linear-gradient(135deg, #2f7bff, #1554e8); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700;
}
.brand strong { font-size: 17px; }
.nav { display: flex; gap: 28px; }
.nav-link { color: #374151; font-weight: 500; font-size: 14px; text-decoration: none; }
.nav-link:hover, .nav-link.active { color: #1769f6; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.login-btn {
  height: 32px; padding: 0 14px; border: 0; border-radius: 6px;
  background: linear-gradient(135deg, #2f7bff, #1554e8); color: #fff;
  font-weight: 600; cursor: pointer; font-size: 13px;
}
.user-chip { color: #374151; text-decoration: none; font-size: 14px; }
.logout-btn {
  height: 30px; padding: 0 10px; border: 1px solid #dce4f2; border-radius: 6px;
  background: #fff; color: #64748b; cursor: pointer; font-size: 12px;
}
@media (max-width: 768px) {
  .nav { gap: 16px; overflow-x: auto; }
  .nav-link { white-space: nowrap; font-size: 13px; }
}
</style>
