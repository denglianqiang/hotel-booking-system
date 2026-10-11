<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { orderApi, customerApi } from '@/api'
import { formatMoney } from '@/utils/format'
import { phoneRules } from '@/utils/validators'

const props = defineProps({
  modelValue: Boolean,
  room: Object,
  // 房态图查询时选中的日期区间，用于打开对话框时预填
  checkInDate: {
    type: String,
    default: '',
  },
  checkOutDate: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref()
const submitting = ref(false)

const form = ref({
  customerId: '',
  checkInDate: '',
  checkOutDate: '',
})

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  checkInDate: [{ required: true, message: '请选择入住日期', trigger: 'change' }],
  checkOutDate: [{ required: true, message: '请选择退房日期', trigger: 'change' }],
}

// 客户下拉数据
const customers = ref([])
const customerLoading = ref(false)

// 快捷新建客户
const quickAddVisible = ref(false)
const quickFormRef = ref()
const quickAdding = ref(false)
const quickForm = ref({ name: '', phone: '', gender: '' })
const quickRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: phoneRules({ required: true }),
}

// 禁用今天之前的日期
function disabledDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

// 加载客户下拉数据
async function loadCustomers() {
  customerLoading.value = true
  try {
    const res = await customerApi.getAll()
    customers.value = res.data || []
  } catch (e) {
    // 拦截器已提示错误
  } finally {
    customerLoading.value = false
  }
}

// 客户下拉项显示文本
function customerLabel(customer) {
  return `${customer.name}（${customer.phone}）`
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      // 打开时重置表单并加载客户列表，日期用房态图当前查询区间预填
      form.value = {
        customerId: '',
        checkInDate: props.checkInDate || '',
        checkOutDate: props.checkOutDate || '',
      }
      quickAddVisible.value = false
      loadCustomers()
    }
  }
)

// 快捷新建面板收起时清空表单，避免上次残留内容
watch(quickAddVisible, (visible) => {
  if (!visible) {
    quickForm.value = { name: '', phone: '', gender: '' }
    quickFormRef.value?.clearValidate()
  }
})

// 快捷新建客户
async function handleQuickAdd() {
  if (!quickFormRef.value) return
  await quickFormRef.value.validate(async (valid) => {
    if (!valid) return
    quickAdding.value = true
    try {
      await customerApi.save({ ...quickForm.value })
      ElMessage.success('客户创建成功，已自动选中')
      quickAddVisible.value = false
      await loadCustomers()
      // 自动选中新创建的客户（手机号唯一，取匹配项）
      const created = customers.value.find((c) => c.phone === quickForm.value.phone)
      if (created) {
        form.value.customerId = created.id
      }
      quickForm.value = { name: '', phone: '', gender: '' }
    } catch (e) {
      // 拦截器已提示错误
    } finally {
      quickAdding.value = false
    }
  })
}

function handleClose() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!formRef.value) return
  // 房间未选中时无法下单，直接返回避免 room 为 null 抛错
  if (!props.room || !props.room.id) {
    ElMessage.warning('未选择房间，请重新选择')
    return
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    // 校验退房日期晚于入住日期
    if (form.value.checkOutDate <= form.value.checkInDate) {
      ElMessage.warning('退房日期必须晚于入住日期')
      return
    }
    submitting.value = true
    try {
      await orderApi.create({
        roomId: props.room.id,
        customerId: form.value.customerId,
        checkInDate: form.value.checkInDate,
        checkOutDate: form.value.checkOutDate,
      })
      ElMessage.success('预订成功')
      emit('success')
      handleClose()
    } catch (e) {
      // 拦截器已提示错误
    } finally {
      submitting.value = false
    }
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="预订房间"
    width="480px"
    :close-on-click-modal="false"
  >
    <!-- 房间信息 -->
    <el-descriptions :column="2" border style="margin-bottom: 20px;">
      <el-descriptions-item label="房间号">{{ room?.roomNumber }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ room?.type }}</el-descriptions-item>
      <el-descriptions-item label="楼层">{{ room?.floor }} 楼</el-descriptions-item>
      <el-descriptions-item label="单价">{{ formatMoney(room?.price) }} / 晚</el-descriptions-item>
    </el-descriptions>

    <!-- 预订表单 -->
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="客户" prop="customerId">
        <div style="display: flex; width: 100%; gap: 8px;">
          <el-select
            v-model="form.customerId"
            filterable
            placeholder="输入姓名或手机号搜索客户"
            :loading="customerLoading"
            style="flex: 1;"
          >
            <el-option
              v-for="c in customers"
              :key="c.id"
              :label="customerLabel(c)"
              :value="c.id"
            />
          </el-select>
          <el-button @click="quickAddVisible = !quickAddVisible">
            {{ quickAddVisible ? '收起' : '新客户' }}
          </el-button>
        </div>
      </el-form-item>

      <!-- 快捷新建客户 -->
      <el-form-item v-if="quickAddVisible" label-width="90px">
        <div class="quick-add-box">
          <div class="quick-add-title">快速新建客户</div>
          <el-form ref="quickFormRef" :model="quickForm" :rules="quickRules" label-width="60px">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="quickForm.name" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="电话" prop="phone">
              <el-input v-model="quickForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="性别">
              <el-radio-group v-model="quickForm.gender">
                <el-radio value="男">男</el-radio>
                <el-radio value="女">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label-width="60px">
              <el-button type="primary" size="small" :loading="quickAdding" @click="handleQuickAdd">
                保存并选择
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-form-item>

      <el-form-item label="入住日期" prop="checkInDate">
        <el-date-picker
          v-model="form.checkInDate"
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
          v-model="form.checkOutDate"
          type="date"
          placeholder="选择退房日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledDate"
          style="width: 100%;"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确认预订</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.quick-add-box {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 12px 0;
  background: #f8f9fb;
}

.quick-add-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
}
</style>
