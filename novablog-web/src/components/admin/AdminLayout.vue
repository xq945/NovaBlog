<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores'

const router = useRouter()
const userStore = useUserStore()
</script>

<template>
  <div class="admin-page">
    <!-- 顶部导航 -->
    <nav class="navbar">
      <div class="nav-brand" @click="router.push('/')">
        <el-icon><ArrowLeft /></el-icon> 返回首页
      </div>
      <div class="nav-title">后台管理</div>
      <div class="nav-user">
        <span class="user-name">{{ userStore.userInfo?.nickname }}</span>
      </div>
    </nav>

    <!-- 主内容区 -->
    <div class="admin-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  background-color: var(--nb-bg-secondary, #f5f7fa);
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: var(--nb-bg-glass, rgba(255, 255, 255, 0.9));
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--nb-border-color, #e4e7ed);
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 500;
  color: var(--nb-text-secondary, #606266);
  cursor: pointer;
  transition: color 0.2s;
}

.nav-brand:hover {
  color: var(--el-color-primary, #409eff);
}

.nav-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--nb-text-primary, #303133);
}

.nav-user .user-name {
  font-size: 14px;
  color: var(--nb-text-secondary, #606266);
}

.admin-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}
</style>
