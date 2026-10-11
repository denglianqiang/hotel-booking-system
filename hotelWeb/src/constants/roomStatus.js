/**
 * 房间状态共享字典
 *
 * 房态模型（后端已调整）：
 * 1. room.status 只表示「是否可售」，仅两个值：0 正常销售 / 3 停用维修；
 *    1（已订）、2（入住）这两个值已废弃，前端不得再使用。
 * 2. 占用情况由后端按日期区间从订单推导，返回 room.availability：
 *    available 可订 / booked 已订 / occupied 在住 / repair 维修。
 *    优先级：repair > occupied（占用单为已入住）> booked > available。
 */

// 可售状态选项（房间管理表单 / 筛选使用）
export const ROOM_STATUS_OPTIONS = [
  { value: 0, label: '正常销售', type: 'success' },
  { value: 3, label: '停用维修', type: 'info' },
]

// 房间可售状态中文文案
export function roomStatusText(status) {
  const item = ROOM_STATUS_OPTIONS.find((s) => s.value === status)
  return item ? item.label : ''
}

// 房间可售状态标签类型
export function roomStatusTagType(status) {
  const item = ROOM_STATUS_OPTIONS.find((s) => s.value === status)
  return item ? item.type : 'info'
}

// 派生占用状态字典（颜色沿用原房态图配色，保持不变）
export const AVAILABILITY_MAP = {
  available: {
    label: '可订',
    color: '#67C23A',
    bg: '#f0f9eb',
    borderColor: '#b3e19d',
    tagType: 'success',
  },
  booked: {
    label: '已订',
    color: '#E6A23C',
    bg: '#fdf6ec',
    borderColor: '#f3d19e',
    tagType: 'warning',
  },
  occupied: {
    label: '在住',
    color: '#F56C6C',
    bg: '#fef0f0',
    borderColor: '#fab6b6',
    tagType: 'danger',
  },
  repair: {
    label: '维修',
    color: '#909399',
    bg: '#f4f4f5',
    borderColor: '#d3d4d6',
    tagType: 'info',
  },
}

/**
 * 安全取房间的派生状态字典项
 * availability 缺失或为未知值时回退到「维修」项，保证模板取值不会抛错
 */
export function availabilityOf(room) {
  const key = room && room.availability
  return AVAILABILITY_MAP[key] || AVAILABILITY_MAP.repair
}
