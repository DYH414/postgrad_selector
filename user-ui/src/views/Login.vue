<template>
  <div class="app-login">
    <div class="login-card">
      <h2>考研择校决策平台</h2>
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-position="top">
            <el-form-item label="手机号/邮箱" prop="account">
              <el-input v-model="loginForm.account" placeholder="请输入手机号或邮箱" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" show-password @keyup.enter="handleLogin" />
            </el-form-item>
            <el-button type="primary" :loading="loading" style="width:100%" @click="handleLogin">登 录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-position="top">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="registerForm.phone" placeholder="请输入手机号（选填）" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="registerForm.email" placeholder="请输入邮箱（选填）" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="至少6位密码" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="再次输入密码" show-password />
            </el-form-item>
            <el-button type="primary" :loading="loading" style="width:100%" @click="handleRegister">注 册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { register } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)

const loginForm = reactive({ account: '', password: '' })
const registerForm = reactive({ phone: '', email: '', password: '', confirmPassword: '' })

const loginFormRef = ref(null)
const registerFormRef = ref(null)

const validateConfirm = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次密码不一致'))
  } else {
    callback()
  }
}

const validateAccount = (rule, value, callback) => {
  if (!registerForm.phone && !registerForm.email) {
    callback(new Error('手机号或邮箱至少填写一项'))
  } else {
    callback()
  }
}

const loginRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  phone: [{ validator: validateAccount, trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: validateConfirm, trigger: 'blur' }]
}

function handleLogin() {
  loginFormRef.value.validate(valid => {
    if (!valid) return
    loading.value = true
    userStore.loginAction(loginForm).then(() => {
      router.push(route.query.redirect || '/profile')
    }).catch(() => {
      ElMessage.error('登录失败，请检查账号密码')
    }).finally(() => { loading.value = false })
  })
}

function handleRegister() {
  registerFormRef.value.validate(valid => {
    if (!valid) return
    loading.value = true
    register(registerForm).then(res => {
      ElMessage.success(res.msg || '注册成功，请登录')
      activeTab.value = 'login'
      loginForm.account = registerForm.phone || registerForm.email
    }).finally(() => { loading.value = false })
  })
}
</script>

<style scoped>
.app-login {
  display: flex; justify-content: center; align-items: center;
  min-height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px; padding: 40px; background: #fff; border-radius: 12px; box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.login-card h2 { text-align: center; margin-bottom: 24px; color: #303133; }
</style>
