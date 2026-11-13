import request from '../utils/request'
import { useUserStore } from '../stores'
import { isAuthError, refreshAccessToken } from '../utils/session'
import { consumeSseStream } from '../utils/sse'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * 创建新会话
 * @param {string} title - 会话标题（可选）
 */
export function createSession(title) {
  return request({
    url: '/chat/session',
    method: 'post',
    data: { title }
  })
}

/**
 * 获取会话列表
 * @param {number} page - 页码
 * @param {number} size - 每页数量
 */
export function getSessionList(page = 1, size = 20) {
  return request({
    url: '/chat/session/list',
    method: 'get',
    params: { page, size }
  })
}

/**
 * 获取会话详情（含消息）
 * @param {number} sessionId - 会话ID
 */
export function getSessionDetail(sessionId) {
  return request({
    url: `/chat/session/${sessionId}`,
    method: 'get'
  })
}

/**
 * 更新会话标题
 * @param {number} sessionId - 会话ID
 * @param {string} title - 新标题
 */
export function updateSessionTitle(sessionId, title) {
  return request({
    url: `/chat/session/${sessionId}/title`,
    method: 'put',
    data: { title }
  })
}

/**
 * 删除会话
 * @param {number} sessionId - 会话ID
 */
export function deleteSession(sessionId) {
  return request({
    url: `/chat/session/${sessionId}`,
    method: 'delete'
  })
}

/**
 * 删除消息（删除该消息及之后的所有消息）
 * @param {number} sessionId - 会话ID
 * @param {number} messageId - 消息ID
 */
export function deleteMessage(sessionId, messageId) {
  return request({
    url: `/chat/session/${sessionId}/message/${messageId}`,
    method: 'delete'
  })
}

/**
 * 编辑用户问题并重新生成回答
 * @param {number} sessionId - 会话ID
 * @param {number} messageId - 消息ID
 * @param {string} content - 新问题内容
 */
export function editMessage(sessionId, messageId, content) {
  return request({
    url: `/chat/session/${sessionId}/message/${messageId}`,
    method: 'put',
    data: { content }
  })
}

/**
 * 同步问答（带会话记录）
 * @param {number} sessionId - 会话ID（可选）
 * @param {string} question - 问题
 */
export function askQuestion(sessionId, question) {
  return request({
    url: '/chat/ask',
    method: 'post',
    data: { sessionId, question }
  })
}

/**
 * 流式问答（SSE，带会话记录）
 *
 * 用 fetch + ReadableStream 而不是 EventSource：
 * EventSource 不能设置请求头，只能把 AccessToken 拼在 URL 上，
 * 而 URL 会进入网关日志、浏览器历史与 Referer。这里令牌走 Authorization 头。
 *
 * @param {number} sessionId - 会话ID（可选）
 * @param {string} question - 问题
 * @param {Function} onMessage - 接收到文本片段时的回调
 * @param {Function} onSources - 接收到来源文章时的回调
 * @param {Function} onError - 发生错误时的回调
 * @param {Function} onComplete - 流结束时的回调
 * @returns {{ close: () => void }} 可中止本次流的句柄
 */
export function askStream(sessionId, question, onMessage, onSources, onError, onComplete) {
  const userStore = useUserStore()
  const controller = new AbortController()
  let closed = false

  let url = `${API_BASE_URL}/chat/ask/stream?question=${encodeURIComponent(question)}`
  if (sessionId) {
    url += `&sessionId=${sessionId}`
  }

  const open = (token) => fetch(url, {
    headers: {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token}`
    },
    cache: 'no-store',
    signal: controller.signal
  })

  const run = async () => {
    try {
      let token = userStore.token
      if (!token) {
        throw new Error('请先登录')
      }

      let res = await open(token)
      // AT 过期是长连接的常态：续期一次再重试，失败才当作真正的错误
      if (res.status === 401) {
        token = await refreshAccessToken()
        res = await open(token)
      }
      if (!res.ok || !res.body) {
        throw new Error(`问答流连接失败: ${res.status}`)
      }

      await consumeSseStream(res.body, (event, data) => {
        if (event === 'sources') {
          try {
            const result = JSON.parse(data)
            if (onSources) onSources(result)
          } catch {
            if (onError) onError(new Error('来源数据解析失败'))
          }
          return
        }
        if (event === 'message' && onMessage) {
          onMessage(data)
        }
      })

      if (!closed && onComplete) {
        onComplete()
      }
    } catch (error) {
      // 主动关闭（切换会话、重新提问）不算错误
      if (closed || error.name === 'AbortError') {
        return
      }
      // 流式请求不经过 axios 拦截器，会话失效时要自己接上全局登录流程
      if (isAuthError(error)) {
        window.dispatchEvent(new CustomEvent('show-login', {
          detail: { message: error.message }
        }))
        return
      }
      if (onError) onError(error)
    }
  }

  run()

  return {
    close() {
      closed = true
      controller.abort()
    }
  }
}
