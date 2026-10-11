<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { clientApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/constants/orderStatus'

const userStore = useUserStore()

// ===== 基本资料 =====
// 手机号是登录账号、证件号由前台核实录入，两者只读；用户可自助修改的只有姓名
const profileFormRef = ref(null)
const profileSaving = ref(false)
const profileInfo = reactive({ phone: '', gender: '', idCard: '', registerTime: '' })
const profileForm = reactive({ name: '' })

const profileRules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名不能超过 50 个字符', trigger: 'blur' },
  ],
}

// 证件号脱敏展示：保留前 6 后 4（客户端自己也只能看到脱敏值，完整信息前台可查）
function maskIdCard(value) {
  if (!value) return '未填写'
  if (value.length <= 10) return value
  return value.slice(0, 6) + '********' + value.slice(-4)
}

// 头像取姓名末字（符合中文称呼习惯），没有姓名时兜底"客"
const avatarChar = computed(() => {
  const name = (profileForm.name || '').trim()
  return name ? name.charAt(name.length - 1) : '客'
})

async function loadProfile() {
  const res = await clientApi.info()
  const data = res?.data || {}
  profileInfo.phone = data.phone || ''
  profileInfo.gender = data.gender || ''
  profileInfo.idCard = data.idCard || ''
  profileInfo.registerTime = data.registerTime || ''
  profileForm.name = data.name || ''
}

async function handleSaveProfile() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    profileSaving.value = true
    try {
      const res = await clientApi.updateProfile({ name: profileForm.name })
      // 同步登录态昵称，顶栏立即显示新姓名，刷新后也不回退
      userStore.setNickname(res?.data?.name || profileForm.name)
      ElMessage.success('资料已更新')
    } catch (e) {
      // 拦截器已提示
    } finally {
      profileSaving.value = false
    }
  })
}

// ===== 修改密码 =====
const passwordFormRef = ref(null)
const passwordSaving = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '新密码长度需在 6 到 20 位之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// 前端强度估算：长度 + 字符组合，仅作提示用，真正的规则校验在表单规则里
const strength = computed(() => {
  const p = passwordForm.newPassword
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (/[a-zA-Z]/.test(p) && /\d/.test(p)) score++
  if (p.length >= 12 || /[^a-zA-Z0-9]/.test(p)) score++
  return Math.max(1, score)
})
const strengthText = computed(() => ['', '弱', '中', '强'][strength.value])

async function handleChangePassword() {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordSaving.value = true
    try {
      await clientApi.changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
      })
      ElMessage.success('密码修改成功，下次登录请使用新密码')
      passwordFormRef.value.resetFields()
    } catch (e) {
      // 拦截器已提示
    } finally {
      passwordSaving.value = false
    }
  })
}

onMounted(() => {
  loadProfile()
})
</script>

