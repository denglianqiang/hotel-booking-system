<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { statisticsApi } from '@/api'

const loading = ref(false)
// 加载失败标记：失败时展示空状态，避免继续展示旧数据造成误导
const loadFailed = ref(false)
const stats = ref({
  total: 0,
  idle: 0,
  booked: 0,
  checkedIn: 0,
  repair: 0,
  occupancyRate: 0,
})
const revenueDates = ref([])
const revenues = ref([])

// 无房间数据时不渲染图表
const hasRooms = computed(() => stats.value.total > 0)

const pieChartRef = ref()
const lineChartRef = ref()
let pieChart = null
let lineChart = null
// 图表容器尺寸监听（侧边栏折叠时自动重绘，替代 window.resize）
const resizeObserver = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(() => handleResize()) : null

// 清空看板数据（加载失败时不留旧数据）
function resetStats() {
  stats.value = { total: 0, idle: 0, booked: 0, checkedIn: 0, repair: 0, occupancyRate: 0 }
  revenueDates.value = []
  revenues.value = []
}

async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await statisticsApi.getDashboard()
    const data = res?.data
    // 响应结构异常时按加载失败处理
    if (!data || !data.roomStatus || !data.revenueTrend) {
      loadFailed.value = true
      resetStats()
      return
    }
    loadFailed.value = false
    // 房态统计（后端已按「今天」口径计算）
    const rs = data.roomStatus
    const rate = Number(rs.occupancyRate)
    stats.value = {
      total: rs.total ?? 0,
      idle: rs.idle ?? 0,
      booked: rs.booked ?? 0,
      checkedIn: rs.checkedIn ?? 0,
      repair: rs.repair ?? 0,
      // 分母为 0 时后端可能返回非数字，这里兜底为 0%
      occupancyRate: Number.isFinite(rate) ? rate : 0,
    }
    // 收入趋势
    const rt = data.revenueTrend
    revenueDates.value = rt.dates || []
    revenues.value = rt.revenues || []

    await nextTick()
    if (hasRooms.value) {
      renderPieChart()
      renderLineChart()
      setupResizeObserver()
    }
  } catch (e) {
    // 拦截器已提示
    loadFailed.value = true
    resetStats()
  } finally {
    loading.value = false
  }
}

function renderPieChart() {
  if (!pieChartRef.value || !hasRooms.value) return
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value)
  }
  const s = stats.value
  pieChart.setOption({
    title: { text: '今日房态分布', left: 'center', textStyle: { fontSize: 16 } },
    tooltip: { trigger: 'item', formatter: '{b}: {c}间 ({d}%)' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{c}间' },
        data: [
          { value: s.idle, name: '可订', itemStyle: { color: '#67C23A' } },
          { value: s.booked, name: '已订', itemStyle: { color: '#E6A23C' } },
          { value: s.checkedIn, name: '在住', itemStyle: { color: '#F56C6C' } },
          { value: s.repair, name: '维修', itemStyle: { color: '#909399' } },
        ],
      },
    ],
  })
}

function renderLineChart() {
  if (!lineChartRef.value || !hasRooms.value) return
  if (!lineChart) {
    lineChart = echarts.init(lineChartRef.value)
  }
  lineChart.setOption({
    title: { text: '近7天收入趋势', left: 'center', textStyle: { fontSize: 16 } },
    tooltip: { trigger: 'axis', formatter: '{b}<br/>收入: ¥{c}' },
    xAxis: {
      type: 'category',
      data: revenueDates.value,
      axisLabel: { rotate: 30, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      name: '收入（元）',
      axisLabel: { formatter: '¥{value}' },
    },
    grid: { left: 60, right: 30, bottom: 50, top: 50 },
    series: [
      {
        type: 'line',
        data: revenues.value,
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { width: 3, color: '#409EFF' },
        itemStyle: { color: '#409EFF' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.3)' },
            { offset: 1, color: 'rgba(64,158,255,0.05)' },
          ]),
        },
      },
    ],
  })
}

// 监听图表容器尺寸变化（侧边栏折叠/展开时重新适配）
function setupResizeObserver() {
  if (!resizeObserver) return
  resizeObserver.disconnect()
  if (pieChartRef.value) resizeObserver.observe(pieChartRef.value)
  if (lineChartRef.value) resizeObserver.observe(lineChartRef.value)
}

function handleResize() {
  // 无房间数据时图表已被销毁，不需要重新适配
  if (!hasRooms.value) return
  pieChart?.resize()
  lineChart?.resize()
}

onMounted(() => {
  loadData()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  pieChart?.dispose()
  lineChart?.dispose()
})
</script>

<template>
  <div v-loading="loading">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 20px;">
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 20px;">
          <div style="font-size: 14px; color: #909399;">总房间数</div>
          <div style="font-size: 32px; font-weight: bold; color: #303133; margin-top: 8px;">{{ stats.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 20px;">
          <div style="font-size: 14px; color: #67C23A;">可订</div>
          <div style="font-size: 32px; font-weight: bold; color: #67C23A; margin-top: 8px;">{{ stats.idle }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 20px;">
          <div style="font-size: 14px; color: #F56C6C;">在住</div>
          <div style="font-size: 32px; font-weight: bold; color: #F56C6C; margin-top: 8px;">{{ stats.checkedIn }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" body-style="padding: 20px;">
          <div style="font-size: 14px; color: #E6A23C;">出租率</div>
          <div style="font-size: 32px; font-weight: bold; color: #E6A23C; margin-top: 8px;">{{ stats.occupancyRate }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row v-if="hasRooms" :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <div ref="pieChartRef" style="width: 100%; height: 380px;"></div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <div ref="lineChartRef" style="width: 100%; height: 380px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 空状态：加载失败 或 暂无房间数据 -->
    <el-card v-if="!loading && (loadFailed || !hasRooms)" shadow="never">
      <el-empty :description="loadFailed ? '加载失败，请稍后重试' : '暂无房间数据'" />
    </el-card>
  </div>
</template>

<style scoped></style>
