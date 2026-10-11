/**
 * 订单状态共享字典
 *
 * 订单状态机（后端为准）：
 * 0 待支付 ──支付──> 1 已支付 ──入住──> 2 已入住 ──退房──> 3 已退房
 *    ├──取消──> 4 已取消        └──取消（退款）──> 4 已取消
 *    └──超时未支付自动取消──> 4 已取消
 *
 * 退款金额由后端计算，前端只负责展示，不在前端复算退款规则。
 */

// 订单状态选项（订单管理 / 我的订单共用）
export const ORDER_STATUS_OPTIONS = [
  { value: 0, label: '待支付', type: 'info' },
  { value: 1, label: '已支付', type: 'warning' },
  { value: 2, label: '已入住', type: 'danger' },
  { value: 3, label: '已退房', type: 'success' },
  // 已取消使用 info：空字符串会被 Element Plus 解析为 primary（蓝色）
  { value: 4, label: '已取消', type: 'info' },
]

// 订单状态中文文案（未知状态返回空串，避免模板出现 undefined）
export function orderStatusText(status) {
  const item = ORDER_STATUS_OPTIONS.find((s) => s.value === status)
  return item ? item.label : ''
}

// 订单状态标签类型（未知状态回退 info）
export function orderStatusTagType(status) {
  const item = ORDER_STATUS_OPTIONS.find((s) => s.value === status)
  return item ? item.type : 'info'
}

/**
 * 格式化后端返回的 ISO-8601 时间：2026-10-04T22:10:00 -> 2026-10-04 22:10:00
 * 空值返回空串
 */
export function formatDateTime(value) {
  if (value === null || value === undefined || value === '') return ''
  return String(value).replace('T', ' ')
}
