import axios from 'axios'
import { useUserStore } from '../stores'
import { handleApiError } from './errorHandler'

// 支持通过环境变量切换 API 地址，用于内网穿透等场景
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000
})

// 是否正在刷新 Token
let isRefreshing = false
// 等待 Token 刷新后重试的请求队列，元素形如 { resolve, reject }
let refreshSubscribers = []

/**
 * 将请求加入等待队列，刷新完成后重试
 */
function addRefreshSubscriber(subscriber) {
  refreshSubscribers.push(subscriber)
}

/**
 * Token 刷新完成后，通知队列中所有请求重试
 */
function onTokenRefreshed(newToken) {
  refreshSubscribers.forEach(subscriber => subscriber.resolve(newToken))
  refreshSubscribers = []
}

/**
 * 刷新失败时让排队中的请求一并失败
 * 只清空队列而不 reject，会让所有等待中的 Promise 永久挂起
 */
function onTokenRefreshFailed(error) {
  refreshSubscribers.forEach(subscriber => subscriber.reject(error))
  refreshSubscribers = []
}

/**
 * 会话类失效：被踢下线（4011）或账号被禁用（4012）
 * 这两种情况刷新令牌也救不回来，直接回登录页，不必白跑一次刷新
 */
function isSessionInvalid(res) {
  return res?.code === 4011 || res?.code === 4012
}

/**
 * 清除登录状态并通知全局弹出登录
 */
function redirectToLogin(message) {
  const userStore = useUserStore()
  userStore.clearToken()
  window.dispatchEvent(new CustomEvent('show-login', {
    detail: { message: message || '登录已过期，请重新登录' }
  }))
}

/**
 * 请求拦截器：自动携带 Access Token
 */
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = 'Bearer ' + userStore.token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器：处理 Token 过期，自动续期
 */
request.interceptors.response.use(
  (response) => {
    return response.data
  },
  async (error) => {
    const originalRequest = error.config
    const res = error.response?.data

    // 会话已被撤销或账号被禁用：刷新令牌也无效，直接回登录页
    if (isSessionInvalid(res)) {
      redirectToLogin(res.message)
      return Promise.reject(error)
    }

    // 401 且不是刷新请求本身，尝试自动续期
    if (res?.code === 401 && !originalRequest._isRefreshRequest) {
      const userStore = useUserStore()

      // RefreshToken 每次刷新都会轮换，且可能已被其他标签页轮换过。
      // 内存里的 store 是本页打开时的快照，必须优先读 localStorage 取全局最新值，
      // 否则会拿着早已作废的旧 RT 去刷新，被服务端判定为令牌泄露并撤销整个会话
      const latestRefreshToken = localStorage.getItem('refreshToken') || userStore.refreshToken

      // 没有 refreshToken，直接清除登录状态
      if (!latestRefreshToken) {
        redirectToLogin(res.message)
        return Promise.reject(error)
      }

      // 正在刷新中，将当前请求加入队列等待
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          addRefreshSubscriber({
            resolve: (newToken) => {
              originalRequest.headers.Authorization = 'Bearer ' + newToken
              resolve(request(originalRequest))
            },
            reject
          })
        })
      }

      // 开始刷新流程
      isRefreshing = true

      try {
        // 调用刷新接口，携带 Refresh Token
        const refreshRes = await axios.post(`${API_BASE_URL}/auth/refresh`, null, {
          headers: { Authorization: 'Bearer ' + latestRefreshToken },
          timeout: 10000
        })

        const refreshData = refreshRes.data

        if (refreshData.code === 200) {
          const { token, refreshToken, userInfo } = refreshData.data

          // 更新 Pinia Store
          userStore.setToken(token, refreshToken)
          if (userInfo) {
            userStore.setUserInfo(userInfo)
          }

          // 重试队列中所有等待的请求
          onTokenRefreshed(token)

          // 重试当前请求
          originalRequest.headers.Authorization = 'Bearer ' + token
          return request(originalRequest)
        } else {
          // 刷新接口返回非 200，清除状态
          throw new Error(refreshData.message || '刷新失败')
        }
      } catch (refreshError) {
        // 刷新失败（包括 refreshToken 过期、网络错误等），清除登录状态并放行排队请求
        onTokenRefreshFailed(refreshError)
        redirectToLogin('登录已过期，请重新登录')
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    // 其他错误统一处理
    handleApiError(error, { silent: true })
    return Promise.reject(error)
  }
)

export default request
