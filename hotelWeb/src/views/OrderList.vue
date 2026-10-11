<script setup>
import { ref, onMounted } from 'vue'
import { orderApi } from '@/api'
import { formatMoney } from '@/utils/format'
import { useConfirmAction } from '@/composables/useConfirmAction'
import {
  ORDER_STATUS_OPTIONS,
  orderStatusText,
  orderStatusTagType,
  formatDateTime,
} from '@/constants/orderStatus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = ref({
  current: 1,
  size: 10,
  orderNo: '',
  customerName: '',
  status: null,
})

// 行级操作状态：正在执行的行按钮转圈，其它行按钮禁用
const { acting, isActing, run } = useConfirmAction()

// 操作记录抽屉
const logVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])
const logOrderNo = ref('')
const logPayDeadline = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await orderApi.getList(query.value)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

// 回到第一页并刷新：页码本来就已在第 1 页时 v-model 不会变化、也就不会触发
// @current-change，因此需要手动刷新；否则交给 current-change 触发，避免重复请求
function reloadFromFirstPage() {
  if (query.value.current === 1) {
    loadData()
  } else {
    query.value.current = 1
  }
}

function handleSearch() {
  reloadFromFirstPage()
}

function handleReset() {
  query.value = { current: 1, size: 10, orderNo: '', customerName: '', status: null }
  loadData()
}

// 取消结果提示：有退款时附带退款金额（退款金额由后端计算）
function cancelTip(res) {
  const refund = Number(res?.data?.refundAmount || 0)
  return refund > 0 ? `取消成功，退款 ¥${refund.toFixed(2)}` : '取消成功'
}

function handlePay(row) {
  run({
    row: row.id,
    message: `确认为订单「${row.orderNo}」支付？`,
    title: '确认支付',
    action: () => orderApi.pay(row.id),
    success: '支付成功',
    onDone: loadData,
  })
}

function handleCheckIn(row) {
  run({
    row: row.id,
    message: `确认为订单「${row.orderNo}」办理入住？`,
    title: '确认入住',
    action: () => orderApi.checkIn(row.id),
    success: '入住成功',
    onDone: loadData,
  })
}

function handleCheckOut(row) {
  run({
    row: row.id,
    message: `确认为订单「${row.orderNo}」办理退房？`,
    title: '确认退房',
    action: () => orderApi.checkOut(row.id),
    success: '退房成功',
    onDone: loadData,
  })
}

// 取消订单：已支付订单涉及退款，需填写可选取消原因；待支付订单沿用原确认框
function handleCancel(row) {
  if (row.status === 1) {
    run({
      row: row.id,
      message: `确认取消订单「${row.orderNo}」并退款？`,
      title: '取消/退款',
      prompt: {
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
        inputType: 'textarea',
        inputPlaceholder: '取消原因（选填）',
      },
      action: (reason) => orderApi.cancel(row.id, reason),
      success: cancelTip,
      onDone: loadData,
    })
  } else {
    run({
      row: row.id,
      message: `确认取消订单「${row.orderNo}」？`,
      title: '确认取消',
      action: () => orderApi.cancel(row.id),
      success: cancelTip,
      onDone: loadData,
    })
  }
}

// 查看操作记录
async function handleLog(row) {
  logOrderNo.value = row.orderNo
  logPayDeadline.value = row.status === 0 ? formatDateTime(row.payDeadline) : ''
  logList.value = []
  logVisible.value = true
  logLoading.value = true
  try {
    const res = await orderApi.getLog(row.id)
    logList.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    logLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600;">订单列表</span>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" :model="query" style="margin-bottom: 16px;">
        <el-form-item label="订单号">
          <el-input v-model="query.orderNo" placeholder="搜索订单号" style="width: 180px;" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户姓名">
          <el-input v-model="query.customerName" placeholder="搜索客户姓名" style="width: 180px;" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" style="width: 120px;" clearable>
            <el-option v-for="s in ORDER_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border style="width: 100%;">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="customerName" label="客户姓名" width="100" />
        <el-table-column prop="roomNumber" label="房间号" width="100" />
        <el-table-column prop="checkInDate" label="入住日期" width="120" />
        <el-table-column prop="checkOutDate" label="退房日期" width="120" />
        <el-table-column prop="totalPrice" label="总价" width="100">
          <template #default="{ row }">{{ formatMoney(row.totalPrice) }}</template>
        </el-table-column>
        <el-table-column prop="refundAmount" label="退款金额" width="110">
          <template #default="{ row }">
            <span v-if="Number(row.refundAmount) > 0" style="color: #f56c6c;">
              {{ formatMoney(Number(row.refundAmount).toFixed(2)) }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusTagType(row.status)" size="small">{{ orderStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="success"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handlePay(row)"
            >支付</el-button>
            <el-button
              v-if="row.status === 1"
              type="primary"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleCheckIn(row)"
            >办理入住</el-button>
            <el-button
              v-if="row.status === 2"
              type="danger"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleCheckOut(row)"
            >办理退房</el-button>
            <el-button
              v-if="row.status === 0"
              type="info"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleCancel(row)"
            >取消</el-button>
            <el-button
              v-if="row.status === 1"
              type="warning"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleCancel(row)"
            >取消/退款</el-button>
            <el-button size="small" @click="handleLog(row)">操作记录</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无订单数据" />
        </template>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="query.current"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end;"
        @current-change="loadData"
      />
    </el-card>

    <!-- 操作记录抽屉 -->
    <el-drawer v-model="logVisible" size="440px">
      <template #header>
        <span style="font-weight: 600;">操作记录 · {{ logOrderNo }}</span>
      </template>
      <div v-loading="logLoading" style="min-height: 120px;">
        <el-alert
          v-if="logPayDeadline"
          type="warning"
          :closable="false"
          show-icon
          :title="`支付截止时间：${logPayDeadline}`"
          style="margin-bottom: 12px;"
        />
        <el-empty v-if="!logLoading && logList.length === 0" description="暂无操作记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="log in logList"
            :key="log.id"
            :timestamp="formatDateTime(log.createTime)"
            placement="top"
          >
            <div style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;">
              <template v-if="log.fromStatus !== null && log.fromStatus !== undefined">
                <el-tag :type="orderStatusTagType(log.fromStatus)" size="small">{{ orderStatusText(log.fromStatus) }}</el-tag>
                <span style="color: #909399;">→</span>
              </template>
              <el-tag :type="orderStatusTagType(log.toStatus)" size="small">{{ orderStatusText(log.toStatus) }}</el-tag>
            </div>
            <div style="margin-top: 4px; font-size: 13px; color: #606266;">
              操作人：{{ log.operator || '—' }}
            </div>
            <div v-if="log.remark" style="margin-top: 2px; font-size: 13px; color: #909399;">
              {{ log.remark }}
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped></style>
