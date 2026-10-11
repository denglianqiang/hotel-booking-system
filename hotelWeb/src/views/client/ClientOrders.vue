<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { clientApi } from '@/api'
import { formatMoney } from '@/utils/format'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { orderStatusText, orderStatusTagType, formatDateTime } from '@/constants/orderStatus'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])

// 行级操作状态：正在执行的行按钮转圈，其它行按钮禁用
const { acting, isActing, run } = useConfirmAction()

async function loadOrders() {
  loading.value = true
  try {
    const res = await clientApi.getOrders()
    tableData.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

// 取消结果提示：有退款时附带退款金额（退款金额由后端计算）
function cancelTip(res) {
  const refund = Number(res?.data?.refundAmount || 0)
  return refund > 0 ? `取消成功，退款 ¥${refund.toFixed(2)}` : '取消成功'
}

function handlePay(row) {
  run({
    row: row.id,
    message: `确认为订单「${row.orderNo}」支付 ${formatMoney(row.totalPrice)}？`,
    title: '确认支付',
    action: () => clientApi.pay(row.id),
    success: '支付成功，请于入住日到前台办理入住',
    onDone: loadOrders,
  })
}

// 待支付订单取消：沿用原确认框
function handleCancel(row) {
  run({
    row: row.id,
    message: `确认取消订单「${row.orderNo}」？`,
    title: '确认取消',
    action: () => clientApi.cancel(row.id),
    success: cancelTip,
    onDone: loadOrders,
  })
}

// 已支付订单取消：涉及退款，收集可选取消原因
function handleCancelPaid(row) {
  run({
    row: row.id,
    message: `确认取消预订「${row.orderNo}」并退款？`,
    title: '取消预订',
    prompt: {
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想',
      inputType: 'textarea',
      inputPlaceholder: '取消原因（选填）',
    },
    action: (reason) => clientApi.cancel(row.id, reason),
    success: cancelTip,
    onDone: loadOrders,
  })
}

onMounted(() => {
  loadOrders()
})
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-weight: 600;">我的订单</span>
          <el-button type="primary" size="small" @click="router.push('/member/home')">去预订</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border style="width: 100%;">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="roomNumber" label="房间号" width="90" />
        <el-table-column prop="checkInDate" label="入住日期" width="120" />
        <el-table-column prop="checkOutDate" label="退房日期" width="120" />
        <el-table-column prop="totalPrice" label="总价" width="100">
          <template #default="{ row }">{{ formatMoney(row.totalPrice) }}</template>
        </el-table-column>
        <el-table-column prop="refundAmount" label="退款金额" width="110">
          <template #default="{ row }">
            <span v-if="Number(row.refundAmount) > 0" style="color: #67c23a;">
              {{ formatMoney(Number(row.refundAmount).toFixed(2)) }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="orderStatusTagType(row.status)" size="small">{{ orderStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button
                type="success"
                size="small"
                :loading="isActing(row.id)"
                :disabled="acting !== null && !isActing(row.id)"
                @click="handlePay(row)"
              >支付</el-button>
              <el-button
                type="info"
                size="small"
                :loading="isActing(row.id)"
                :disabled="acting !== null && !isActing(row.id)"
                @click="handleCancel(row)"
              >取消</el-button>
            </template>
            <template v-else-if="row.status === 1">
              <el-button
                type="warning"
                size="small"
                :loading="isActing(row.id)"
                :disabled="acting !== null && !isActing(row.id)"
                @click="handleCancelPaid(row)"
              >取消预订</el-button>
              <span style="font-size: 12px; color: #e6a23c; margin-left: 8px;">
                待前台办理入住
              </span>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有订单，去挑一间喜欢的房间吧">
            <el-button type="primary" @click="router.push('/member/home')">浏览客房</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped></style>
