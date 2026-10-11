<script setup>
import { ref, computed, onMounted } from 'vue'
import { checkInApi } from '@/api'
import { formatMoney } from '@/utils/format'
import { formatDateTime, orderStatusTagType, orderStatusText } from '@/constants/orderStatus'

// ===== 页签 =====
const activeTab = ref('inhouse')

// ===== 页签一：在住客人 =====
const inHouseLoading = ref(false)
const inHouseList = ref([])

async function loadInHouse() {
  inHouseLoading.value = true
  try {
    const res = await checkInApi.getInHouse()
    inHouseList.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    inHouseLoading.value = false
  }
}

// ===== 页签二：历史入住记录 =====
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = ref({
  current: 1,
  size: 10,
  roomNumber: '',
  customerName: '',
  startDate: undefined,
  endDate: undefined,
})

// 日期区间：只有两端都选中才作为查询条件；清空时置为 undefined，查询参数会被省略
const dateRange = computed({
  get() {
    const { startDate, endDate } = query.value
    return startDate && endDate ? [startDate, endDate] : []
  },
  set(val) {
    const [start, end] = Array.isArray(val) ? val : []
    query.value.startDate = start && end ? start : undefined
    query.value.endDate = start && end ? end : undefined
  },
})

async function loadData() {
  loading.value = true
  try {
    const res = await checkInApi.getList(query.value)
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

// 查询：条件变化后回到第一页
function handleSearch() {
  reloadFromFirstPage()
}

function handleReset() {
  query.value = {
    current: 1,
    size: 10,
    roomNumber: '',
    customerName: '',
    startDate: undefined,
    endDate: undefined,
  }
  loadData()
}

// 切换页签时刷新对应数据
function handleTabChange(name) {
  if (name === 'inhouse') {
    loadInHouse()
  } else {
    handleSearch()
  }
}

onMounted(() => {
  loadInHouse()
})
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600;">入住登记</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 在住客人 -->
        <el-tab-pane label="在住客人" name="inhouse">
          <el-table :data="inHouseList" v-loading="inHouseLoading" border style="width: 100%;">
            <el-table-column prop="orderNo" label="订单号" width="180" />
            <el-table-column prop="roomNumber" label="房间号" width="100" />
            <el-table-column prop="customerName" label="客户姓名" width="120" />
            <el-table-column prop="actualCheckInTime" label="实际入住时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.actualCheckInTime) }}</template>
            </el-table-column>
            <el-table-column prop="checkOutDate" label="预计退房日期" width="130" />
            <el-table-column prop="totalPrice" label="房费" width="110">
              <template #default="{ row }">{{ formatMoney(row.totalPrice) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="orderStatusTagType(row.status)" size="small">
                  {{ orderStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无在住客人" />
            </template>
          </el-table>
        </el-tab-pane>

        <!-- 历史入住记录 -->
        <el-tab-pane label="历史入住记录" name="history">
          <!-- 筛选区 -->
          <el-form :inline="true" :model="query" style="margin-bottom: 16px;">
            <el-form-item label="房间号">
              <el-input v-model="query.roomNumber" placeholder="搜索房间号" style="width: 160px;" clearable @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="客户姓名">
              <el-input v-model="query.customerName" placeholder="搜索客户姓名" style="width: 160px;" clearable @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="入住日期">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :clearable="true"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 260px;"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch">查询</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>

          <!-- 表格 -->
          <el-table :data="tableData" v-loading="loading" border style="width: 100%;">
            <el-table-column prop="roomNumber" label="房间号" width="100" />
            <el-table-column prop="customerName" label="客户姓名" width="120" />
            <el-table-column prop="checkInTime" label="入住时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.checkInTime) }}</template>
            </el-table-column>
            <el-table-column prop="checkOutTime" label="退房时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.checkOutTime) }}</template>
            </el-table-column>
            <el-table-column prop="actualPrice" label="实收金额" width="120">
              <template #default="{ row }">{{ formatMoney(row.actualPrice) }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" min-width="120" />
            <template #empty>
              <el-empty description="暂无入住记录" />
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
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped></style>
