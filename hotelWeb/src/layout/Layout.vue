<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Fold,
  Expand,
  UserFilled,
  SwitchButton,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
// 菜单项（含图标）由权限码统一派生，新增页面只需改 constants/permissions.js
import { menuItemsOfPermissions } from '@/constants/permissions'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 按权限过滤的菜单（权限来自 store，改动后可实时刷新）
const menuItems = computed(() => menuItemsOfPermissions(userStore.permissions))

const activeMenu = computed(() => route.path)

const isCollapse = ref(false)

// 当前登录用户昵称与角色
const nickname = computed(() => userStore.nickname || '管理员')
const roleText = computed(() => {
  if (userStore.role === 'operator') return '运营方'
  if (userStore.role === 'customer') return '普通用户'
  return '管理员'
})

function handleSelect(index) {
  router.push(index)
}

// 退出登录（注销由 store 统一处理，失败也会清理本地登录态）
function handleLogout() {
  ElMessageBox.confirm('确认退出登录？', '提示', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    })
    .catch(() => {})
}
</script>

<template>
  <el-container style="height: 100vh">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '210px'" style="background: #304156">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: bold; white-space: nowrap; overflow: hidden;">
        {{ isCollapse ? '酒店' : '酒店管理系统' }}
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        @select="handleSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主区域 -->
    <el-container>
      <!-- 顶栏 -->
      <el-header style="display: flex; align-items: center; background: #fff; border-bottom: 1px solid #e6e6e6;">
        <el-icon
          style="font-size: 20px; cursor: pointer; margin-right: 15px;"
          @click="isCollapse = !isCollapse"
        >
          <Fold v-if="isCollapse" />
          <Expand v-else />
        </el-icon>
        <span style="font-size: 16px; font-weight: 600; color: #333;">
          {{ route.meta.title }}
        </span>

        <!-- 右侧：用户信息 + 退出登录 -->
        <div style="margin-left: auto; display: flex; align-items: center; gap: 12px;">
          <el-tag size="small" :type="roleText === '运营方' ? 'danger' : 'primary'">{{ roleText }}</el-tag>
          <span style="display: flex; align-items: center; gap: 6px; color: #606266; font-size: 14px;">
            <el-icon><UserFilled /></el-icon>
            {{ nickname }}
          </span>
          <el-button type="danger" plain size="small" :icon="SwitchButton" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main style="background: #f0f2f5; overflow-y: auto;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped></style>
