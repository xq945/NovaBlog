import axios from 'axios'
import { useUserStore } from '../stores'
import { handleApiError } from './errorHandler'
import { clearSession, isAuthError, refreshAccessToken } from './session'

// 支持通过环境变量切换 API 地址，用于内网穿透等场景
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  // RefreshToken 在 HttpOnly Cookie 里，必须允许浏览器携带凭据
  withCredentials: true
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
  clearSession()
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

    // 受保护接口的 401（过滤链返回，HTTP 401 + body.code=401）触发静默续期。
    // _retry 保证每个请求最多重试一次，避免刷新后仍 401 时形成请求风暴
    const isUnauthorized = res?.code === 401 || error.response?.status === 401
    if (isUnauthorized && !originalRequest._retry) {
      originalRequest._retry = true

      // 正在刷新中，将当前请求加入队列等待
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          addRefreshSubscriber({
            resolve: (newToken) => {
              originalRequest.headers = originalRequest.headers || {}
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
        // 刷新统一走 session.js 的单例 Promise：
        // RT 在 Cookie 里，前端不再读取或传递它
        const newToken = await refreshAccessToken()

        // 重试队列中所有等待的请求
        onTokenRefreshed(newToken)

        // 重试当前请求
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = 'Bearer ' + newToken
        return request(originalRequest)
      } catch (refreshError) {
        onTokenRefreshFailed(refreshError)
        if (isAuthError(refreshError)) {
          // 确认性失效：清状态回登录
          redirectToLogin(refreshError.message)
        } else {
          // 网络抖动或服务端故障：会话可能仍有效，不踢人，只让本次请求失败
          handleApiError(refreshError, { silent: true })
        }
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
