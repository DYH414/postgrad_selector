<template>
  <div class="app-login">
    <div class="login-shell">
      <section class="login-intro">
        <div class="brand-mark">408</div>
        <h1>考研择校决策平台</h1>
        <p>登录后先建立你的考研画像，系统会基于预估分、目标地区和报考偏好，帮你完成筛选、对比和 AI 分析。</p>
        <div class="intro-steps">
          <div>
            <span>1</span>
            <b>填写画像</b>
            <em>让平台知道你的分数与目标</em>
          </div>
          <div>
            <span>2</span>
            <b>规则筛选</b>
            <em>快速得到可比较的院校列表</em>
          </div>
          <div>
            <span>3</span>
            <b>AI 推荐</b>
            <em>解释为什么这些学校值得关注</em>
          </div>
        </div>
      </section>

      <div class="login-card">
        <div class="login-card-title">
          <h2>{{ activeTab === 'login' ? '欢迎回来' : '创建账号' }}</h2>
          <p>{{ activeTab === 'login' ? '登录后将进入你的考研画像页' : '注册后即可保存画像和备选学校' }}</p>
        </div>
        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-position="top">
              <el-form-item label="手机号/邮箱" prop="account">
                <el-input v-model="loginForm.account" placeholder="请输入手机号或邮箱" size="large" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" show-password size="large" @keyup.enter="handleLogin" />
              </el-form-item>
              <el-button type="primary" :loading="loading" size="large" class="submit-button" @click="handleLogin">登录</el-button>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="注册" name="register">
            <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-position="top">
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="registerForm.phone" placeholder="请输入手机号（选填）" size="large" />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="registerForm.email" placeholder="请输入邮箱（选填）" size="large" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="registerForm.password" type="password" placeholder="至少6位密码" show-password size="large" />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="registerForm.confirmPassword" type="password" placeholder="再次输入密码" show-password size="large" />
              </el-form-item>
              <el-button type="primary" :loading="loading" size="large" class="submit-button" @click="handleRegister">注册</el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background:
    radial-gradient(circle at 18% 14%, rgba(47, 122, 255, 0.22), transparent 28%),
    radial-gradient(circle at 82% 20%, rgba(97, 218, 251, 0.18), transparent 24%),
    linear-gradient(135deg, #eef6ff 0%, #f8fbff 52%, #edf4ff 100%);
}

.login-shell {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 430px;
  gap: 28px;
  align-items: stretch;
}

.login-intro,
.login-card {
  border: 1px solid rgba(43, 126, 255, 0.16);
  border-radius: 24px;
  background: rgba(255,255,255,0.92);
  box-shadow: 0 24px 70px rgba(32, 80, 142, 0.14);
}

.login-intro {
  min-height: 560px;
  padding: 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.login-intro::after {
  content: "";
  position: absolute;
  right: -90px;
  bottom: -120px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(36, 120, 255, 0.14), rgba(55, 190, 255, 0.06));
}

.brand-mark {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 26px;
  border-radius: 18px;
  background: linear-gradient(135deg, #1769f6, #31a0ff);
  color: #fff;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: 0;
}

.login-intro h1 {
  margin: 0;
  color: #07152f;
  font-size: 38px;
  line-height: 1.18;
}

.login-intro p {
  max-width: 560px;
  margin: 18px 0 30px;
  color: #61718a;
  font-size: 16px;
  line-height: 1.85;
}

.intro-steps {
  display: grid;
  gap: 14px;
}

.intro-steps div {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  column-gap: 12px;
  align-items: center;
  max-width: 480px;
  padding: 16px;
  border: 1px solid #e2ecfb;
  border-radius: 16px;
  background: #fbfdff;
}

.intro-steps span {
  grid-row: span 2;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #e9f2ff;
  color: #1769f6;
  font-weight: 800;
}

.intro-steps b {
  color: #10203f;
  font-size: 15px;
}

.intro-steps em {
  color: #7a879a;
  font-style: normal;
  font-size: 13px;
}

.login-card {
  padding: 38px;
}

.login-card-title {
  margin-bottom: 18px;
  text-align: center;
}

.login-card-title h2 {
  margin: 0;
  color: #10203f;
  font-size: 26px;
}

.login-card-title p {
  margin: 8px 0 0;
  color: #718097;
  font-size: 14px;
}

.submit-button {
  width: 100%;
  margin-top: 4px;
}

.login-card :deep(.el-tabs__item) {
  font-weight: 700;
}

.login-card :deep(.el-form-item__label) {
  color: #263955;
  font-weight: 700;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-intro {
    min-height: auto;
    padding: 34px;
  }
}

@media (max-width: 520px) {
  .app-login {
    padding: 20px 12px;
  }

  .login-card,
  .login-intro {
    padding: 24px;
    border-radius: 18px;
  }

  .login-intro h1 {
    font-size: 30px;
  }
}
</style>
