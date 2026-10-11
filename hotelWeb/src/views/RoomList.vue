<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { roomApi } from '@/api'
import { formatMoney } from '@/utils/format'
import { useConfirmAction } from '@/composables/useConfirmAction'
import {
  ROOM_STATUS_OPTIONS,
  roomStatusText,
  roomStatusTagType,
} from '@/constants/roomStatus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = ref({
  current: 1,
  size: 10,
  type: '',
  status: '',
  floor: '',
})

// 行级操作状态：正在执行的行按钮转圈，其它行按钮禁用
const { acting, isActing, run } = useConfirmAction()

// 可售状态选项（0 正常销售 / 3 停用维修）
const statusOptions = ROOM_STATUS_OPTIONS

const typeOptions = [
  { value: '单人间', label: '单人间' },
  { value: '双人间', label: '双人间' },
  { value: '套房', label: '套房' },
]

// 状态文案 / 标签类型统一走共享字典
const statusText = roomStatusText
const statusTagType = roomStatusTagType

// 新增/编辑对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const submitting = ref(false)
const form = reactive({
  id: null,
  roomNumber: '',
  type: '单人间',
  price: '',
  floor: '',
  description: '',
  status: 0,
})

const rules = {
  roomNumber: [{ required: true, message: '请输入房间号', trigger: 'blur' }],
  type: [{ required: true, message: '请选择房间类型', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  floor: [{ required: true, message: '请输入楼层', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const res = await roomApi.getList({
      current: query.value.current,
      size: query.value.size,
      type: query.value.type || undefined,
      status: query.value.status !== '' ? query.value.status : undefined,
      floor: query.value.floor || undefined,
    })
    tableData.value = res.data.records
    total.value = res.data.total
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
  query.value = { current: 1, size: 10, type: '', status: '', floor: '' }
  loadData()
}

function handleAdd() {
  dialogTitle.value = '新增房间'
  Object.assign(form, {
    id: null,
    roomNumber: '',
    type: '单人间',
    price: '',
    floor: '',
    description: '',
    status: 0,
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑房间'
  Object.assign(form, {
    id: row.id,
    roomNumber: row.roomNumber,
    type: row.type,
    price: row.price,
    floor: row.floor,
    description: row.description,
    status: row.status,
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (form.id) {
        // 编辑时不再提交 status：状态只能由列表行的「设为维修 / 恢复空闲」修改，
        // 避免弹窗里的旧值覆盖行操作刚改好的状态
        const { status: _status, ...payload } = form
        await roomApi.update(payload)
        ElMessage.success('修改成功')
      } else {
        await roomApi.save({ ...form })
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {
      // 拦截器已提示
    } finally {
      submitting.value = false
    }
  })
}

function handleDelete(row) {
  run({
    row: row.id,
    message: `确认删除房间「${row.roomNumber}」？`,
    title: '确认删除',
    action: () => roomApi.delete(row.id),
    success: '删除成功',
    onDone: () => {
      // 删除的是当前页最后一条且不在第 1 页时，回退一页，避免停留在空页
      if (tableData.value.length === 1 && query.value.current > 1) {
        query.value.current--
      }
      loadData()
    },
  })
}

// 设为维修 / 恢复空闲
function handleSetStatus(row, status) {
  const action = status === 3 ? '设为维修' : '恢复空闲'
  run({
    row: row.id,
    message: `确认将房间「${row.roomNumber}」${action}？`,
    title: '确认操作',
    action: () => roomApi.setStatus(row.id, status),
    success: `${action}成功`,
    onDone: loadData,
  })
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <!-- 筛选栏 -->
    <el-card shadow="never" style="margin-bottom: 16px;">
      <el-form :inline="true" :model="query">
        <el-form-item label="房间类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 120px;">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-input v-model="query.floor" placeholder="如 3" style="width: 80px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 房间列表 -->
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-weight: 600;">房间列表</span>
          <el-button type="primary" size="small" @click="handleAdd">新增房间</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border style="width: 100%;">
        <el-table-column prop="roomNumber" label="房间号" width="100" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="price" label="价格（元/晚）" width="130">
          <template #default="{ row }">{{ formatMoney(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="floor" label="楼层" width="80" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status === 0"
              type="warning"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleSetStatus(row, 3)"
            >设为维修</el-button>
            <el-button
              v-else-if="row.status === 3"
              type="success"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleSetStatus(row, 0)"
            >恢复空闲</el-button>
            <el-button
              v-if="row.status === 0 || row.status === 3"
              type="danger"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无房间数据" />
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="房间号" prop="roomNumber">
          <el-input v-model="form.roomNumber" placeholder="请输入房间号，如 501" />
        </el-form-item>
        <el-form-item label="房间类型" prop="type">
          <el-select v-model="form.type" style="width: 100%;">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格（元/晚）" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="10" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="楼层" prop="floor">
          <el-input-number v-model="form.floor" :min="1" :step="1" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="房间描述（选填）" />
        </el-form-item>
        <!-- 状态不在编辑弹窗中修改：只能通过列表行的「设为维修 / 恢复空闲」操作 -->
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped></style>
