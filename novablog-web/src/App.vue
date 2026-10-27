<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from './stores'
import { useNotificationStore } from './stores/notification'
import { getProfile } from './api/user'

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

  userStore.clearToken()
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
 * 页面刷新后恢复登录状态
 * 只恢复 token/refreshToken，不恢复 userInfo
 * userInfo 由有效 token 换取，避免 token 过期后仍显示登录状态
 */
onMounted(async () => {
  // 必须先注册监听：下面的 getProfile 失败时拦截器会立刻派发 show-login
  window.addEventListener('show-login', handleShowLogin)

  // 全局使用暗色主题
  document.documentElement.classList.add('dark')

  const token = localStorage.getItem('token')
  const refreshToken = localStorage.getItem('refreshToken')

  if (token && refreshToken) {
    userStore.token = token
    userStore.refreshToken = refreshToken

    // 调用 /user/profile 验证 token 是否有效
    // 如果 token 过期，响应拦截器会自动触发 refresh 流程
    // 如果 refresh 也失败，会清除状态并跳转登录页
    try {
      const res = await getProfile()
      if (res.code === 200) {
        userStore.setUserInfo(res.data)
      }
    } catch {
      // 请求失败由响应拦截器统一处理（自动续期或跳转登录页）
      // 这里不需要额外处理
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('show-login', handleShowLogin)
  notificationStore.stop()
})
</script>

<template>
  <router-view />
</template>