<template>
  <el-row :gutter="16" class="profile-row">
    <!-- 基本资料 -->
    <el-col :xs="24" :md="12">
      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">基本资料</span>
            <span class="panel-sub">完善个人信息，让入住更便捷</span>
          </div>
        </template>

        <!-- 身份区 -->
        <div class="identity">
          <div class="avatar">{{ avatarChar }}</div>
          <div class="identity-info">
            <div class="identity-name">
              {{ profileForm.name || '未命名用户' }}
              <el-tag size="small" type="success" effect="light" round>普通用户</el-tag>
            </div>
            <div class="identity-sub">欢迎回来，祝您入住愉快</div>
          </div>
        </div>

        <el-divider style="margin: 16px 0 8px;" />

        <!-- 信息行 -->
        <div class="info-list">
          <div class="info-row">
            <span class="info-icon"><el-icon><Iphone /></el-icon></span>
            <span class="info-label">手机号</span>
            <span class="info-value">
              {{ profileInfo.phone || '—' }}
              <el-tag size="small" type="info" effect="plain">登录账号</el-tag>
            </span>
          </div>
          <div class="info-row">
            <span class="info-icon"><el-icon><User /></el-icon></span>
            <span class="info-label">性别</span>
            <span class="info-value">{{ profileInfo.gender || '未填写' }}</span>
          </div>
          <div class="info-row">
            <span class="info-icon"><el-icon><Postcard /></el-icon></span>
            <span class="info-label">证件号</span>
            <span class="info-value">{{ maskIdCard(profileInfo.idCard) }}</span>
          </div>
          <div class="info-row">
            <span class="info-icon"><el-icon><Clock /></el-icon></span>
            <span class="info-label">注册时间</span>
            <span class="info-value">{{ profileInfo.registerTime ? formatDateTime(profileInfo.registerTime) : '—' }}</span>
          </div>
        </div>

        <div class="note">手机号是登录账号，如需变更请联系酒店前台办理</div>

        <el-divider style="margin: 8px 0 16px;" />

        <!-- 姓名编辑 -->
        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="60px">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="profileForm.name" maxlength="50" placeholder="请输入姓名" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="profileSaving" @click="handleSaveProfile">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <!-- 修改密码 -->
    <el-col :xs="24" :md="12">
      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">修改密码</span>
            <span class="panel-sub">建议定期更换，保障账号安全</span>
          </div>
        </template>

        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="80px">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <div class="field-block">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6-20 位，建议字母 + 数字组合" />
              <div v-if="passwordForm.newPassword" class="strength">
                <div class="strength-bars">
                  <span v-for="i in 3" :key="i" class="bar" :class="{ active: strength >= i, ['lv' + strength]: strength >= i }" />
                </div>
                <span class="strength-text" :class="'lv' + strength">{{ strengthText }}</span>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" @keyup.enter="handleChangePassword" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordSaving" @click="handleChangePassword">修改密码</el-button>
          </el-form-item>
        </el-form>

        <el-divider style="margin: 4px 0 14px;" />

        <div class="tips">
          <div class="tips-title">安全提示</div>
          <div class="tip">· 密码长度 6-20 位，建议字母与数字组合</div>
          <div class="tip">· 修改成功后，下次登录请使用新密码</div>
          <div class="tip">· 忘记密码请联系酒店前台重置</div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<style scoped>
/* 两栏等高：行内拉伸，卡片撑满列高，底边对齐 */
.profile-row {
  align-items: stretch;
}
.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 10px;
  border: none;
  box-shadow: 0 1px 4px rgba(31, 58, 95, 0.08);
}
.panel :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 卡片头：标题 + 副标题 */
.panel-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.panel-title {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
  display: flex;
  align-items: center;
}
.panel-title::before {
  content: '';
  width: 3px;
  height: 14px;
  background: #409eff;
  border-radius: 2px;
  margin-right: 8px;
}
.panel-sub {
  font-size: 12px;
  color: #c0c4cc;
}

/* 身份区 */
.identity {
  display: flex;
  align-items: center;
  gap: 14px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2c5f8a, #3a7ca5);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(44, 95, 138, 0.35);
}
.identity-name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.identity-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
}

/* 信息行 */
.info-list {
  display: flex;
  flex-direction: column;
}
.info-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
}
.info-icon {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: #ecf5ff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
}
.info-label {
  width: 64px;
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
}
.info-value {
  color: #303133;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  word-break: break-all;
}
.note {
  margin: 10px 0 0 40px;
  font-size: 12px;
  color: #c0c4cc;
}

/* 表单 */
.field-block {
  width: 100%;
}
.strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}
.strength-bars {
  display: flex;
  gap: 4px;
  flex: 1;
}
.bar {
  height: 4px;
  flex: 1;
  border-radius: 2px;
  background: #ebeef5;
  transition: background 0.2s;
}
.bar.active.lv1 { background: #f56c6c; }
.bar.active.lv2 { background: #e6a23c; }
.bar.active.lv3 { background: #67c23a; }
.strength-text { font-size: 12px; color: #c0c4cc; }
.strength-text.lv1 { color: #f56c6c; }
.strength-text.lv2 { color: #e6a23c; }
.strength-text.lv3 { color: #67c23a; }

/* 安全提示 */
.tips {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 14px;
  margin-top: auto;
}
.tips-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}
.tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.9;
}
</style>
