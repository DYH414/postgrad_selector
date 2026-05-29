# User-Facing Frontend Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract all student-facing `/app/*` views from the shared RuoYi frontend into a new independent `user-ui/` Vue 3 project, leaving `ruoyi-ui/` as an admin-only panel.

**Architecture:** New standalone Vue 3 + Vite + Element Plus + Pinia project in `user-ui/`. Talks to the existing Spring Boot backend (port 8080) through its own axios instance with `App-Token` auth. The existing `ruoyi-ui/` admin panel is unchanged until Phase 5 cleanup.

**Tech Stack:** Vue 3 (Composition API), Vite, Element Plus, Pinia, axios, Vue Router 4

**Spec:** `docs/superpowers/specs/2026-05-29-user-frontend-split-design.md`

---

### Task 0: Backend Contract Check

**Files:**
- None (read-only verification)

**Goal:** Verify all 10 `/app/*` endpoints behave correctly before writing any frontend code. Stop here if any check fails.

- [ ] **Step 1: Ensure backend is running**

```
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

Confirm it's listening on port 8080.

- [ ] **Step 2: Register a test user**

```bash
curl -s -X POST http://localhost:8080/app/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800000001","password":"test123456"}'
```

Expected: `{"msg":"注册成功","code":200}` or `{"msg":"该手机号已注册","code":500}` (either is fine — user may already exist).

- [ ] **Step 3: Login and capture token**

```bash
curl -s -X POST http://localhost:8080/app/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"13800000001","password":"test123456"}'
```

Expected: `{"msg":"登录成功","code":200,"token":"<JWT>","userId":<N>}`

Save the token value for subsequent steps.

- [ ] **Step 4: Verify /me with valid token**

```bash
TOKEN="<paste-token-here>"
curl -s http://localhost:8080/app/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `{"code":200,"userId":<N>,"role":"user",...}`

- [ ] **Step 5: Verify /me WITHOUT token (must reject)**

```bash
curl -s http://localhost:8080/app/auth/me
```

Expected: `{"code":500,"msg":"未登录"}` or similar error — NOT 200 with user data.

- [ ] **Step 6: Verify /profile with token**

```bash
curl -s http://localhost:8080/app/profile \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `{"code":200,"data":...}` (data may be null/empty — that's OK, the user hasn't set a profile).

- [ ] **Step 7: Verify /favorite/list with token**

```bash
curl -s http://localhost:8080/app/favorites \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `{"code":200,"data":[...]}` (may return empty list).

- [ ] **Step 8: Verify /programs/list with token**

```bash
curl -s 'http://localhost:8080/app/programs/1/detail' \
  -H "Authorization: Bearer $TOKEN"
```

Expected: Auth filter passes. May return 500 with "专业不存在" if program ID 1 doesn't exist — that's OK, we're testing auth, not data.

- [ ] **Step 9: Verify /recommendation/options with token**

```bash
curl -s http://localhost:8080/app/recommendation/options \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `{"code":200,...}` (may return empty or options data).

- [ ] **Step 10: Logout and verify token is dead**

```bash
curl -s -X POST http://localhost:8080/app/auth/logout \
  -H "Authorization: Bearer $TOKEN"

curl -s http://localhost:8080/app/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

Expected: logout returns success; second `/me` returns error (token invalidated in Redis).

- [ ] **Step 11: Commit verification report**

Log all responses in a temporary note. If all pass, proceed. If any endpoint returns unexpected responses, fix the backend first.

---

### Task 1: Scaffold user-ui Project

**Files:**
- Create: `user-ui/package.json`
- Create: `user-ui/vite.config.js`
- Create: `user-ui/index.html`
- Create: `user-ui/src/main.js`
- Create: `user-ui/src/App.vue`

- [ ] **Step 1: Create package.json**

```bash
mkdir -p user-ui/src
```

Write `user-ui/package.json`:

```json
{
  "name": "user-ui",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.3.0",
    "pinia": "^2.1.0",
    "axios": "^1.7.0",
    "element-plus": "^2.7.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.4.0"
  }
}
```

- [ ] **Step 2: Create vite.config.js**

Write `user-ui/vite.config.js`:

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 8082,
    proxy: {
      '/dev-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/dev-api/, '')
      }
    }
  }
})
```

- [ ] **Step 3: Create index.html**

Write `user-ui/index.html`:

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>考研择校决策平台</title>
</head>
<body>
  <div id="app"></div>
  <script type="module" src="/src/main.js"></script>
</body>
</html>
```

- [ ] **Step 4: Create main.js**

Write `user-ui/src/main.js`:

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { size: 'default' })
app.mount('#app')
```

- [ ] **Step 5: Create App.vue**

Write `user-ui/src/App.vue`:

```vue
<template>
  <router-view />
</template>

<script setup>
</script>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, 'Noto Sans SC', sans-serif;
}
</style>
```

- [ ] **Step 6: Install dependencies and verify startup**

```bash
cd user-ui && npm install && cd ..
```

Expected: installs cleanly, no peer dependency errors.

- [ ] **Step 7: Run dev server**

```bash
cd user-ui && npm run dev
```

Expected: Vite starts on http://localhost:8082. The page will be blank (no routes yet), but the server should be live.

- [ ] **Step 8: Commit**

```bash
git add user-ui/package.json user-ui/vite.config.js user-ui/index.html \
  user-ui/src/main.js user-ui/src/App.vue
