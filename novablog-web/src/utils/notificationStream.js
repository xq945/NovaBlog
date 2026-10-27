/**
 * 通知 SSE 客户端
 *
 * 浏览器原生的 EventSource 不能自定义请求头，只能把 token 拼在 URL 上，
 * 而 Token 出现在 URL 里会被网关日志、Referer 一并带出去。
 * 这里改用 fetch + ReadableStream 手动解析 SSE，token 走 Authorization 头。
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
const MAX_RETRY_DELAY = 30000
const INITIAL_RETRY_DELAY = 1000

let controller = null
let retryTimer = null
let retryDelay = INITIAL_RETRY_DELAY
let stopped = true
let handlers = { onNotification: null, onOpen: null, onClose: null }

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

async function open() {
  if (stopped) return

  const token = localStorage.getItem('token')
  if (!token) {
    scheduleReconnect()
    return
  }

  controller = new AbortController()
  try {
    const res = await fetch(`${API_BASE_URL}/notification/stream`, {
      headers: {
        Accept: 'text/event-stream',
        Authorization: `Bearer ${token}`
      },
      cache: 'no-store',
      signal: controller.signal
    })

    // 401 说明登录态已经失效，交给请求拦截器/登录流程处理，不再重试
    if (res.status === 401) {
      stopped = true
      return
    }
    if (!res.ok || !res.body) {
      throw new Error(`通知流连接失败: ${res.status}`)
    }

    retryDelay = INITIAL_RETRY_DELAY
    handlers.onOpen?.()
    await consume(res.body)
    // 服务端正常结束（重启、超时）也要重连
    scheduleReconnect()
  } catch (error) {
    if (error.name === 'AbortError') return
    scheduleReconnect()
  }
}

async function consume(body) {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    // 不同实现可能发 \r\n，统一成 \n 再按空行切事件
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      handleEvent(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
  }
  handlers.onClose?.()
}

function handleEvent(chunk) {
  if (!chunk) return

  let event = 'message'
  const dataLines = []
  for (const line of chunk.split('\n')) {
    // 以冒号开头的是注释，服务端心跳会发 ":ping"
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (event !== 'notification' || dataLines.length === 0) return

  try {
    // SSE 规范里多行 data 用换行拼接
    handlers.onNotification?.(JSON.parse(dataLines.join('\n')))
  } catch (error) {
    // 单条事件解析失败不影响整条连接
    console.warn('通知事件解析失败', error)
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
