<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from './stores'
import { useNotificationStore } from './stores/notification'
import { clearSession } from './utils/session'

const userStore = useUserStore()
const notificationStore = useNotificationStore()
const router = useRouter()

// 登录态变化时启停通知流：token 刷新不会改变这里，
// 所以不会因为续期而反复重连
watch(
  () => userStore.token,
  (token) => {
    if (token) {
      notificationStore.start()
    } else {
      notificationStore.stop()
    }
  },
  { immediate: true }
)

// 多个并发请求同时失效时会重复派发 show-login，只处理第一次
let redirecting = false

/**
 * 登录态失效（令牌过期且刷新失败、被踢下线、账号被禁用）的统一处理
 */
function handleShowLogin(event) {
  if (redirecting) return
  redirecting = true

  clearSession()
  const message = event?.detail?.message
  if (message) {
    ElMessage.warning(message)
  }
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
  // 稍后解除去重，避免用户再次登录后又被踢却收不到提示
  setTimeout(() => {
    redirecting = false
  }, 1000)
}

/**
 * 登录态的恢复已经在 main.js 启动阶段完成（凭 RT Cookie 静默续期），
 * 这里只负责注册全局失效事件与主题初始化
 */
onMounted(() => {
  // 必须先注册监听：任何后续请求触发会话失效时，事件都能被接住
  window.addEventListener('show-login', handleShowLogin)

  // 全局使用暗色主题
  document.documentElement.classList.add('dark')
})

onUnmounted(() => {
  window.removeEventListener('show-login', handleShowLogin)
  notificationStore.stop()
})
</script>

<template>
  <router-view />
</template>
