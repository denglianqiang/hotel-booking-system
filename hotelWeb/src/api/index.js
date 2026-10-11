import request from '@/utils/request'

// 房间相关 API
export const roomApi = {
  // 分页查询房间列表
  getList(params) {
    return request.post('/room/list', params)
  },
  // 获取房态图数据（可选 checkInDate / checkOutDate，格式 YYYY-MM-DD，缺省为今天→明天）
  getStatus(params) {
    return request.get('/room/status', { params })
  },
  // 新增房间
  save(data) {
    return request.post('/room/save', data)
  },
  // 编辑房间
  update(data) {
    return request.put('/room/update', data)
  },
  // 删除房间
  delete(id) {
    return request.delete(`/room/delete/${id}`)
  },
  // 手动设置房间状态（0空闲 / 3维修）
  setStatus(id, status) {
    return request.post('/room/set-status', { id, status })
  },
}

// 订单相关 API
export const orderApi = {
  // 分页查询订单列表
  getList(params) {
    return request.post('/order/list', params)
  },
  // 创建预订订单
  create(data) {
    return request.post('/order/create', data)
  },
  // 办理入住
  checkIn(orderId) {
    return request.post('/order/checkin', { orderId })
  },
  // 办理退房
  checkOut(orderId) {
    return request.post('/order/checkout', { orderId })
  },
  // 支付订单（返回支付后的订单，含 paidTime / payDeadline）
  pay(orderId) {
    return request.post('/order/pay', { orderId })
  },
  // 取消订单（reason 可选；返回取消后的订单，refundAmount 为退款金额）
  cancel(orderId, reason) {
    return request.post('/order/cancel', { orderId, reason })
  },
  // 查询订单操作记录（按下单时间倒序）
  getLog(orderId) {
    return request.get('/order/log/' + orderId)
  },
}

// 客户相关 API
export const customerApi = {
  // 分页查询客户列表
  getList(params) {
    return request.post('/customer/list', params)
  },
  // 获取全部客户（预订下拉选择用）
  getAll() {
    return request.get('/customer/all')
  },
  // 新增客户
  save(data) {
    return request.post('/customer/save', data)
  },
  // 编辑客户
  update(data) {
    return request.put('/customer/update', data)
  },
  // 重置普通用户登录密码（前台代客开通或重置）
  resetPassword(id, password) {
    return request.put('/customer/reset-password', { id, password })
  },
  // 删除客户
  delete(id) {
    return request.delete(`/customer/delete/${id}`)
  },
}

// 统计相关 API
export const statisticsApi = {
  // 获取看板数据
  getDashboard() {
    return request.get('/statistics/dashboard')
  },
}

// 入住登记相关 API
export const checkInApi = {
  // 入住登记记录（分页）
  getList(params) {
    return request.get('/checkin/list', { params })
  },
  // 在住客人列表
  getInHouse() {
    return request.get('/checkin/inhouse')
  },
}

// 用户（统一登录 / 运营方用户管理）相关 API
export const userApi = {
  // 统一登录（管理端账号与普通用户手机号共用）
  login(data) {
    return request.post('/user/login', data)
  },
  // 获取当前登录用户信息
  getInfo() {
    return request.get('/user/info')
  },
  // 退出登录
  logout() {
    return request.post('/user/logout')
  },
  // 用户列表（运营方）
  getList() {
    return request.get('/user/list')
  },
  // 新增账号（运营方）：roleIds 指定账号持有的角色
  save(data) {
    return request.post('/user/save', data)
  },
  // 编辑账号（运营方）：roleIds 提供即整体替换
  update(data) {
    return request.put('/user/update', data)
  },
  // 删除账号（运营方）
  delete(id) {
    return request.delete(`/user/delete/${id}`)
  },
}

// 角色与权限管理（RBAC）相关 API
export const roleApi = {
  // 角色列表（含每个角色拥有的权限码）
  getList() {
    return request.get('/role/list')
  },
  // 全部权限码清单（角色编辑对话框的勾选项）
  getPermissions() {
    return request.get('/role/permissions')
  },
  // 新增角色
  save(data) {
    return request.post('/role/save', data)
  },
  // 编辑角色（名称/说明/权限集合，编码不可改）
  update(data) {
    return request.put('/role/update', data)
  },
  // 删除角色（内置角色或仍被账号持有的角色会被拒绝）
  delete(id) {
    return request.delete(`/role/delete/${id}`)
  },
}

// 客人端（自助预订）相关 API
export const clientApi = {
  // 普通用户登录
  login(data) {
    return request.post('/client/login', data)
  },
  // 普通用户注销（普通用户 token 与管理员 token 分开存放，必须走客人端接口）
  logout() {
    return request.post('/client/logout')
  },
  // 当前用户信息（验活用）
  info() {
    return request.get('/client/info')
  },
  // 修改个人资料（姓名），返回更新后的个人信息
  updateProfile(data) {
    return request.put('/client/profile', data)
  },
  // 修改密码（需原密码校验）
  changePassword(data) {
    return request.put('/client/password', data)
  },
  // 修改个人资料（姓名），返回更新后的普通用户信息
  updateProfile(data) {
    return request.put('/client/profile', data)
  },
  // 修改密码（需原密码校验）
  changePassword(data) {
    return request.put('/client/password', data)
  },
  // 可预订房间列表（可选 checkInDate / checkOutDate，格式 YYYY-MM-DD）
  getRooms(params) {
    return request.get('/client/rooms', { params })
  },
  // 下单预订
  book(data) {
    return request.post('/client/order', data)
  },
  // 我的订单列表
  getOrders() {
    return request.get('/client/orders')
  },
  // 支付自己的订单
  pay(orderId) {
    return request.post('/client/order/pay', { orderId })
  },
  // 取消自己的订单（reason 可选；返回取消后的订单，refundAmount 为退款金额）
  cancel(orderId, reason) {
    return request.post('/client/order/cancel', { orderId, reason })
  },
}
