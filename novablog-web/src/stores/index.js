import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 用户状态管理
 * 管理登录状态、Token、用户信息
 */
export const useUserStore = defineStore('user', () => {
  // Token
  const token = ref(localStorage.getItem('token') || '')
  const refreshToken = ref(localStorage.getItem('refreshToken') || '')

  // 用户信息（仅在内存中维护，不持久化到 localStorage）
  // 避免 token 过期后 userInfo 仍存在，导致页面错误显示登录状态
  const userInfo = ref(null)

  /**
   * 设置 Token
   */
  const setToken = (newToken, newRefreshToken) => {
    token.value = newToken
    refreshToken.value = newRefreshToken
    localStorage.setItem('token', newToken)
    localStorage.setItem('refreshToken', newRefreshToken)
  }

  /**
   * 设置用户信息
   */
  const setUserInfo = (info) => {
    userInfo.value = info
  }

  /**
   * 清除登录状态
   */
  const clearToken = () => {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
  }

  /**
   * 是否已登录
   */
  const isLoggedIn = () => {
    return !!token.value
  }

  return {
    token,
    refreshToken,
    userInfo,
    setToken,
    setUserInfo,
    clearToken,
    isLoggedIn
  }
})

export { useChatStore } from './chat'
