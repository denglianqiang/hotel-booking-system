import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
// 登录态统一由 Pinia 管理（默认落地路径的计算也放在 store 里）
import { useUserStore, homeOfRole } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ===== 统一登录页（所有角色共用） =====
    {
      path: '/login',
      name: 'login',
      meta: { title: '登录' },
      component: () => import('@/views/Login.vue'),
    },
    // ===== 管理端（按 permissions 权限码控制） =====
    {
      path: '/',
      redirect: '/rooms/status',
    },
    {
      path: '/rooms',
      name: 'rooms',
      meta: { title: '房间管理', area: 'admin', permission: 'rooms' },
      component: () => import('@/views/RoomList.vue'),
    },
    {
      path: '/rooms/status',
      name: 'roomStatus',
      meta: { title: '房态图', area: 'admin', permission: 'roomStatus' },
      component: () => import('@/views/RoomStatus.vue'),
    },
    {
      path: '/orders',
      name: 'orders',
      meta: { title: '订单管理', area: 'admin', permission: 'orders' },
      component: () => import('@/views/OrderList.vue'),
    },
    {
      path: '/checkins',
      name: 'checkins',
      meta: { title: '入住登记', area: 'admin', permission: 'checkins' },
      component: () => import('@/views/CheckInList.vue'),
    },
    {
      path: '/customers',
      name: 'customers',
      meta: { title: '普通用户管理', area: 'admin', permission: 'customers' },
      component: () => import('@/views/CustomerList.vue'),
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      meta: { title: '统计看板', area: 'admin', permission: 'dashboard' },
      component: () => import('@/views/Dashboard.vue'),
    },
    {
      path: '/users',
      name: 'users',
      meta: { title: '账号管理', area: 'admin', permission: 'users' },
      component: () => import('@/views/UserList.vue'),
    },

    // ===== 普通用户端（仅 customer 角色） =====
    // 注意：这里刻意用 /member 而不是 /client。
    // 后端普通用户接口就是 /client/**（/client/login、/client/orders ...），
    // 而打包后前端页面与接口同源；若页面路由也叫 /client/orders，
    // 刷新该页面时请求会先被后端的登录拦截器按 /client/** 拦成 401，页面根本加载不出来。
    {
      path: '/member',
      redirect: '/member/home',
    },
    {
      path: '/member/home',
      name: 'clientHome',
      meta: { title: '酒店预订', area: 'client' },
      component: () => import('@/views/client/ClientHome.vue'),
    },
    {
      path: '/member/orders',
      name: 'clientOrders',
      meta: { title: '我的订单', area: 'client' },
      component: () => import('@/views/client/ClientOrders.vue'),
    },
    {
      path: '/member/profile',
      name: 'clientProfile',
      meta: { title: '个人中心', area: 'client' },
      component: () => import('@/views/client/ClientProfile.vue'),
    },
  ],
})

// 全局前置守卫：统一登录页 + 按角色/权限控制访问
router.beforeEach(async (to) => {
  // 注意：Pinia 已在 main.js 注册，但守卫可能在应用挂载前执行，store 必须在守卫内部获取
  const store = useUserStore()
  const role = store.role
  const permissions = store.permissions

  // 登录页：已登录则按角色回默认首页
  if (to.path === '/login') {
    if (!store.isLoggedIn) return true
    const home = homeOfRole(role, permissions)
    // 账号没有任何可用页面：清掉登录态并留在登录页。
    // 不能返回 '/login'（与当前目标同一路径），否则会触发路由的无限重定向检测。
    if (home === '/login') {
      store.clearLoginState()
      return true
    }
    return home
  }

  // 未登录一律回登录页
  if (!store.isLoggedIn) {
    return '/login'
  }

  // 应用启动后只验活一次，避免每次跳转都请求服务端
  if (!store.sessionValidated) {
    const valid = await store.validateSession()
    if (!valid) return '/login'
  }

  // 客人端仅 customer 角色可访问
  if (to.meta.area === 'client' || to.path.startsWith('/member')) {
    if (role === 'customer') return true
    const home = homeOfRole(role, permissions)
    // homeOfRole 无可用页面时返回登录页，此时不再提示普通用户功能警告
    if (home !== '/login') {
      ElMessage.warning('该功能请使用普通用户账号登录')
      return home
    }
    return '/login'
  }

  // 管理端按权限码校验
  const permission = to.meta.permission
  if (permission && !store.hasPermission(permission)) {
    const home = homeOfRole(role, permissions)
    if (home !== '/login' && home !== to.path) {
      ElMessage.warning('暂无该页面的访问权限')
      return home
    }
    // 没有任何可访问页面：清理登录态回登录页
    ElMessage.error('当前账号未分配任何权限，请联系运营方')
    store.clearLoginState()
    return '/login'
  }
  return true
})

export default router