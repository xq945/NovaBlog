<script setup>
import { onMounted } from 'vue'
import { useUserStore } from './stores'
import { getProfile } from './api/user'

const userStore = useUserStore()

/**
 * 页面刷新后恢复登录状态
 * 只恢复 token/refreshToken，不恢复 userInfo
 * userInfo 由有效 token 换取，避免 token 过期后仍显示登录状态
 */
onMounted(async () => {
  const token = localStorage.getItem('token')
  const refreshToken = localStorage.getItem('refreshToken')

  if (token && refreshToken) {
    userStore.token = token
    userStore.refreshToken = refreshToken

    // 调用 /user/profile 验证 token 是否有效
    // 如果 token 过期，响应拦截器会自动触发 refresh 流程
    // 如果 refresh 也失败，会清除状态并弹出登录框
    try {
      const res = await getProfile()
      if (res.code === 200) {
        userStore.setUserInfo(res.data)
      }
    } catch {
      // 请求失败由响应拦截器统一处理（自动续期或弹出登录框）
      // 这里不需要额外处理
    }
  }
})
</script>

<template>
  <router-view />
</template>
