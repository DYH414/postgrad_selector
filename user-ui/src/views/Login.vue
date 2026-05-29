<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2>考研择校决策平台</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="手机号/邮箱" prop="account">
          <el-input v-model="form.account" placeholder="请输入手机号或邮箱" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="handleLogin">登 录</el-button>
      </el-form>
      <p class="auth-switch">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ account: '', password: '' })
const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.loginAction(form)
    const redirect = route.query.redirect || '/profile'
    router.push(redirect)
  } catch (e) {
    // error message shown by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex; justify-content: center; align-items: center;
  min-height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.auth-card {
  width: 400px; padding: 40px; background: #fff; border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.auth-card h2 { text-align: center; margin-bottom: 24px; color: #303133; }
.auth-switch { text-align: center; margin-top: 16px; color: #909399; font-size: 14px; }
.auth-switch a { color: #409eff; }
</style>
