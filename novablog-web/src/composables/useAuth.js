import { useUserStore } from '../stores'

/**
 * 认证相关组合式函数
 * @returns {Object} 认证相关方法
 */
export function useAuth() {
  const userStore = useUserStore()

  const isLoggedIn = () => {
    return !!userStore.token
  }

  const isAdmin = () => {
    return userStore.userInfo?.role === 'ADMIN'
  }

  const logout = () => {
    userStore.clearToken()
    window.location.href = '/login'
  }

  return {
    isLoggedIn,
    isAdmin,
    logout,
    token: userStore.token,
    userInfo: userStore.userInfo
  }
}
