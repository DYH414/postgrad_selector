<template>
  <header class="app-header">
    <div class="brand" @click="$router.push('/app/recommend')">
      <span class="brand-mark">
        <i class="el-icon-s-platform"></i>
      </span>
      <span>
        <strong>408 考研推荐平台</strong>
        <em>智能择校 · 精准推荐</em>
      </span>
    </div>

    <nav class="nav">
      <router-link to="/app/recommend" class="nav-link" :class="{ active: currentPage === 'recommend' }">首页</router-link>
      <router-link to="/app/results" class="nav-link" :class="{ active: currentPage === 'results' }">推荐</router-link>
      <router-link to="/app/results?tab=compare" class="nav-link" :class="{ active: currentPage === 'compare' }">对比与备选</router-link>
      <router-link to="/app/favorites" class="nav-link" :class="{ active: currentPage === 'favorites' }">我的</router-link>
    </nav>

    <div class="header-actions">
      <div class="search-box">
        <i class="el-icon-search"></i>
        <span>搜索院校 / 专业 / 关键词</span>
      </div>
      <el-badge :value="12" class="notice-badge">
        <el-button icon="el-icon-bell" circle size="mini"></el-button>
      </el-badge>
      <button v-if="!hasToken" class="login-entry" type="button" @click="goLogin">登录/注册</button>
      <div v-else class="user-chip" @click="$router.push('/app/profile')">
        <span class="avatar">408</span>
        <span>我的账号</span>
        <i class="el-icon-arrow-down"></i>
      </div>
      <button v-if="hasToken" class="logout-entry" type="button" @click="logout">退出</button>
    </div>
  </header>
</template>

<script>
import { getAppToken, removeAppToken } from '@/utils/appAuth'

export default {
  name: 'AppHeader',
  props: { currentPage: { type: String, default: '' } },
  data() {
    return { hasToken: !!getAppToken() }
  },
  watch: {
    '$route.fullPath'() {
      this.hasToken = !!getAppToken()
    }
  },
  methods: {
    goLogin() {
      this.$router.push({
        path: '/app/login',
        query: { redirect: this.$route.fullPath }
      })
    },
    logout() {
      removeAppToken()
      this.hasToken = false
      this.$message.success('已退出登录')
      if (!['/app/recommend', '/app/results'].includes(this.$route.path)) {
        this.goLogin()
      }
    }
  }
}
</script>

<style scoped>
.app-header {
  height: 58px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid #e7edf8;
  box-shadow: 0 8px 24px rgba(36, 78, 156, 0.08);
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
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(135deg, #2f7bff, #1554e8);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.24);
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
  gap: 34px;
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
}

.nav-link:hover,
.nav-link.active {
  color: #1769f6;
  border-bottom-color: #1769f6;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-box {
  width: 280px;
  height: 34px;
  border: 1px solid #dce4f2;
  border-radius: 8px;
  background: #fbfcff;
  color: #9aa7bc;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  font-size: 13px;
}

.notice-badge /deep/ .el-badge__content {
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
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #eef2f7, #d6dbe4);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.login-entry {
  height: 34px;
  padding: 0 16px;
  border: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, #2f7bff, #1554e8);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22);
}

.logout-entry {
  height: 32px;
  padding: 0 10px;
  border: 1px solid #dce4f2;
  border-radius: 8px;
  background: #fff;
  color: #64748b;
  cursor: pointer;
}

.logout-entry:hover {
  color: #1769f6;
  border-color: #bfd3ff;
}

@media (max-width: 960px) {
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

  .search-box {
    display: none;
  }
}
</style>
