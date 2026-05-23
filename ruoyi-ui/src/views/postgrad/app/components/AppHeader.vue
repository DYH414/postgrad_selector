<template>
  <div class="app-header">
    <span class="app-logo" @click="$router.push('/app/recommend')">考研择校</span>
    <div>
      <router-link to="/app/profile" class="nav-link" :class="{ active: currentPage === 'profile' }">考研画像</router-link>
      <router-link to="/app/recommend" class="nav-link" :class="{ active: currentPage === 'recommend' }">智能推荐</router-link>
      <router-link to="/app/favorites" class="nav-link" :class="{ active: currentPage === 'favorites' }">我的收藏</router-link>
      <router-link to="/app/history" class="nav-link" :class="{ active: currentPage === 'history' }">推荐历史</router-link>
      <span class="nav-link logout" @click="handleLogout">退出</span>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex'
export default {
  name: 'AppHeader',
  props: { currentPage: { type: String, default: '' } },
  methods: {
    ...mapActions('appUser', ['Logout']),
    handleLogout() {
      this.Logout().then(() => { this.$router.push('/app/login') })
    }
  }
}
</script>

<style scoped>
.app-header { display: flex; justify-content: space-between; align-items: center; padding: 0 24px; height: 56px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
.app-logo { font-size: 18px; font-weight: 700; color: #1890ff; cursor: pointer; }
.nav-link { margin-left: 20px; color: #606266; text-decoration: none; font-size: 14px; }
.nav-link:hover, .nav-link.active { color: #1890ff; }
.nav-link.logout { cursor: pointer; }
</style>
