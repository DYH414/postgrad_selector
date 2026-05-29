<template>
  <router-view />
</template>

<script setup>
import { watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/api/request'

const router = useRouter()
const userStore = useUserStore()

// Restore user session on app init if token exists
if (getToken()) {
  userStore.fetchMe().catch(() => {
    // token expired or invalid — clear it
    userStore.logoutAction()
  })
}

// Watch for unauthorized redirects from interceptor
watch(() => userStore.token, (val) => {
  if (!val && router.currentRoute?.value?.path !== '/login') {
    router.push('/login')
  }
})
</script>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans SC', sans-serif;
}
</style>
