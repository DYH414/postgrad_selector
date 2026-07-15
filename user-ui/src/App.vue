<template>
  <router-view />
</template>

<script setup>
import { useUserStore } from '@/stores/user'
import { getToken } from '@/api/request'

const userStore = useUserStore()

// Restore user session on app init if token exists
if (getToken()) {
  userStore.fetchMe().catch(() => {
    // token expired or invalid — clear it
    userStore.clearSession()
  })
}
</script>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans SC', sans-serif;
}
</style>
