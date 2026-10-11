import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { userApi, clientApi } from '@/api'
// 管理端页面清单（含顺序）统一由 constants/permissions 提供，避免多处维护同一份映射
import { ADMIN_PAGES } from '@/constants/permissions'

// 权限码统一成数组（接口与 localStorage 里存的都是逗号分隔字符串）
function toPermissionList(permissions) {
  if (Array.isArray(permissions)) return permissions.filter(Boolean)
  return String(permissions || '')
    .split(',')
    .filter(Boolean)
}

// 按角色计算登录后的默认落地路径：普通用户进预订端，管理端进第一个有权限的页面（顺序即 ADMIN_PAGES 顺序），都没有则回登录页
export function homeOfRole(role, permissions) {
  if (role === 'customer') {
    return '/member/home'
  }
  const list = toPermissionList(permissions)
  const hit = ADMIN_PAGES.find((p) => list.includes(p.code))
  return hit ? hit.path : '/login'
}

export const useUserStore = defineStore('user', () => {
  // ===== 状态（初始化自 localStorage，保证刷新后登录态不丢） =====
  const token = ref(localStorage.getItem('token') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')
  const role = ref(localStorage.getItem('role') || '')
  const permissions = ref(toPermissionList(localStorage.getItem('permissions')))
  // 是否已向服务端验活：登录即视为有效，清理登录态后需要重新验活
  const sessionValidated = ref(false)

  // ===== getters =====
  const isLoggedIn = computed(() => !!token.value)
  const isCustomer = computed(() => role.value === 'customer')
  const isOperator = computed(() => role.value === 'operator')

  function hasPermission(code) {
    return permissions.value.includes(code)
  }

  // ===== 内部：状态写入后同步 localStorage（request.js 仍直接读 token） =====
  function persist() {
    localStorage.setItem('token', token.value || '')
    localStorage.setItem('nickname', nickname.value || '')
    localStorage.setItem('role', role.value || '')
    localStorage.setItem('permissions', permissions.value.join(','))
  }

  // 保存登录接口返回的登录态
  function setLoginState(data) {
    const info = data || {}
    token.value = info.token || ''
    nickname.value = info.nickname || ''
    role.value = info.role || ''
    permissions.value = toPermissionList(info.permissions)
    persist()
    // 刚登录，无需再向服务端验活
    sessionValidated.value = true
  }

  // 更新昵称（个人中心改名后调用，头部显示与 localStorage 一并刷新）
  function setNickname(name) {
    nickname.value = name || ''
    persist()
  }

  // 更新昵称（个人中心改名后调用，顶部显示与 localStorage 一并刷新）
  function setNickname(name) {
    nickname.value = name || ''
    persist()
  }

  // 清理登录态（本地）
  function clearLoginState() {
    token.value = ''
    nickname.value = ''
    role.value = ''
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('nickname')
    localStorage.removeItem('role')
    localStorage.removeItem('permissions')
    sessionValidated.value = false
  }

  // 登录：成功后写入登录态并返回登录接口的 data，失败向上抛
  async function login(credentials) {
    const res = await userApi.login(credentials)
    const data = res?.data
    setLoginState(data)
    return data
  }

  // 退出登录：尽力调用对应端的注销接口，失败不影响本地清理
  async function logout() {
    try {
      if (role.value === 'customer') {
        await clientApi.logout()
      } else {
        await userApi.logout()
      }
    } catch (e) {
      // 后端注销失败也继续清理本地登录态
    }
    clearLoginState()
  }

  // 向服务端验活登录态；失效时清理本地登录态
  async function validateSession() {
    // 已验活过（或刚登录）就不重复请求
    if (sessionValidated.value) return true
    if (!token.value) {
      clearLoginState()
      return false
    }
    try {
      // 普通用户走客人端接口，管理端账号走统一登录接口
      const res = role.value === 'customer' ? await clientApi.info() : await userApi.info()
      if (!res || res.code !== 200) {
        clearLoginState()
        return false
      }
      const data = res.data || {}
      // 刷新昵称：普通用户接口返回 name，管理端返回 nickname / username
      nickname.value = role.value === 'customer' ? data.name || '' : data.nickname || data.username || ''
      persist()
      sessionValidated.value = true
      return true
    } catch (e) {
      clearLoginState()
      return false
    }
  }

  return {
    // 状态
    token,
    nickname,
    role,
    permissions,
    sessionValidated,
    // getters
    isLoggedIn,
    isCustomer,
    isOperator,
    hasPermission,
    // actions
    setLoginState,
    setNickname,
    clearLoginState,
    login,
    logout,
    validateSession,
  }
})
