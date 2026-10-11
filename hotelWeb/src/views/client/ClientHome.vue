<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clientApi } from '@/api'
import { formatMoney } from '@/utils/format'
import { availabilityOf } from '@/constants/roomStatus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const rooms = ref([])

// 本地日期格式化为 YYYY-MM-DD（避免使用 toISOString 造成时区偏差）
function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// 选中的日期区间：默认今天 → 明天
const today = new Date()
const tomorrow = new Date(today.getTime() + 86400000)
const dateRange = ref([formatDate(today), formatDate(tomorrow)])
const checkInDate = computed(() => dateRange.value?.[0] || '')
const checkOutDate = computed(() => dateRange.value?.[1] || '')

// 禁用今天之前的日期
function disabledDate(date) {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  return date.getTime() < start.getTime()
}

// 日期变更后按新区间重新查询可订房态
function handleDateChange() {
  if (!checkInDate.value || !checkOutDate.value) return
  loadRooms()
}

// 统计数据：按后端推导出的占用状态（availability）计数
const stats = computed(() => {
  const result = { total: rooms.value.length, available: 0, booked: 0, occupied: 0 }
  rooms.value.forEach((room) => {
    if (room.availability === 'available') result.available++
    else if (room.availability === 'booked') result.booked++
    else if (room.availability === 'occupied') result.occupied++
  })
  return result
})

