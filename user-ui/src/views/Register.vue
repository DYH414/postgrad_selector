<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2>注册账号</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号（选填）" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱（选填）" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="至少6位密码" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="handleRegister">注 册</el-button>
      </el-form>
      <p class="auth-switch">
        已有账号？<router-link to="/login">去登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ phone: '', email: '', password: '', confirmPassword: '' })

const validateAccount = (rule, value, callback) => {
  if (!form.phone && !form.email) {
    callback(new Error('手机号或邮箱至少填写一项'))
  } else {
    callback()
  }
}

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  phone: [{ validator: validateAccount, trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.registerAction({
      phone: form.phone || undefined,
      email: form.email || undefined,
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    router.push({ path: '/login', query: { account: form.phone || form.email } })
  } catch (e) {
    ElMessage.error(e?.message || '暂时未开放测试通道')
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
