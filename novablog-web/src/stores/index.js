import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 用户状态管理
 *
 * AccessToken 只保存在内存中，不落 localStorage：
 * XSS 得手也只能拿到当前页面这一次的短命令牌，RefreshToken 则在 HttpOnly Cookie 里，
 * 脚本读不到。页面刷新后由 Cookie 静默续期重新换取 AT（见 utils/session.js）。
 */
export const useUserStore = defineStore('user', () => {
  // AccessToken：纯内存，随页面生命周期存在
  const token = ref('')

  // 用户信息（同样只在内存中维护）
  // 避免 token 过期后 userInfo 仍存在，导致页面错误显示登录状态
  const userInfo = ref(null)

  /**
   * 设置 AccessToken
   */
  const setToken = (newToken) => {
    token.value = newToken || ''
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
    userInfo.value = null
  }

  /**
   * 是否已登录
   */
  const isLoggedIn = () => {
    return !!token.value
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    clearToken,
    isLoggedIn
  }
})

export { useChatStore } from './chat'
