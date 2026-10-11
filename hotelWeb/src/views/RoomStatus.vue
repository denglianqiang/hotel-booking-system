<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { roomApi } from '@/api'
import { formatMoney } from '@/utils/format'
import { useConfirmAction } from '@/composables/useConfirmAction'
import BookingDialog from '@/components/BookingDialog.vue'
import { availabilityOf } from '@/constants/roomStatus'

const rooms = ref([])
const loading = ref(false)
const bookingVisible = ref(false)
const selectedRoom = ref(null)

// 行级操作状态：正在恢复空闲的房间卡片转圈，其它房间卡片禁用点击
const { acting, isActing, run } = useConfirmAction()

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

// 日期变更后按新区间重新查询房态
function handleDateChange() {
  if (!checkInDate.value || !checkOutDate.value) return
  loadRooms()
}

// 按楼层分组
const floorGroups = computed(() => {
  const groups = {}
  rooms.value.forEach((room) => {
    if (!groups[room.floor]) {
      groups[room.floor] = []
    }
    groups[room.floor].push(room)
  })
  // 按楼层排序
  return Object.keys(groups)
    .sort((a, b) => a - b)
    .map((floor) => ({
      floor: parseInt(floor),
      rooms: groups[floor],
    }))
})

// 统计数据：按后端推导出的占用状态（availability）计数
const stats = computed(() => {
  const result = { total: 0, available: 0, booked: 0, occupied: 0, repair: 0 }
  rooms.value.forEach((room) => {
    result.total++
    const key = room.availability
    if (key === 'available') result.available++
    else if (key === 'booked') result.booked++
    else if (key === 'occupied') result.occupied++
    else result.repair++
  })
  return result
})

async function loadRooms() {
  // 请求进行中直接忽略，避免重复点击发起并发请求
  if (loading.value) return
  loading.value = true
  try {
    const res = await roomApi.getStatus({
      checkInDate: checkInDate.value,
      checkOutDate: checkOutDate.value,
    })
    rooms.value = res.data || []
  } catch (e) {
    // 拦截器已提示错误
  } finally {
    loading.value = false
  }
}

function handleRoomClick(room) {
  const info = availabilityOf(room)
  if (room.availability === 'available') {
    // 可订房间 → 弹出预订对话框
    selectedRoom.value = room
    bookingVisible.value = true
  } else if (room.availability === 'repair') {
    // 维修房间 → 确认恢复为正常销售
    if (loading.value) return
    run({
      row: room.id,
      message: `确认房间「${room.roomNumber}」维修完成，恢复为正常销售？`,
      title: '恢复空闲',
      prompt: {
        confirmButtonText: '恢复空闲',
        cancelButtonText: '取消',
      },
      action: () => roomApi.setStatus(room.id, 0),
      success: `房间 ${room.roomNumber} 已恢复空闲`,
      onDone: loadRooms,
    })
  } else {
    // 已订 / 在住：占用单可能来自当前查询区间
    const extra = room.occupierOrderNo ? `，占用订单号：${room.occupierOrderNo}` : ''
    ElMessage.info(
      `${checkInDate.value} 至 ${checkOutDate.value} 房间 ${room.roomNumber} 已是${info.label}状态，不可预订${extra}`
    )
  }
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
        <span style="font-size: 14px; color: #606266;">查询日期</span>
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
      <el-col :span="4">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #909399;">总房间数</div>
          <div style="font-size: 28px; font-weight: bold; color: #303133;">{{ stats.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #67C23A;">可订</div>
          <div style="font-size: 28px; font-weight: bold; color: #67C23A;">{{ stats.available }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #E6A23C;">已订</div>
          <div style="font-size: 28px; font-weight: bold; color: #E6A23C;">{{ stats.booked }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #F56C6C;">在住</div>
          <div style="font-size: 28px; font-weight: bold; color: #F56C6C;">{{ stats.occupied }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" body-style="padding: 15px;">
          <div style="font-size: 13px; color: #909399;">维修</div>
          <div style="font-size: 28px; font-weight: bold; color: #909399;">{{ stats.repair }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="refresh-card" :body-style="{ padding: '15px' }" @click="loadRooms">
          <div class="refresh-inner">
            <el-icon class="refresh-icon"><Refresh /></el-icon>
            <span class="refresh-text">刷新房态</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 房态图：按楼层分组 -->
    <div v-for="group in floorGroups" :key="group.floor" style="margin-bottom: 24px;">
      <div style="font-size: 15px; font-weight: bold; color: #303133; margin-bottom: 12px;">
        {{ group.floor }} 楼
      </div>
      <el-row :gutter="16">
        <el-col
          v-for="room in group.rooms"
          :key="room.id"
          :xs="12" :sm="8" :md="6" :lg="4" :xl="3"
        >
          <el-card
            shadow="hover"
            v-loading="isActing(room.id)"
            :body-style="{ padding: '20px', textAlign: 'center', cursor: acting !== null && !isActing(room.id) ? 'not-allowed' : 'pointer', opacity: acting !== null && !isActing(room.id) ? 0.6 : 1, border: '2px solid ' + availabilityOf(room).borderColor, background: availabilityOf(room).bg }"
            @click="handleRoomClick(room)"
          >
            <div style="font-size: 22px; font-weight: bold; color: #303133; margin-bottom: 8px;">
              {{ room.roomNumber }}
            </div>
            <div style="font-size: 13px; color: #909399; margin-bottom: 8px;">
              {{ room.type }}
            </div>
            <el-tag :type="availabilityOf(room).tagType" size="small">
              {{ availabilityOf(room).label }}
            </el-tag>
            <div style="font-size: 12px; color: #909399; margin-top: 8px;">
              {{ formatMoney(room.price) }} / 晚
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!loading && rooms.length === 0" description="暂无房间数据" />

    <!-- 预订对话框：带上当前查询的日期区间 -->
    <BookingDialog
      v-model="bookingVisible"
      :room="selectedRoom"
      :check-in-date="checkInDate"
      :check-out-date="checkOutDate"
      @success="loadRooms"
    />
  </div>
</template>

<style scoped>
.refresh-card {
  cursor: pointer;
  transition: all 0.25s;
}

.refresh-card:hover {
  border-color: #409eff;
}

.refresh-inner {
  height: 52px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.refresh-icon {
  font-size: 24px;
  color: #409eff;
  transition: transform 0.5s ease;
}

.refresh-card:hover .refresh-icon {
  transform: rotate(180deg);
}

.refresh-text {
  font-size: 13px;
  color: #909399;
  transition: color 0.25s;
}

.refresh-card:hover .refresh-text {
  color: #409eff;
}
</style>