async function loadRooms() {
  // 请求进行中直接忽略，避免重复点击发起并发请求
  if (loading.value) return
  loading.value = true
  try {
    const res = await clientApi.getRooms({
      checkInDate: checkInDate.value,
      checkOutDate: checkOutDate.value,
    })
    rooms.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

// 预订对话框
const bookingVisible = ref(false)
const selectedRoom = ref(null)
const submitting = ref(false)
const bookingFormRef = ref(null)
const bookingForm = ref({ checkInDate: '', checkOutDate: '' })
const bookingRules = {
  checkInDate: [{ required: true, message: '请选择入住日期', trigger: 'change' }],
  checkOutDate: [{ required: true, message: '请选择退房日期', trigger: 'change' }],
}

// 预估总价（天数无效/NaN 时返回 null，不渲染提示）
const estimate = computed(() => {
  const { checkInDate: start, checkOutDate: end } = bookingForm.value
  if (!selectedRoom.value || !start || !end) return null
  const days = Math.round((new Date(end) - new Date(start)) / 86400000)
  if (!Number.isFinite(days) || days <= 0) return null
  return { days, total: (days * Number(selectedRoom.value.price)).toFixed(2) }
})

function handleBook(room) {
  // 只有后端推导为「可订」的房间才能下单
  if (room.availability !== 'available') {
    const info = availabilityOf(room)
    const extra = room.occupierOrderNo ? `，占用订单号：${room.occupierOrderNo}` : ''
    ElMessage.info(
      `${checkInDate.value} 至 ${checkOutDate.value} 房间 ${room.roomNumber} 已是${info.label}状态，不可预订${extra}`
    )
    return
  }
  // 未使用普通用户身份登录时跳统一登录页
  if (!userStore.isLoggedIn || !userStore.isCustomer) {
    ElMessage.warning('请先登录普通用户账号再预订')
    router.push('/login')
    return
  }
  selectedRoom.value = room
  // 用当前查询区间预填预订日期
  bookingForm.value = {
    checkInDate: checkInDate.value,
    checkOutDate: checkOutDate.value,
  }
  bookingVisible.value = true
}

async function handleSubmit() {
  if (!bookingFormRef.value) return
  await bookingFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (bookingForm.value.checkOutDate <= bookingForm.value.checkInDate) {
      ElMessage.warning('退房日期必须晚于入住日期')
      return
    }
    if (!selectedRoom.value || !selectedRoom.value.id) {
      ElMessage.warning('未选择房间，请重新选择')
      return
    }
    submitting.value = true
    try {
      await clientApi.book({
        roomId: selectedRoom.value.id,
        checkInDate: bookingForm.value.checkInDate,
        checkOutDate: bookingForm.value.checkOutDate,
      })
      ElMessage.success('预订成功，请前往"我的订单"支付')
      bookingVisible.value = false
      loadRooms()
    } catch (e) {
      // 拦截器已提示
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  loadRooms()
})
</script>

<template>
  <div v-loading="loading">
    <!-- 日期区间选择：房态按所选区间推导 -->
    <el-card shadow="never" style="margin-bottom: 16px;">
      <div style="display: flex; align-items: center; flex-wrap: wrap; gap: 10px;">
        <span style="font-size: 14px; color: #606266;">入住日期</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="入住日期"
          end-placeholder="退房日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledDate"
          :clearable="false"
          style="width: 300px;"
          @change="handleDateChange"
        />
        <span style="font-size: 12px; color: #909399;">默认今天至明天，更换日期后自动刷新房态</span>
      </div>
    </el-card>

    <!-- 统计栏 -->
    <el-row :gutter="16" style="margin-bottom: 20px;">
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #909399;">总客房</div>
          <div style="font-size: 28px; font-weight: bold; color: #303133;">{{ stats.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #67C23A;">可订</div>
          <div style="font-size: 28px; font-weight: bold; color: #67C23A;">{{ stats.available }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #E6A23C;">已订</div>
          <div style="font-size: 28px; font-weight: bold; color: #E6A23C;">{{ stats.booked }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #F56C6C;">在住</div>
          <div style="font-size: 28px; font-weight: bold; color: #F56C6C;">{{ stats.occupied }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 客房卡片 -->
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600;">全部客房</span>
      </template>
      <el-row :gutter="16">
        <el-col
          v-for="room in rooms"
          :key="room.id"
          :xs="12" :sm="8" :md="6" :lg="4" :xl="3"
          style="margin-bottom: 16px;"
        >
          <el-card
            shadow="hover"
            :body-style="{ padding: '16px', textAlign: 'center', cursor: 'pointer', border: '2px solid ' + availabilityOf(room).borderColor, background: availabilityOf(room).bg }"
            @click="handleBook(room)"
          >
            <div style="font-size: 22px; font-weight: bold; color: #303133; margin-bottom: 6px;">
              {{ room.roomNumber }}
            </div>
            <div style="font-size: 13px; color: #909399; margin-bottom: 8px;">
              {{ room.type }} · {{ room.floor }}楼
            </div>
            <el-tag :type="availabilityOf(room).tagType" size="small">
              {{ availabilityOf(room).label }}
            </el-tag>
            <div style="font-size: 15px; font-weight: bold; color: #f56c6c; margin-top: 8px;">
              {{ formatMoney(room.price) }} / 晚
            </div>
            <div style="font-size: 12px; color: #909399; margin-top: 6px; height: 32px; overflow: hidden;">
              {{ room.description || '舒适客房' }}
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && rooms.length === 0" description="暂无可预订客房" />
    </el-card>

    <!-- 预订对话框 -->
    <el-dialog v-model="bookingVisible" title="预订客房" width="440px" :close-on-click-modal="false">
      <el-descriptions :column="2" border style="margin-bottom: 18px;">
        <el-descriptions-item label="房间号">{{ selectedRoom?.roomNumber }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ selectedRoom?.type }}</el-descriptions-item>
        <el-descriptions-item label="楼层">{{ selectedRoom?.floor }} 楼</el-descriptions-item>
        <el-descriptions-item label="单价">{{ formatMoney(selectedRoom?.price) }} / 晚</el-descriptions-item>
      </el-descriptions>

      <el-form ref="bookingFormRef" :model="bookingForm" :rules="bookingRules" label-width="90px">
        <el-form-item label="入住日期" prop="checkInDate">
          <el-date-picker
            v-model="bookingForm.checkInDate"
            type="date"
            placeholder="选择入住日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="退房日期" prop="checkOutDate">
          <el-date-picker
            v-model="bookingForm.checkOutDate"
            type="date"
            placeholder="选择退房日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
            style="width: 100%;"
          />
        </el-form-item>
        <el-alert
          v-if="estimate"
          :title="`共 ${estimate.days} 晚，预计总价 ${formatMoney(estimate.total)}`"
          type="info"
          :closable="false"
          style="margin-left: 90px;"
        />
      </el-form>

      <template #footer>
        <el-button @click="bookingVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认预订</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped></style>
