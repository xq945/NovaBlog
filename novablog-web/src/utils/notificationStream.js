/**
 * 通知 SSE 客户端
 *
 * 浏览器原生的 EventSource 不能自定义请求头，只能把 token 拼在 URL 上，
 * 而 Token 出现在 URL 里会被网关日志、Referer 一并带出去。
 * 这里改用 fetch + ReadableStream 手动解析 SSE，token 走 Authorization 头。
 *
 * AccessToken 只在内存里、有效期短，长连接期间过期是常态：
 * 收到 401 时先静默续期一次再重连，而不是就此停掉
 */

import { useUserStore } from '../stores'
import { isAuthError, refreshAccessToken } from './session'
import { consumeSseStream } from './sse'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const MAX_RETRY_DELAY = 30000
const INITIAL_RETRY_DELAY = 1000

let controller = null
let retryTimer = null
let retryDelay = INITIAL_RETRY_DELAY
let stopped = true
let handlers = { onNotification: null, onOpen: null, onClose: null, onFatal: null }

/**
 * 建立通知流。重复调用是幂等的
 */
export function connectNotificationStream(options = {}) {
  handlers = { ...handlers, ...options }
  if (!stopped) return
  stopped = false
  retryDelay = INITIAL_RETRY_DELAY
  open()
}

/**
 * 断开通知流。登出或组件卸载时必须调用
 */
export function disconnectNotificationStream() {
  stopped = true
  clearTimeout(retryTimer)
  retryTimer = null
  if (controller) {
    controller.abort()
    controller = null
  }
}

/**
 * 发起一次流请求。
 * abort 后 fetch 会抛 AbortError，由调用方识别并静默退出
 */
function requestStream(token, signal) {
  return fetch(`${API_BASE_URL}/notification/stream`, {
    headers: {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token}`
    },
    cache: 'no-store',
    signal
  })
}

async function open() {
  if (stopped) return

  const userStore = useUserStore()
  let token = userStore.token
  if (!token) {
    scheduleReconnect()
    return
  }

  controller = new AbortController()
  try {
    let res = await requestStream(token, controller.signal)

    // AT 过期：静默续期一次再重连
    if (res.status === 401) {
      try {
        token = await refreshAccessToken()
      } catch (error) {
        if (isAuthError(error)) {
          // 确认性失效：停止重连并交给全局登录流程
          stopped = true
          handlers.onFatal?.()
          window.dispatchEvent(new CustomEvent('show-login', {
            detail: { message: error.message }
          }))
        } else {
          // 网络抖动等暂时性失败：继续按退避重连，不把用户踢下线
          scheduleReconnect()
        }
        return
      }
      res = await requestStream(token, controller.signal)
      if (res.status === 401) {
        stopped = true
        handlers.onFatal?.()
        return
      }
    }

    if (!res.ok || !res.body) {
      throw new Error(`通知流连接失败: ${res.status}`)
    }

    retryDelay = INITIAL_RETRY_DELAY
    handlers.onOpen?.()
    await consumeSseStream(res.body, (event, data) => {
      if (event !== 'notification') return
      try {
        handlers.onNotification?.(JSON.parse(data))
      } catch (error) {
        // 单条事件解析失败不影响整条连接
        console.warn('通知事件解析失败', error)
      }
    })
    // 服务端正常结束（重启、超时）也要重连
    scheduleReconnect()
  } catch (error) {
    if (error.name === 'AbortError') return
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  if (stopped) return
  clearTimeout(retryTimer)
  // 指数退避 + 抖动，避免服务端重启时所有客户端同时重连
  const delay = retryDelay + retryDelay * 0.3 * Math.random()
  retryTimer = setTimeout(open, delay)
  retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY)
}
