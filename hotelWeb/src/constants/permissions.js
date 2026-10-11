/**
 * 管理端页面（权限码）唯一来源
 *
 * 登录后的默认落地页、侧边栏菜单、用户管理里的“可访问页面”勾选项都从这里派生。
 * 新增一个页面只需在此处加一条，避免多处手写清单互相漂移。
 *
 * 注意：icon 存的是 @element-plus/icons-vue 的组件名字符串，
 * 本模块本身不引入任何组件；字符串到组件的解析统一由 menuItemsOfPermissions 完成。
 */
import * as ICONS from '@element-plus/icons-vue'

// 顺序即"登录后默认落地页"的优先级顺序。
// 统计看板放最前：运营方（看板+账号治理视角）登录后直接落在看板；
// admin 不含 dashboard 权限，落地不受影响（取第一个有权限的页面，仍是房态图）。
export const ADMIN_PAGES = [
  { code: 'dashboard', label: '统计看板', path: '/dashboard', icon: 'DataAnalysis' },
  { code: 'roomStatus', label: '房态图', path: '/rooms/status', icon: 'Grid' },
  { code: 'rooms', label: '房间管理', path: '/rooms', icon: 'House' },
  { code: 'orders', label: '订单管理', path: '/orders', icon: 'Document' },
  { code: 'checkins', label: '入住登记', path: '/checkins', icon: 'Tickets' },
  { code: 'customers', label: '普通用户管理', path: '/customers', icon: 'User' },
  { code: 'users', label: '账号管理', path: '/users', icon: 'Setting' },
]

export const PERMISSION_CODES = ADMIN_PAGES.map((p) => p.code)

// 权限码 -> 页面定义（找不到返回 undefined）
export function pageOfPermission(code) {
  return ADMIN_PAGES.find((p) => p.code === code)
}

// 权限码列表 -> 菜单项（含解析后的图标组件），供 Layout 使用
export function menuItemsOfPermissions(permissions) {
  const list = normalizePermissions(permissions)
  return ADMIN_PAGES.filter((p) => list.includes(p.code)).map((p) => ({
    index: p.path,
    title: p.label,
    // 图标名写错时兜底为 Grid，避免出现空白图标
    icon: ICONS[p.icon] || ICONS.Grid,
    permission: p.code,
  }))
}

// 权限码统一成数组（接口与 localStorage 里存的都可能是逗号分隔字符串）
function normalizePermissions(permissions) {
  if (Array.isArray(permissions)) return permissions.filter(Boolean)
  return String(permissions || '')
    .split(',')
    .filter(Boolean)
}