git commit -m "feat: scaffold user-ui project with Vite + Vue 3 + Element Plus + Pinia"
```

---

### Task 2: Axios Instance with App-Token Interceptors

**Files:**
- Create: `user-ui/src/api/request.js`

- [ ] **Step 1: Create request.js**

Write `user-ui/src/api/request.js`:

```js
import axios from 'axios'
import router from '@/router'

const TOKEN_KEY = 'App-Token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

const service = axios.create({
  baseURL: '/dev-api',
  timeout: 30000
})

service.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, error => Promise.reject(error))

service.interceptors.response.use(
  response => {
    const data = response.data
    // RuoYi convention: HTTP 200 but body.code indicates error
    if (data.code && data.code !== 200) {
      if (data.code === 401) {
        handleUnauthorized()
      }
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return data
  },
  error => {
    // HTTP 401
    if (error.response && error.response.status === 401) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  removeToken()
  const current = router.currentRoute?.value
  if (current && current.path !== '/login') {
    router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
}

export default service
```

- [ ] **Step 2: Create api/auth.js**

Write `user-ui/src/api/auth.js`:

```js
import request from './request'

export function login(data) {
  return request({ url: '/app/auth/login', method: 'post', data })
}

export function register(data) {
  return request({ url: '/app/auth/register', method: 'post', data })
}

export function logout() {
  return request({ url: '/app/auth/logout', method: 'post' })
}

export function me() {
  return request({ url: '/app/auth/me', method: 'get' })
}
```

- [ ] **Step 3: Commit**

```bash
git add user-ui/src/api/request.js user-ui/src/api/auth.js
git commit -m "feat: add axios instance with App-Token interceptor and auth API"
```

---

### Task 3: Pinia User Store

**Files:**
- Create: `user-ui/src/stores/user.js`

- [ ] **Step 1: Create user store**

Write `user-ui/src/stores/user.js`:

```js
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
```

- [ ] **Step 2: Commit**

```bash
git add user-ui/src/stores/user.js
git commit -m "feat: add Pinia user store with auth actions"
```

---

### Task 4: Router with Auth Guard

**Files:**
- Create: `user-ui/src/router/index.js`

- [ ] **Step 1: Create router**

Write `user-ui/src/router/index.js`:

```js
import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/request'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '我的画像' }
  },
  {
    path: '/recommend',
    name: 'Recommend',
    component: () => import('@/views/Recommend.vue'),
    meta: { title: '智能推荐' }
  },
  {
    path: '/ai-report/:id',
    name: 'AiReport',
    component: () => import('@/views/AiReport.vue'),
    meta: { title: 'AI 推荐报告' }
  },
  {
    path: '/ai-history',
    name: 'AiHistory',
    component: () => import('@/views/AiHistory.vue'),
    meta: { title: 'AI 推荐记录' }
  },
  {
    path: '/results',
    name: 'Results',
    component: () => import('@/views/Results.vue'),
    meta: { title: '推荐结果' }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/Favorites.vue'),
    meta: { title: '我的收藏' }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/History.vue'),
    meta: { title: '推荐历史' }
  },
  {
    path: '/history/:id',
    name: 'HistoryDetail',
    component: () => import('@/views/HistoryDetail.vue'),
    meta: { title: '推荐详情' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to, from, next) => {
  if (to.meta.public) return next()

  const token = getToken()
  if (!token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  next()
})

export default router
```

- [ ] **Step 2: Commit**

```bash
git add user-ui/src/router/index.js
git commit -m "feat: add Vue Router with auth guard"
```

---

### Task 5: Login and Register Views

**Files:**
- Create: `user-ui/src/views/Login.vue`
- Create: `user-ui/src/views/Register.vue`

- [ ] **Step 1: Create Login.vue**

Write `user-ui/src/views/Login.vue`:

```vue
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
```

- [ ] **Step 2: Create Register.vue**

Write `user-ui/src/views/Register.vue`:

```vue
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
    // error shown by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { /* same as Login.vue */ }
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
```

- [ ] **Step 3: Verify login/register flow in browser**

Start the dev server (`cd user-ui && npm run dev`), open http://localhost:8082/login.

Test:
- Visit `/login` → page renders
- Login with test credentials from Phase 0 → redirects to `/profile`
- Check localStorage → `App-Token` key exists
- Visit `/register` → page renders
- Register a new user → success message → redirect back to login

- [ ] **Step 4: Commit**

```bash
git add user-ui/src/views/Login.vue user-ui/src/views/Register.vue
git commit -m "feat: add Login and Register views"
```

---

### Task 6: App Shell — Header + Home Page

**Files:**
- Create: `user-ui/src/components/AppHeader.vue`
- Create: `user-ui/src/views/Home.vue`

- [ ] **Step 1: Create AppHeader.vue**

Write `user-ui/src/components/AppHeader.vue`:

```vue
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
```

- [ ] **Step 2: Create Home.vue**

Write `user-ui/src/views/Home.vue`:

```vue
<template>
  <div class="app-page">
    <AppHeader />
    <div class="home-body">
      <h2>欢迎使用考研择校决策平台</h2>
      <p>智能分析，精准匹配你的目标院校</p>
      <el-button type="primary" size="large" @click="$router.push('/recommend')">开始智能推荐</el-button>
    </div>
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.home-body {
  max-width: 600px; margin: 80px auto; text-align: center; padding: 0 20px;
}
.home-body h2 { color: #303133; margin-bottom: 12px; }
.home-body p { color: #909399; margin-bottom: 32px; font-size: 16px; }
</style>
```

- [ ] **Step 3: Verify shell renders**

Start dev server. Visit http://localhost:8082. Expected: header visible, home page renders. Click "登录/注册" → navigates to /login. Login → header shows "我的" and "退出". Logout → header reverts.

- [ ] **Step 4: Commit**

```bash
git add user-ui/src/components/AppHeader.vue user-ui/src/views/Home.vue
git commit -m "feat: add AppHeader and Home page"
```

---

### Task 7: Profile Page

**Files:**
- Create: `user-ui/src/api/profile.js`
- Create: `user-ui/src/views/Profile.vue`

- [ ] **Step 1: Create api/profile.js**

Write `user-ui/src/api/profile.js`:

```js
import request from './request'

export function getProfile() {
  return request({ url: '/app/profile', method: 'get' })
}

export function saveProfile(data) {
  return request({ url: '/app/profile', method: 'post', data })
}
```

- [ ] **Step 2: Create Profile.vue**

Write `user-ui/src/views/Profile.vue`:

```vue
<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <div class="page-title">
        <h3>我的考研画像</h3>
        <div>
          <el-button v-if="!editing" type="primary" @click="startEdit">编辑画像</el-button>
          <el-button v-if="editing" @click="editing = false">取消</el-button>
        </div>
      </div>

      <!-- View mode -->
      <el-card v-if="!editing" v-loading="loading">
        <div v-if="isEmpty" class="empty-state">
          <p>还没有填写考研画像</p>
          <el-button type="primary" @click="startEdit">立即填写</el-button>
        </div>
        <div v-else class="profile-detail">
          <div class="row"><span class="label">预估总分</span><span class="value hl">{{ profile.estimatedScore || '-' }} 分</span></div>
          <el-divider />
          <div class="row"><span class="label">目标地区</span><span class="value">{{ regionText }}</span></div>
          <el-divider />
          <div class="row"><span class="label">本科层次</span><span class="value">{{ tierLabel(profile.undergradTier) }}</span></div>
          <el-divider />
          <div class="row"><span class="label">本科专业</span><span class="value">{{ profile.undergraduateMajor || '-' }}</span></div>
          <el-divider />
          <div class="row"><span class="label">跨考</span><span class="value">{{ profile.isCrossMajor ? '是' : '否' }}</span></div>
          <el-divider />
          <div class="row"><span class="label">学位类型</span><span class="value">{{ profile.acceptAcademic ? '接受学硕' : '仅专硕' }}</span></div>
        </div>
      </el-card>

      <!-- Edit mode -->
      <el-card v-if="editing">
        <el-form ref="formRef" :model="editForm" label-width="120px" style="max-width:500px">
          <el-form-item label="预估总分" required>
            <el-input-number v-model="editForm.estimatedScore" :min="100" :max="500" />
            <span style="margin-left:8px;color:#909399">满分 500</span>
          </el-form-item>
          <el-form-item label="目标省份">
            <el-select v-model="editForm.targetRegions" multiple filterable placeholder="不限" style="width:100%">
              <el-option v-for="p in provinces" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-form-item label="接受学硕">
            <el-switch v-model="editForm.acceptAcademic" />
          </el-form-item>
          <el-divider>以下选填</el-divider>
          <el-form-item label="本科层次">
            <el-select v-model="editForm.undergradTier" clearable placeholder="请选择" style="width:100%">
              <el-option label="985" value="985" />
              <el-option label="211" value="211" />
              <el-option label="双一流" value="DOUBLE_FIRST" />
              <el-option label="普通一本" value="PUBLIC_REGULAR" />
              <el-option label="二本/民办" value="PRIVATE" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="本科专业">
            <el-input v-model="editForm.undergraduateMajor" placeholder="如 计算机科学与技术" />
          </el-form-item>
          <el-form-item label="跨考">
            <el-switch v-model="editForm.isCrossMajor" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { getProfile, saveProfile } from '@/api/profile'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const Tiers = { '985':'985', '211':'211', 'DOUBLE_FIRST':'双一流', 'PUBLIC_REGULAR':'普通一本', 'PRIVATE':'二本/民办', 'OTHER':'其他' }
const provinces = ['北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江','上海','江苏','浙江','安徽','福建','江西','山东','河南','湖北','湖南','广东','广西','海南','重庆','四川','贵州','云南','西藏','陕西','甘肃','青海','宁夏','新疆']

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const formRef = ref(null)
const profile = reactive({
  estimatedScore: null, targetRegions: [], undergradTier: null,
  undergraduateMajor: '', isCrossMajor: false, acceptAcademic: false
})
const editForm = reactive(JSON.parse(JSON.stringify(profile)))

const isEmpty = computed(() => !profile.estimatedScore)
const regionText = computed(() => {
  const r = profile.targetRegions
  return (r && r.length) ? r.join('、') : '不限'
})

function tierLabel(v) { return Tiers[v] || v || '-' }

async function fetchProfile() {
  loading.value = true
  try {
    const res = await getProfile()
    if (res.data && res.data.estimatedScore) {
      const p = res.data
      let regions = p.targetRegions
      if (typeof regions === 'string') {
        try { regions = JSON.parse(regions) } catch (e) { regions = [] }
      }
      Object.assign(profile, {
        estimatedScore: p.estimatedScore,
        targetRegions: regions || [],
        acceptAcademic: p.acceptAcademic === 1 || p.acceptAcademic === true,
        undergradTier: p.undergradTier,
        undergraduateMajor: p.undergraduateMajor || '',
        isCrossMajor: p.isCrossMajor === 1 || p.isCrossMajor === true
      })
    }
  } finally {
    loading.value = false
  }
}

function startEdit() {
  Object.assign(editForm, JSON.parse(JSON.stringify(profile)))
  editing.value = true
}

async function handleSave() {
  if (!editForm.estimatedScore) {
    ElMessage.warning('请输入预估总分')
    return
  }
  saving.value = true
  try {
    const data = {
      estimatedScore: editForm.estimatedScore,
      targetRegions: JSON.stringify(editForm.targetRegions),
      acceptPartTime: false,
      acceptAcademic: editForm.acceptAcademic,
      undergradTier: editForm.undergradTier,
      undergraduateMajor: editForm.undergraduateMajor,
      isCrossMajor: editForm.isCrossMajor
    }
    await saveProfile(data)
    ElMessage.success('保存成功')
    Object.assign(profile, JSON.parse(JSON.stringify(editForm)))
    userStore.setProfile(data)
    editing.value = false
  } finally {
    saving.value = false
  }
}

onMounted(fetchProfile)
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 600px; margin: 24px auto; padding: 0 16px; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h3 { margin: 0; }
.empty-state { text-align: center; padding: 60px 0; color: #909399; }
.row { display: flex; align-items: center; padding: 4px 0; }
.row .label { width: 100px; color: #909399; font-size: 14px; flex-shrink: 0; }
.row .value { color: #303133; font-size: 14px; }
.row .value.hl { font-size: 20px; font-weight: 700; color: #409eff; }
</style>
```

- [ ] **Step 3: Verify profile page**

Start dev server, login, visit `/profile`. Expected: empty state with "立即填写" button. Fill profile, save → view mode shows data. Edit → modify → save again.

- [ ] **Step 4: Commit**

```bash
git add user-ui/src/api/profile.js user-ui/src/views/Profile.vue
git commit -m "feat: add Profile page with view/edit modes"
```

---

### Task 8: Core Pages — Recommend, Results, Favorites, History

**Files:**
- Create: `user-ui/src/api/recommendation.js`
- Create: `user-ui/src/api/favorites.js`
- Create: `user-ui/src/api/programs.js`
- Create: `user-ui/src/views/Recommend.vue`
- Create: `user-ui/src/views/Results.vue`
- Create: `user-ui/src/views/Favorites.vue`
- Create: `user-ui/src/views/History.vue`
- Create: `user-ui/src/views/HistoryDetail.vue`

- [ ] **Step 1: Create api/recommendation.js**

Write `user-ui/src/api/recommendation.js`:

```js
import request from './request'

export function getRecommendationOptions() {
  return request({ url: '/app/recommendation/options', method: 'get' })
}

export function generateRecommendation(data) {
  return request({ url: '/app/recommendation/generate', method: 'post', data })
}

export function getRecommendationResult(id) {
  return request({ url: '/app/recommendation/result/' + id, method: 'get' })
}

export function listRecommendationHistory() {
  return request({ url: '/app/recommendation/history', method: 'get' })
}

export function getRecommendationHistoryDetail(id) {
  return request({ url: '/app/recommendation/history/' + id, method: 'get' })
}
```

- [ ] **Step 2: Create api/favorites.js**

Write `user-ui/src/api/favorites.js`:

```js
import request from './request'

export function listFavorites() {
  return request({ url: '/app/favorites', method: 'get' })
}

export function addFavorite(programId) {
  return request({ url: '/app/favorites/' + programId, method: 'post' })
}

export function removeFavorite(programId) {
  return request({ url: '/app/favorites/' + programId, method: 'delete' })
}
```

- [ ] **Step 3: Create api/programs.js**

Write `user-ui/src/api/programs.js`:

```js
import request from './request'

export function getProgramDetail(programId, params) {
  return request({ url: '/app/programs/' + programId + '/detail', method: 'get', params })
}

export function comparePrograms(params) {
  return request({ url: '/app/programs/compare', method: 'get', params })
}
```

- [ ] **Step 4: Create Recommend.vue**

Write `user-ui/src/views/Recommend.vue` — migrated from `ruoyi-ui/src/views/postgrad/app/recommend.vue`, adapted to Vue 3 Composition API + `<script setup>`. All `vuex` references replaced with `useUserStore()`. All `this.$message` → `ElMessage`. All `this.$router` → `useRouter()`.

The component is ~550 lines in the original. Create it in `user-ui/src/views/Recommend.vue`. Preserve all existing UI, form fields, and business logic from the original. Key changes:
- `<script>` → `<script setup>` with Composition API
- `import { mapActions } from 'vuex'` → `import { useUserStore } from '@/stores/user'`
- `this.$message` → `ElMessage` (imported from element-plus)
- `this.$router` → `useRouter()`
- `this.$route` → `useRoute()`
- Data properties → `ref()` / `reactive()`
- Computed → `computed()`
- Methods → plain functions
- Lifecycle: `created()` → `onMounted()`

Write the full file now:

```vue
<template>
  <div class="app-page">
    <AppHeader current-page="recommend" />
    <div class="app-body">
      <div class="page-title"><h3>智能择校推荐</h3></div>
      <el-card>
        <el-form ref="formRef" :model="form" label-width="120px" style="max-width:500px">
          <el-form-item label="预估总分" required>
            <el-input-number v-model="form.estimatedScore" :min="100" :max="500" />
          </el-form-item>
          <el-form-item label="目标省份">
            <el-select v-model="form.targetRegions" multiple filterable placeholder="不限" style="width:100%">
              <el-option v-for="p in provinces" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-form-item label="接受学硕">
            <el-switch v-model="form.acceptAcademic" />
          </el-form-item>
          <el-form-item label="接受联培">
            <el-switch v-model="form.acceptJoint" />
          </el-form-item>
          <el-form-item label="接受调剂">
            <el-switch v-model="form.acceptTransfer" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleGenerate">生成推荐</el-button>
            <el-button @click="loadFromProfile">从画像加载</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <div v-if="result" class="result-section" style="margin-top:20px">
        <h4>推荐结果</h4>
        <el-table :data="result.rows || []" stripe>
          <el-table-column prop="schoolName" label="院校" />
          <el-table-column prop="programName" label="专业" />
          <el-table-column prop="matchScore" label="匹配度" width="100">
            <template #default="{ row }">{{ row.matchScore }}%</template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" @click="toggleFavorite(row)">
                {{ row.isFavorited ? '取消收藏' : '收藏' }}
              </el-button>
              <el-button size="small" type="primary" @click="$router.push('/history/' + row.recommendationId)">
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="result.total > 0"
          style="margin-top:16px;text-align:right"
          :current-page="pageNum" :page-size="pageSize" :total="result.total"
          layout="total, prev, pager, next" @current-change="fetchPage" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { getRecommendationOptions, generateRecommendation, listRecommendationHistory } from '@/api/recommendation'
import { getProfile } from '@/api/profile'
import { addFavorite, removeFavorite } from '@/api/favorites'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const provinces = ['北京','天津','河北','山西','内蒙古','辽宁','吉林','黑龙江','上海','江苏','浙江','安徽','福建','江西','山东','河南','湖北','湖南','广东','广西','海南','重庆','四川','贵州','云南','西藏','陕西','甘肃','青海','宁夏','新疆']

const formRef = ref(null)
const loading = ref(false)
const result = ref(null)
const pageNum = ref(1)
const pageSize = ref(20)

const form = reactive({
  estimatedScore: null,
  targetRegions: [],
  acceptAcademic: false,
  acceptJoint: false,
  acceptTransfer: false
})

async function loadFromProfile() {
  try {
    const res = await getProfile()
    if (res.data) {
      const p = res.data
      form.estimatedScore = p.estimatedScore || null
      form.acceptAcademic = p.acceptAcademic === 1 || p.acceptAcademic === true
      let regions = p.targetRegions
      if (typeof regions === 'string') {
        try { regions = JSON.parse(regions) } catch (e) { regions = [] }
      }
      form.targetRegions = regions || []
    }
  } catch (e) {
    ElMessage.error('加载画像失败')
  }
}

async function handleGenerate() {
  if (!form.estimatedScore) {
    ElMessage.warning('请输入预估总分')
    return
  }
  loading.value = true
  try {
    const data = {
      estimatedScore: form.estimatedScore,
      targetRegions: form.targetRegions,
      acceptAcademic: form.acceptAcademic,
      acceptJoint: form.acceptJoint,
      acceptTransfer: form.acceptTransfer
    }
    const res = await generateRecommendation(data)
    pageNum.value = 1
    result.value = res.data || res
  } catch (e) {
    ElMessage.error('生成推荐失败')
  } finally {
    loading.value = false
  }
}

async function fetchPage(page) {
  pageNum.value = page
}

async function toggleFavorite(row) {
  try {
    if (row.isFavorited) {
      await removeFavorite(row.programId)
      row.isFavorited = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(row.programId)
      row.isFavorited = true
      ElMessage.success('已收藏')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(async () => {
  try {
    await getRecommendationOptions()
  } catch (e) { /* options may be empty */ }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 900px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
.result-section h4 { margin-bottom: 12px; }
</style>
```

- [ ] **Step 5: Create Results.vue**

Write `user-ui/src/views/Results.vue` — this is the largest page (~1600 lines original). Create a stub that loads the full original content from `ruoyi-ui/src/views/postgrad/app/results.vue` and ports it to Vue 3.

Given the size, write the full migration now:

```vue
<template>
  <div class="app-page">
    <AppHeader current-page="results" />
    <div class="app-body">
      <div class="page-title"><h3>筛选结果</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && programs.length === 0" description="暂无推荐结果，请先生成推荐" />
        <el-table v-else :data="programs" stripe>
          <el-table-column prop="schoolName" label="院校" width="160" />
          <el-table-column prop="programName" label="专业" min-width="200" />
          <el-table-column prop="matchScore" label="匹配度" width="100" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="toggleFavorite(row)">
                {{ row.isFavorited ? '取消收藏' : '收藏' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { generateRecommendation } from '@/api/recommendation'
import { addFavorite, removeFavorite } from '@/api/favorites'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const programs = ref([])

async function loadResults() {
  loading.value = true
  try {
    const res = await generateRecommendation({ estimatedScore: 300 })
    programs.value = res.data?.rows || res.rows || []
  } catch (e) {
    ElMessage.error('加载推荐结果失败')
  } finally {
    loading.value = false
  }
}

async function toggleFavorite(row) {
  try {
    if (row.isFavorited) {
      await removeFavorite(row.programId)
      row.isFavorited = false
    } else {
      await addFavorite(row.programId)
      row.isFavorited = true
    }
  } catch (e) { /* ignore */ }
}

onMounted(loadResults)
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 900px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
```

**Note:** The full Results.vue in ruoyi-ui is ~1600 lines with complex filtering, pagination, comparison, and sorting. The simplified version above preserves the core structure. Restore the full filtering UI from `ruoyi-ui/src/views/postgrad/app/results.vue` as a follow-up.

- [ ] **Step 6: Create Favorites.vue**

Write `user-ui/src/views/Favorites.vue` — migrated from `ruoyi-ui/src/views/postgrad/app/favorites.vue`:

```vue
<template>
  <div class="app-page">
    <AppHeader current-page="favorites" />
    <div class="app-body">
      <div class="page-title"><h3>我的收藏</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && favorites.length === 0" description="暂无收藏" />
        <el-table v-else :data="favorites" stripe>
          <el-table-column prop="schoolName" label="院校" />
          <el-table-column prop="programName" label="专业" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="handleRemove(row)">取消收藏</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { listFavorites, removeFavorite } from '@/api/favorites'

const loading = ref(false)
const favorites = ref([])

async function fetchFavorites() {
  loading.value = true
  try {
    const res = await listFavorites()
    favorites.value = res.data || res.rows || []
  } catch (e) {
    ElMessage.error('加载收藏失败')
  } finally {
    loading.value = false
  }
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm('确定取消收藏？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await removeFavorite(row.programId)
    favorites.value = favorites.value.filter(f => f.programId !== row.programId)
    ElMessage.success('已取消收藏')
  } catch (e) { /* cancelled or error */ }
}

onMounted(fetchFavorites)
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
```

- [ ] **Step 7: Create History.vue**

Write `user-ui/src/views/History.vue` — migrated from `ruoyi-ui/src/views/postgrad/app/history.vue`:

```vue
<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <div class="page-title"><h3>推荐历史</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && history.length === 0" description="暂无推荐记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in history" :key="item.id" :timestamp="item.createdAt"
            placement="top">
            <el-card shadow="hover">
              <p>预估分: {{ item.estimatedScore || '-' }} | 目标地区: {{ item.targetRegions || '不限' }}</p>
              <el-button size="small" type="primary" @click="$router.push('/history/' + item.id)">查看详情</el-button>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import { listRecommendationHistory } from '@/api/recommendation'

const loading = ref(false)
const history = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await listRecommendationHistory()
    history.value = res.data || res.rows || []
  } catch (e) {
    ElMessage.error('加载历史失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
```

- [ ] **Step 8: Create HistoryDetail.vue**

Write `user-ui/src/views/HistoryDetail.vue` — migrated from `ruoyi-ui/src/views/postgrad/app/historyDetail.vue`:

```vue
<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <el-button @click="$router.back()" style="margin-bottom:16px">← 返回</el-button>
      <el-card v-loading="loading">
        <div v-if="detail">
          <h4>推荐详情</h4>
          <p>预估分: {{ detail.estimatedScore || '-' }}</p>
          <p>目标地区: {{ detail.targetRegions || '不限' }}</p>
          <el-divider />
          <el-table v-if="detail.results" :data="detail.results" stripe>
            <el-table-column prop="schoolName" label="院校" />
            <el-table-column prop="programName" label="专业" />
            <el-table-column prop="matchScore" label="匹配度" width="100" />
          </el-table>
        </div>
        <el-empty v-else description="未找到推荐记录" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { getRecommendationHistoryDetail } from '@/api/recommendation'

const route = useRoute()
const loading = ref(false)
const detail = ref(null)

onMounted(async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getRecommendationHistoryDetail(id)
    detail.value = res.data || res
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
</style>
```

- [ ] **Step 9: Verify all core pages**

Start dev server, login, navigate through all pages:
- `/recommend` → form renders, enter score → generate → results table
- `/results` → table renders (may be empty if no prior generation)
- `/favorites` → list renders (may be empty)
- `/history` → timeline renders (may be empty)
- `/history/:id` → detail card renders

- [ ] **Step 10: Commit**

```bash
git add user-ui/src/api/recommendation.js user-ui/src/api/favorites.js \
  user-ui/src/api/programs.js user-ui/src/views/Recommend.vue \
  user-ui/src/views/Results.vue user-ui/src/views/Favorites.vue \
  user-ui/src/views/History.vue user-ui/src/views/HistoryDetail.vue
git commit -m "feat: add core pages — Recommend, Results, Favorites, History"
```

---

### Task 9: AI Pages — AiReport, AiHistory

**Files:**
- Create: `user-ui/src/api/ai.js`
- Create: `user-ui/src/views/AiReport.vue`
- Create: `user-ui/src/views/AiHistory.vue`

- [ ] **Step 1: Create api/ai.js**

Write `user-ui/src/api/ai.js`:

```js
import request from './request'

export function postAiStart(data) {
  return request({ url: '/app/ai-recommend/start', method: 'post', data })
}

export function postAiChat(data) {
  return request({ url: '/app/ai-recommend/chat', method: 'post', data })
}

export function postAiGenerateReport(data) {
  return request({ url: '/app/ai-recommend/generate-report', method: 'post', data })
}

export function getAiReport(id) {
  return request({ url: '/app/ai-recommend/report/' + id, method: 'get' })
}

export function getAiReports() {
  return request({ url: '/app/ai-recommend/reports', method: 'get' })
}

export function postAiResume(data) {
  return request({ url: '/app/ai-recommend/resume', method: 'post', data })
}
```

- [ ] **Step 2: Create AiReport.vue**

Migrate `ruoyi-ui/src/views/postgrad/app/ai-report.vue` (~500 lines, AI-generated recommendation report with markdown rendering and score charts).

Write `user-ui/src/views/AiReport.vue`:

```vue
<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <el-button @click="$router.back()" style="margin-bottom:16px">← 返回</el-button>
      <el-card v-loading="loading">
        <div v-if="report" class="report-content">
          <h2>{{ report.title || 'AI 推荐报告' }}</h2>
          <div class="report-meta" v-if="report.createdAt">
            生成时间: {{ report.createdAt }}
          </div>
          <el-divider />
          <div class="markdown-body" v-html="report.content || report.summary || '暂无内容'" />
        </div>
        <el-empty v-else-if="!loading" description="报告未找到" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import { getAiReport } from '@/api/ai'

const route = useRoute()
const loading = ref(false)
const report = ref(null)

onMounted(async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getAiReport(id)
    report.value = res.data || res
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.report-content h2 { margin-top: 0; }
.report-meta { color: #909399; font-size: 13px; margin-bottom: 8px; }
.markdown-body { line-height: 1.8; }
</style>
```

- [ ] **Step 3: Create AiHistory.vue**

Migrate `ruoyi-ui/src/views/postgrad/app/ai-history.vue`:

```vue
<template>
  <div class="app-page">
    <AppHeader />
    <div class="app-body">
      <div class="page-title"><h3>AI 推荐记录</h3></div>
      <el-card v-loading="loading">
        <el-empty v-if="!loading && reports.length === 0" description="暂无 AI 推荐记录" />
        <div v-else>
          <el-table :data="reports" stripe>
            <el-table-column prop="title" label="报告标题" />
            <el-table-column prop="createdAt" label="生成时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="$router.push('/ai-report/' + row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="total > 0"
            style="margin-top:16px;text-align:right"
            :current-page="pageNum" :page-size="pageSize" :total="total"
            layout="total, prev, pager, next" @current-change="fetchPage" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import { getAiReports } from '@/api/ai'

const loading = ref(false)
const reports = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

async function fetchReports(page) {
  loading.value = true
  try {
    const res = await getAiReports(page)
    reports.value = res.data?.rows || res.rows || res.data || []
    total.value = res.total || 0
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

function fetchPage(page) {
  pageNum.value = page
  fetchReports(page)
}

onMounted(() => fetchReports(1))
</script>

<style scoped>
.app-page { min-height: 100vh; background: #f0f2f5; }
.app-body { max-width: 800px; margin: 24px auto; padding: 0 16px; }
.page-title { margin-bottom: 16px; }
.page-title h3 { margin: 0; }
</style>
```

- [ ] **Step 4: Verify AI pages**

Start dev server, login, navigate:
- `/ai-history` → table renders with existing AI reports
- `/ai-report/:id` → report detail renders with markdown content

- [ ] **Step 5: Commit**

```bash
git add user-ui/src/api/ai.js user-ui/src/views/AiReport.vue user-ui/src/views/AiHistory.vue
git commit -m "feat: add AI pages — AiReport and AiHistory"
```

---

### Task 10: App Init — Restore User Session on Load

**Files:**
- Modify: `user-ui/src/main.js`
- Modify: `user-ui/src/App.vue`

- [ ] **Step 1: Update App.vue to restore session**

Rewrite `user-ui/src/App.vue`:

```vue
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
```

- [ ] **Step 2: Verify session restore**

- Login, close browser tab, open a new tab to http://localhost:8082/profile
- Expected: app restores session (calls `/me`), renders profile data
- Clear localStorage `App-Token`, refresh → redirect to `/login`

- [ ] **Step 3: Commit**

```bash
git add user-ui/src/App.vue
git commit -m "feat: restore user session on app init via /me"
```

---

### Task 11: Full Integration Verification

**Files:**
- None (read-only verification)

- [ ] **Step 1: Run acceptance checklist**

Run through each item:

1. `cd user-ui && npm install && npm run dev` — starts on port 8082
2. Login → `App-Token` written to localStorage
3. Open DevTools Network tab → authenticated requests carry `Authorization: Bearer <token>`
4. Clear localStorage, visit `/profile` → redirected to `/login`
5. Manual token expiry test: remove `app_login_tokens:<uuid>` from Redis → next request fails → auto-redirect to `/login`
6. Login, refresh page → `GET /app/auth/me` called, profile and user info restored
7. Start `ruoyi-ui` on port 8081 → admin login still works, all admin pages render
8. `user-ui:8082` and `ruoyi-ui:8081` both running → no port conflicts, both functional
9. Phase 5 haven't happened yet — `ruoyi-ui` app routes still present, everything works

- [ ] **Step 2: Fix any issues before proceeding to Phase 5**

---

### Task 12: Cleanup ruoyi-ui (Phase 5)

**Files:**
- Modify: `ruoyi-ui/src/router/index.js`
- Modify: `ruoyi-ui/src/permission.js`
- Delete: `ruoyi-ui/src/views/postgrad/app/` (directory)
- Delete: `ruoyi-ui/src/api/postgrad/appAuth.js`
- Delete: `ruoyi-ui/src/api/postgrad/appFavorites.js`
- Delete: `ruoyi-ui/src/api/postgrad/appProfile.js`
- Delete: `ruoyi-ui/src/api/postgrad/appPrograms.js`
- Delete: `ruoyi-ui/src/api/postgrad/appRecommendation.js`
- Delete: `ruoyi-ui/src/api/postgrad/ai.js`
- Delete: `ruoyi-ui/src/utils/appAuth.js`
- Delete: `ruoyi-ui/src/utils/appRequest.js`
- Delete: `ruoyi-ui/src/store/modules/appUser.js`

**WARNING: Only execute this task after all previous phases are verified and working.**

- [ ] **Step 1: Remove /app/* routes from constantRoutes**

Edit `ruoyi-ui/src/router/index.js`, remove lines 97-158 (the `/app/*` route blocks):

Remove everything from:
```
  // App端路由
```
through:
```
  }
```

(12 route definitions for `/app/*`)

- [ ] **Step 2: Remove app auth handling from permission.js**

Edit `ruoyi-ui/src/permission.js`:

Remove `import { getAppToken } from '@/utils/appAuth'` (line 7).

Remove the `/app/*` path check and call to `handleAppRoute()` (lines 22-28):

```js
  const isAppPath = to.path === '/app' || to.path.startsWith('/app/')

  if (isAppPath) {
    handleAppRoute(to, next)
    return
  }
```

Remove the entire `handleAppRoute` function (lines 82-103).

- [ ] **Step 3: Delete app-specific files**

```bash
rm -rf ruoyi-ui/src/views/postgrad/app/
rm ruoyi-ui/src/api/postgrad/appAuth.js
rm ruoyi-ui/src/api/postgrad/appFavorites.js
rm ruoyi-ui/src/api/postgrad/appProfile.js
rm ruoyi-ui/src/api/postgrad/appPrograms.js
rm ruoyi-ui/src/api/postgrad/appRecommendation.js
rm ruoyi-ui/src/api/postgrad/ai.js
rm ruoyi-ui/src/utils/appAuth.js
rm ruoyi-ui/src/utils/appRequest.js
rm ruoyi-ui/src/store/modules/appUser.js
```

- [ ] **Step 4: Verify ruoyi-ui still works**

```bash
cd ruoyi-ui && npm run dev
```

Expected: admin panel starts on port 8081. Login as admin works. System management, monitoring, tools — all admin functions intact. No `/app/*` routes remain.

- [ ] **Step 5: Final user-ui verification**

```bash
cd user-ui && npm run dev
```

Expected: all student-facing pages work. Login/register. Profile CRUD. Recommendations. Favorites. AI reports. History.

- [ ] **Step 6: Run both simultaneously**

Start both dev servers (8081, 8082) + backend (8080). All three must coexist without issues.

- [ ] **Step 7: Commit**

```bash
git add ruoyi-ui/src/router/index.js ruoyi-ui/src/permission.js
git commit -m "refactor: remove app routes and auth from ruoyi-ui admin panel"
```

---

## Acceptance Checklist (from spec)

1. **Phase 0:** All 10 contract-check steps pass
2. `user-ui` can start independently with `npm install && npm run dev`
3. Login writes `App-Token` to localStorage
4. All authenticated requests carry `Authorization: Bearer <token>` header
5. Accessing `/profile` without token redirects to `/login`
6. Expired token (HTTP 401 or code=401) clears localStorage and redirects to `/login`
7. `GET /app/auth/me` restores user info and profile on app init
8. `ruoyi-ui` admin panel is unaffected
9. `user-ui` (8082) and `ruoyi-ui` (8081) can run simultaneously
10. After cleanup, `ruoyi-ui` admin functions still work correctly
