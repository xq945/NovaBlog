import axios from 'axios'
import { useUserStore } from '../stores'

/**
 * 会话恢复与续期
 *
 * RefreshToken 存在 HttpOnly Cookie 里，前端读不到也删不掉，
 * 因此「当前浏览器有没有登录态」只能靠一个持久标记来近似判断，
 * 真正的判定永远以服务端 /auth/refresh 的响应为准。
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * 会话提示标记：登录成功时置位，确认性失效时清除。
 * 它的唯一作用是减少未登录用户启动时那次必然失败的刷新请求
 */
const SESSION_HINT_KEY = 'nb_has_session'

/**
 * 全局唯一的刷新 Promise。
 * 路由守卫、启动恢复、401 重试可能同时触发，必须收敛成一次网络请求
 */
let refreshPromise = null

export function hasSessionHint() {
  return localStorage.getItem(SESSION_HINT_KEY) === '1'
}

export function setSessionHint(on) {
  if (on) {
    localStorage.setItem(SESSION_HINT_KEY, '1')
  } else {
    localStorage.removeItem(SESSION_HINT_KEY)
  }
}

/**
 * 清除本地的登录态。
 *
 * 注意：HttpOnly Cookie 无法从前端删除，只能由服务端 Set-Cookie 清除；
 * 这里同步清掉提示标记，避免下次启动无效地尝试恢复
 */
export function clearSession() {
  const userStore = useUserStore()
  userStore.clearToken()
  setSessionHint(false)
}

/**
 * 是否为「确认性认证失败」。
 *
 * 只有确认失效才允许清登录态；网络错误、5xx 属于暂时性故障，
 * 据此踢人会让后端一次抖动演变成全员下线
 */
export function isAuthError(error) {
  return error?.isAuthError === true
}

function createAuthError(message, cause) {
  const error = new Error(message || '登录已失效，请重新登录')
  error.isAuthError = true
  if (cause) {
    error.cause = cause
  }
  return error
}

/**
 * 用 Cookie 中的 RefreshToken 换发新的 AccessToken。
 *
 * 返回本浏览器内唯一的 in-flight Promise，并发调用只会发出一次请求。
 * 失败时区分两类：
 * - 401/403 或业务 code 401/4011/4012：确认失效，清除本地状态并抛出 isAuthError
 * - 网络错误、5xx：保留登录态，原样抛出，交给调用方提示
 */
export function refreshAccessToken() {
  if (refreshPromise) {
    return refreshPromise
  }
  refreshPromise = doRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

async function doRefresh() {
  const userStore = useUserStore()

  let res
  try {
    // 使用裸 axios 而不是主实例：主实例的 401 拦截器会再次触发刷新，形成递归
    res = await axios.post(`${API_BASE_URL}/auth/refresh`, null, {
      withCredentials: true,
      timeout: 10000
    })
  } catch (error) {
    const status = error.response?.status
    if (status === 401 || status === 403) {
      clearSession()
      throw createAuthError('登录已过期，请重新登录', error)
    }
    throw error
  }

  const body = res.data
  if (body?.code === 200 && body.data?.token) {
    userStore.setToken(body.data.token)
    if (body.data.userInfo) {
      userStore.setUserInfo(body.data.userInfo)
    }
    setSessionHint(true)
    return body.data.token
  }

  // 业务 401 与过滤器链的 4011/4012 都属于确认性失效
  if (body?.code === 401 || body?.code === 4011 || body?.code === 4012) {
    clearSession()
    throw createAuthError(body.message)
  }

  throw new Error(body?.message || '刷新登录状态失败')
}

/**
 * 恢复登录态：内存里已有 AT 直接成功，否则尝试静默续期。
 *
 * @param {boolean} force 忽略会话提示标记，直接向服务端确认（路由守卫用）
 * @returns {Promise<boolean>} 是否拥有可用会话
 */
export async function restoreSession({ force = false } = {}) {
  const userStore = useUserStore()
  if (userStore.token) {
    return true
  }
  if (!force && !hasSessionHint()) {
    return false
  }
  try {
    await refreshAccessToken()
    return true
  } catch {
    return false
  }
}
