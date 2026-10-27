<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores'
import { useNotificationStore } from '../stores/notification'
import { logout } from '../api/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const isLoggedIn = computed(() => userStore.isLoggedIn())
const isAdmin = computed(() => userStore.userInfo?.role === 'ADMIN')
const nickname = computed(() => userStore.userInfo?.nickname || '')
const unreadCount = computed(() => notificationStore.unreadCount)

const navItems = computed(() => {
  const items = [
    { name: '首页', path: '/', icon: 'HomeFilled' },
    { name: '问答', path: '/chat', icon: 'ChatDotRound' }
  ]

  if (isLoggedIn.value) {
    items.push(
      { name: '写文章', path: '/article/create', icon: 'Plus' },
      { name: '我的收藏', path: '/favorites', icon: 'Star' },
      { name: '通知', path: '/notifications', icon: 'Bell' },
      { name: '个人中心', path: '/profile', icon: 'User' }
    )
  }

  return items
})

const isActive = (path) => {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}

const navigate = (path) => {
  router.push(path)
}

const handleLogout = async () => {
  try {
    // 必须先等接口返回再 reload，否则页面跳转会中断在途请求，服务端收不到撤销指令。
    // 加超时兜底：网络挂起时不至于让用户卡在登出按钮上
    await Promise.race([
      logout(userStore.refreshToken),
      new Promise((resolve) => setTimeout(resolve, 2000))
    ])
  } catch {
    // 服务端不可达不阻塞本地登出，清掉本地状态即可
  }
  userStore.clearToken()
  ElMessage.success('已退出登录')
  window.location.reload()
}

const goToLogin = () => {
  router.push('/login')
}
</script>

<template>
  <aside class="app-sidebar">
    <a class="sidebar-brand" href="https://github.com/xq945/NovaBlog" target="_blank" rel="noopener noreferrer">
      NovaBlog
    </a>

    <nav class="sidebar-nav">
      <div
        v-for="item in navItems"
        :key="item.path"
        class="sidebar-item"
        :class="{ active: isActive(item.path) }"
        @click="navigate(item.path)"
      >
        <el-badge
          v-if="item.path === '/notifications'"
          :value="unreadCount"
          :max="99"
          :hidden="unreadCount === 0"
          class="nav-badge"
        >
          <el-icon>
            <component :is="item.icon" />
          </el-icon>
        </el-badge>
        <el-icon v-else>
          <component :is="item.icon" />
        </el-icon>
        <span>{{ item.name }}</span>
      </div>
    </nav>

    <div class="sidebar-footer">
      <template v-if="isLoggedIn">
        <div class="user-info">
          <el-avatar :size="32" :src="userStore.userInfo?.avatar">
            {{ nickname.charAt(0).toUpperCase() }}
          </el-avatar>
          <span class="user-name">{{ nickname }}</span>
        </div>
        <div class="sidebar-actions">
          <div
            v-if="isAdmin"
            class="sidebar-item"
            :class="{ active: isActive('/admin') }"
            @click="navigate('/admin')"
          >
            <el-icon><Setting /></el-icon>
            <span>后台管理</span>
          </div>
          <div class="sidebar-item logout" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            <span>退出</span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="sidebar-item" @click="goToLogin">
          <el-icon><User /></el-icon>
          <span>登录</span>
        </div>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.app-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  width: 220px;
  height: 100vh;
  background: var(--nb-bg-secondary);
  border-right: 1px solid var(--nb-border-color);
  display: flex;
  flex-direction: column;
  z-index: 100;
}

.sidebar-brand {
  display: block;
  padding: 20px 24px;
  font-size: 1.5rem;
  font-weight: 700;
  color: #fff;
  text-decoration: none;
  border-bottom: 1px solid var(--nb-border-color);
  transition: color 0.2s;
}

.sidebar-brand:visited {
  color: #fff;
}

.sidebar-brand:hover {
  color: var(--nb-accent);
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 4px;
  border-radius: 8px;
  color: var(--nb-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 0.95rem;
}

.sidebar-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: var(--nb-text-primary);
}

.sidebar-item.active {
  background: rgba(64, 158, 255, 0.15);
  color: var(--nb-accent);
}

.sidebar-item .el-icon {
  font-size: 1.1rem;
}

/* 通知角标：只包裹图标，不占额外的布局宽度 */
.nav-badge {
  display: inline-flex;
  align-items: center;
}

.nav-badge :deep(.el-badge__content) {
  height: 15px;
  line-height: 13px;
  padding: 0 4px;
  font-size: 10px;
  border: none;
}

.sidebar-footer {
  padding: 16px 12px;
  border-top: 1px solid var(--nb-border-color);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  margin-bottom: 8px;
  color: var(--nb-text-primary);
}

.user-name {
  font-size: 0.9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-actions {
  display: flex;
  gap: 8px;
}

.sidebar-actions .sidebar-item {
  flex: 1 1 auto;
  min-width: 0;
  justify-content: center;
  white-space: nowrap;
  font-size: 0.85rem;
  padding: 10px 8px;
}

.logout {
  color: var(--nb-text-muted);
}

.logout:hover {
  color: var(--nb-danger);
  background: rgba(245, 108, 108, 0.1);
}

@media (max-width: 768px) {
  .app-sidebar {
    width: 64px;
  }

  .sidebar-brand {
    padding: 20px 0;
    text-align: center;
    font-size: 1rem;
  }

  .sidebar-item span,
  .user-name {
    display: none;
  }

  .sidebar-item {
    justify-content: center;
    padding: 12px;
  }

  .user-info {
    justify-content: center;
    padding: 8px;
  }
}
</style>
