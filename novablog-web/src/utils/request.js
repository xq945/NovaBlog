import axios from 'axios'
import { useUserStore } from '../stores'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 是否正在刷新 Token
let isRefreshing = false
// 等待 Token 刷新后重试的请求队列
let refreshSubscribers = []

/**
 * 将请求加入等待队列，刷新完成后重试
 */
function addRefreshSubscriber(callback) {
  refreshSubscribers.push(callback)
}

/**
 * Token 刷新完成后，通知队列中所有请求重试
 */
function onTokenRefreshed(newToken) {
  refreshSubscribers.forEach(callback => callback(newToken))
  refreshSubscribers = []
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

    // 401 且不是刷新请求本身，尝试自动续期
    if (res?.code === 401 && !originalRequest._isRefreshRequest) {
      const userStore = useUserStore()

      // 没有 refreshToken，直接清除登录状态
      if (!userStore.refreshToken) {
        userStore.clearToken()
        window.dispatchEvent(new CustomEvent('show-login', {
          detail: { message: res.message || '登录已过期，请重新登录' }
        }))
        return Promise.reject(error)
      }

      // 正在刷新中，将当前请求加入队列等待
      if (isRefreshing) {
        return new Promise((resolve) => {
          addRefreshSubscriber((newToken) => {
            originalRequest.headers.Authorization = 'Bearer ' + newToken
            resolve(request(originalRequest))
          })
        })
      }

      // 开始刷新流程
      isRefreshing = true

      try {
        // 调用刷新接口，携带 Refresh Token
        const refreshRes = await axios.post('/api/auth/refresh', null, {
          headers: { Authorization: 'Bearer ' + userStore.refreshToken }
        })

        const refreshData = refreshRes.data

        if (refreshData.code === 200) {
          const { token, refreshToken, userInfo } = refreshData.data

          // 更新 Pinia Store
          userStore.setToken(token, refreshToken)
          userStore.setUserInfo(userInfo)

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
        // 刷新失败（包括 refreshToken 过期、网络错误等），清除登录状态
        userStore.clearToken()
        window.dispatchEvent(new CustomEvent('show-login', {
          detail: { message: '登录已过期，请重新登录' }
        }))
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

export default request
