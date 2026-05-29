import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, logout, me } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/api/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const userId = ref(null)
  const role = ref(null)
  const profile = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  async function loginAction(credentials) {
    const res = await login(credentials)
    token.value = res.token
    userId.value = res.userId
    setToken(res.token)
    return res
  }

  async function registerAction(data) {
    return await register(data)
  }

  async function fetchMe() {
    const res = await me()
    userId.value = res.userId
    role.value = res.role
    profile.value = res.profile || null
    return res
  }

  function setProfile(p) {
    profile.value = p
  }

  async function logoutAction() {
    try { await logout() } catch (e) { /* ignore */ }
    token.value = null
    userId.value = null
    role.value = null
    profile.value = null
    removeToken()
  }

  return {
    token, userId, role, profile, isLoggedIn,
    loginAction, registerAction, fetchMe, setProfile, logoutAction
  }
})
