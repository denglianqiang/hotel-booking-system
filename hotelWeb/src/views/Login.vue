<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clientApi } from '@/api'
import { phoneRules } from '@/utils/validators'
import { useUserStore, homeOfRole } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ name: '', phone: '', password: '', confirmPassword: '' })

const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: phoneRules({ required: true, requiredMessage: '请输入手机号', message: '手机号格式不正确' }),
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需在 6 到 20 位之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// 登录：登录态由 store 统一保存，按角色/权限跳转到默认页面
async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const data = await userStore.login({ ...loginForm })
      // 兼容接口返回 200 但 data 为空的情况，避免后续取值报错
      if (!data) {
        ElMessage.error('登录失败：服务端未返回登录信息')
        return
      }
      const home = homeOfRole(data.role, data.permissions)
      // 账号没有任何可用页面：明确提示，不要跳到登录页（同路径跳转不会有任何反应）
      if (home === '/login') {
        ElMessage.error('该账号未分配任何可访问的页面权限，请联系运营方')
        return
      }
      ElMessage.success('登录成功')
      router.push(home)
    } catch (e) {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
  })
}

async function handleRegister() {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await clientApi.register({
        name: registerForm.name,
        phone: registerForm.phone,
        password: registerForm.password,
      })
      ElMessage.success('注册成功，请登录')
      activeTab.value = 'login'
      loginForm.username = registerForm.phone
      loginForm.password = ''
    } catch (e) {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-title">酒店客房预订管理系统</div>
      <div class="login-subtitle">Hotel Booking Management System</div>

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="账号登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" size="large" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="账号 / 用户手机号" :prefix-icon="'User'" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                show-password
                :prefix-icon="'Lock'"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
                登 录
              </el-button>
            </el-form-item>
            <div class="login-tip">管理员与运营方使用工作账号，普通用户使用手机号登录</div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="普通用户注册" name="register">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" size="large" @keyup.enter="handleRegister">
            <el-form-item prop="name">
              <el-input v-model="registerForm.name" placeholder="姓名" :prefix-icon="'User'" />
            </el-form-item>
            <el-form-item prop="phone">
              <el-input v-model="registerForm.phone" placeholder="手机号" :prefix-icon="'Iphone'" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="密码（6-20位）" show-password :prefix-icon="'Lock'" />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" show-password :prefix-icon="'Lock'" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" :loading="loading" @click="handleRegister">注 册</el-button>
            </el-form-item>
            <div class="login-tip">注册即成为普通用户，登录后可在线预订客房</div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a5f 0%, #2c5f8a 50%, #3a7ca5 100%);
}

.login-card {
  width: 420px;
  padding: 10px 10px 0;
}

.login-title {
  font-size: 22px;
  font-weight: bold;
  color: #303133;
  text-align: center;
  margin-bottom: 6px;
}

.login-subtitle {
  font-size: 12px;
  color: #909399;
  text-align: center;
  margin-bottom: 20px;
  letter-spacing: 1px;
}

.login-btn {
  width: 100%;
}

.login-tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
  margin-bottom: 16px;
}
</style>
