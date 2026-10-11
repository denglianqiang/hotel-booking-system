<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { customerApi } from '@/api'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { phoneRules, idCardRules } from '@/utils/validators'
import { formatDateTime } from '@/constants/orderStatus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = ref({
  current: 1,
  size: 10,
  name: '',
  phone: '',
})

// 行级操作状态：正在执行的行按钮转圈，其它行按钮禁用
const { acting, isActing, run } = useConfirmAction()

// 新增/编辑对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const submitting = ref(false)
const form = reactive({
  id: null,
  name: '',
  phone: '',
  idCard: '',
  gender: '',
})

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: phoneRules({ required: true }),
  // 身份证号选填；填写则必须是 18 位（末位可为 X），与后端 CustomerDTO 的校验保持一致
  idCard: idCardRules(),
}

async function loadData() {
  loading.value = true
  try {
    const res = await customerApi.getList(query.value)
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
  query.value = { current: 1, size: 10, name: '', phone: '' }
  loadData()
}

function handleAdd() {
  dialogTitle.value = '新增用户'
  Object.assign(form, { id: null, name: '', phone: '', idCard: '', gender: '' })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑用户'
  Object.assign(form, { id: row.id, name: row.name, phone: row.phone, idCard: row.idCard, gender: row.gender })
  dialogVisible.value = true
}

// 重置登录密码：前台代客开通（未注册档案）或重置（已注册用户）。
// 密码不回显、不存明文，设置成功后用户即可用手机号 + 新密码登录普通用户端。
function handleResetPassword(row) {
  ElMessageBox.prompt(`为「${row.name}」设置登录密码（6-20 位），设置后即可用手机号登录`, '重置密码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'password',
    inputPattern: /^.{6,20}$/,
    inputErrorMessage: '密码长度需在 6 到 20 位之间',
  })
    .then(async ({ value }) => {
      await customerApi.resetPassword(row.id, value)
      ElMessage.success('密码已重置，该用户现在可用手机号 + 新密码登录')
      loadData()
    })
    .catch(() => {})
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (form.id) {
        await customerApi.update({ ...form })
        ElMessage.success('修改成功')
      } else {
        await customerApi.save({ ...form })
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
    message: `确认删除用户「${row.name}」？`,
    title: '确认删除',
    action: () => customerApi.delete(row.id),
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

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-weight: 600;">普通用户列表</span>
          <el-button type="primary" size="small" @click="handleAdd">新增用户</el-button>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" :model="query" style="margin-bottom: 16px;">
        <el-form-item label="姓名">
          <el-input v-model="query.name" placeholder="搜索用户姓名" style="width: 180px;" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="query.phone" placeholder="搜索电话" style="width: 180px;" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border style="width: 100%;">
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="idCard" label="身份证号" width="200" />
        <el-table-column prop="gender" label="性别" width="80" />
        <el-table-column label="注册状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.registered ? 'success' : 'info'" size="small">
              {{ row.registered ? '已注册' : '未注册' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="registerTime" label="注册时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.registerTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" size="plain" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button
              type="danger"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" />
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
          <div style="font-size: 12px; color: #909399; line-height: 1.6;">
            手机号即登录账号：修改后该用户需用新手机号登录；配合「重置密码」可代客开通或重置账号
          </div>
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="form.idCard" placeholder="请输入身份证号" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio value="男" />
            <el-radio value="女" />
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped></style>
