<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Layout from '@/layout/Layout.vue'
import ClientLayout from '@/views/client/ClientLayout.vue'

const route = useRoute()
// 统一登录页全屏独立展示，/client 客人端使用独立布局，其余为管理后台布局
const layoutMode = computed(() => {
  if (route.path === '/login') return 'none'
  // 统一登录页全屏独立展示，普通用户端（/member）使用独立布局，其余为管理后台布局
  return route.path.startsWith('/member') ? 'client' : 'admin'
})
</script>

<template>
  <ClientLayout v-if="layoutMode === 'client'" />
  <Layout v-else-if="layoutMode === 'admin'" />
  <router-view v-else />
</template>

<style scoped></style>
