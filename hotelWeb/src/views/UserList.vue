<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, roleApi } from '@/api'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { formatDateTime } from '@/constants/orderStatus'
// 权限码 -> 页面名称的展示映射；角色编辑对话框的勾选项以后端 /role/permissions 为准
import { ADMIN_PAGES } from '@/constants/permissions'

const loading = ref(false)
const tableData = ref([])

// 行级操作状态：正在执行的行按钮转圈，其它行按钮禁用
const { acting, isActing, run } = useConfirmAction()

// ===== 角色数据（RBAC：账号权限 = 所选角色的权限并集） =====
const roles = ref([])
const permList = ref([])

function roleText(role) {
  // 账号可能持有多个角色，后端以逗号分隔返回
  return String(role || '').split(',').filter(Boolean)
}

function permissionLabels(permissions) {
  return (permissions || '').split(',').filter(Boolean)
    .map((p) => ADMIN_PAGES.find((o) => o.code === p)?.label || p)
}

function permCodeLabel(code) {
  return ADMIN_PAGES.find((o) => o.code === code)?.label || code
}

async function loadData() {
  loading.value = true
  try {
    const [userRes, roleRes, permRes] = await Promise.all([
      userApi.getList(),
      roleApi.getList(),
      roleApi.getPermissions(),
    ])
    tableData.value = userRes.data || []
    roles.value = roleRes.data || []
    permList.value = permRes.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

// ===== 账号新增/编辑对话框 =====
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const submitting = ref(false)
const form = reactive({
  id: null,
  username: '',
  password: '',
  nickname: '',
  roleIds: [],
})

// 所选角色的权限并集预览（编辑账号时直观看到最终可访问的页面）
const mergedPermissions = computed(() => {
  const codes = new Set()
  for (const roleId of form.roleIds) {
    const role = roles.value.find((r) => r.id === roleId)
    for (const code of role?.permissionCodes || []) {
      codes.add(code)
    }
  }
  return [...codes]
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    {
      // 新增必填，编辑时选填（填写即重置）
      validator: (rule, value, callback) => {
        if (!form.id && !value) {
          callback(new Error('请输入密码'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  roleIds: [
    {
      validator: (rule, value, callback) => {
        if (!value || !value.length) {
          callback(new Error('请至少分配一个角色'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
}

function handleAdd() {
  dialogTitle.value = '新增账号'
  Object.assign(form, {
    id: null,
    username: '',
    password: '',
    nickname: '',
    roleIds: [],
  })
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑账号'
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    nickname: row.nickname,
    roleIds: roleText(row.role)
      .map((code) => roles.value.find((r) => r.roleCode === code)?.id)
      .filter((id) => id != null),
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        id: form.id,
        username: form.username,
        nickname: form.nickname,
        roleIds: form.roleIds,
      }
      if (form.id) {
        // 编辑：密码填写了才重置
        if (form.password) {
          payload.password = form.password
        }
        await userApi.update(payload)
        ElMessage.success('修改成功')
      } else {
        payload.password = form.password
        await userApi.save(payload)
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
    message: `确认删除账号「${row.username}」？`,
    title: '确认删除',
    action: () => userApi.delete(row.id),
    success: '删除成功',
    onDone: loadData,
  })
}

// ===== 角色管理（新增/编辑/删除角色并勾选其权限） =====
const roleDialogVisible = ref(false)
const roleDialogTitle = ref('')
const roleFormRef = ref(null)
const roleSubmitting = ref(false)
const roleForm = reactive({
  id: null,
  roleCode: '',
  roleName: '',
  description: '',
  permissionCodes: [],
})

const roleRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  permissionCodes: [
    {
      validator: (rule, value, callback) => {
        if (!value || !value.length) {
          callback(new Error('请至少勾选一个权限'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
}

function handleRoleAdd() {
  roleDialogTitle.value = '新增角色'
  Object.assign(roleForm, {
    id: null,
    roleCode: '',
    roleName: '',
    description: '',
    permissionCodes: [],
  })
  roleDialogVisible.value = true
}

function handleRoleEdit(row) {
  roleDialogTitle.value = '编辑角色'
  Object.assign(roleForm, {
    id: row.id,
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description,
    permissionCodes: [...(row.permissionCodes || [])],
  })
  roleDialogVisible.value = true
}

async function handleRoleSave() {
  if (!roleFormRef.value) return
  await roleFormRef.value.validate(async (valid) => {
    if (!valid) return
    roleSubmitting.value = true
    try {
      const payload = {
        id: roleForm.id,
        roleCode: roleForm.roleCode,
        roleName: roleForm.roleName,
        description: roleForm.description,
        permissionCodes: roleForm.permissionCodes,
      }
      if (roleForm.id) {
        await roleApi.update(payload)
        ElMessage.success('修改成功')
      } else {
        await roleApi.save(payload)
        ElMessage.success('新增成功')
      }
      roleDialogVisible.value = false
      loadData()
    } catch (e) {
      // 拦截器已提示
    } finally {
      roleSubmitting.value = false
    }
  })
}

function handleRoleDelete(row) {
  run({
    row: 'role-' + row.id,
    message: `确认删除角色「${row.roleName}」？删除后其权限配置不可恢复。`,
    title: '确认删除',
    action: () => roleApi.delete(row.id),
    success: '删除成功',
    onDone: loadData,
  })
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-weight: 600;">
            角色管理
            <span style="font-size: 12px; color: #909399; margin-left: 8px; font-weight: normal;">
              RBAC 权限模型：权限挂在角色上，账号通过角色获得可访问页面；调整角色权限对持有账号立即生效
            </span>
          </span>
          <el-button type="primary" size="small" @click="handleRoleAdd">新增角色</el-button>
        </div>
      </template>

      <el-table :data="roles" v-loading="loading" border size="small" style="width: 100%;">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="roleCode" label="角色编码" width="130" />
        <el-table-column prop="roleName" label="角色名称" width="130">
          <template #default="{ row }">
            {{ row.roleName }}
            <el-tag v-if="row.builtIn === 1" type="info" size="small" style="margin-left: 4px;">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="权限（可访问页面）" min-width="260">
          <template #default="{ row }">
            <el-tag
              v-for="code in row.permissionCodes"
              :key="code"
              size="small"
              style="margin-right: 6px;"
            >{{ permCodeLabel(code) }}</el-tag>
            <span v-if="!(row.permissionCodes || []).length" style="color: #c0c4cc;">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleRoleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.builtIn !== 1"
              type="danger"
              size="small"
              :loading="isActing('role-' + row.id)"
              :disabled="acting !== null && !isActing('role-' + row.id)"
              @click="handleRoleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无角色数据" />
        </template>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-weight: 600;">
            账号管理
            <span style="font-size: 12px; color: #909399; margin-left: 8px; font-weight: normal;">
              为账号分配角色，可访问页面由角色的权限并集决定
            </span>
          </span>
          <el-button type="primary" size="small" @click="handleAdd">新增账号</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border style="width: 100%;">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column label="角色" width="180">
          <template #default="{ row }">
            <el-tag
              v-for="code in roleText(row.role)"
              :key="code"
              :type="code === 'operator' ? 'danger' : 'primary'"
              size="small"
              style="margin-right: 6px;"
            >{{ code }}</el-tag>
            <span v-if="!roleText(row.role).length" style="color: #c0c4cc;">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="可访问页面" min-width="260">
          <template #default="{ row }">
            <el-tag
              v-for="label in permissionLabels(row.permissions)"
              :key="label"
              size="small"
              style="margin-right: 6px;"
            >{{ label }}</el-tag>
            <span v-if="!permissionLabels(row.permissions).length" style="color: #c0c4cc;">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="!roleText(row.role).includes('operator')"
              type="danger"
              size="small"
              :loading="isActing(row.id)"
              :disabled="acting !== null && !isActing(row.id)"
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无账号数据" />
        </template>
      </el-table>
    </el-card>

    <!-- 账号新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录账号" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="页面显示的名称" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '不修改请留空' : '登录密码'"
          />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="选择账号持有的角色" style="width: 100%;">
            <el-option
              v-for="r in roles"
              :key="r.id"
              :value="r.id"
              :label="r.roleName"
            >{{ r.roleName }}（{{ r.roleCode }}）</el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="可访问页面">
          <div v-if="mergedPermissions.length" style="line-height: 1.8;">
            <el-tag
              v-for="code in mergedPermissions"
              :key="code"
              size="small"
              style="margin-right: 6px;"
            >{{ permCodeLabel(code) }}</el-tag>
          </div>
          <span v-else style="color: #c0c4cc; font-size: 12px;">选择角色后展示该账号最终的权限并集</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色新增/编辑对话框 -->
    <el-dialog v-model="roleDialogVisible" :title="roleDialogTitle" width="520px">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input
            v-model="roleForm.roleCode"
            placeholder="如 finance，创建后不可修改"
            :disabled="!!roleForm.id"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="如 财务专员" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="roleForm.description" placeholder="该角色的职责说明（可选）" />
        </el-form-item>
        <el-form-item label="权限" prop="permissionCodes">
          <el-checkbox-group v-model="roleForm.permissionCodes">
            <el-checkbox v-for="p in permList" :key="p.id" :value="p.permCode">
              {{ p.permName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="handleRoleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped></style>
