import request from '../utils/request'

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
 * @param {number} sessionId - 会话ID（可选）
 * @param {string} question - 问题
 * @param {Function} onMessage - 接收到文本片段时的回调
 * @param {Function} onSources - 接收到来源文章时的回调
 * @param {Function} onError - 发生错误时的回调
 * @param {Function} onComplete - 流结束时的回调
 * @returns {EventSource} EventSource 实例
 */
export function askStream(sessionId, question, onMessage, onSources, onError, onComplete) {
  const token = localStorage.getItem('token') || ''
  let url = `/api/chat/ask/stream?question=${encodeURIComponent(question)}`
  if (sessionId) {
    url += `&sessionId=${sessionId}`
  }
  if (token) {
    url += `&token=${encodeURIComponent(token)}`
  }

  const eventSource = new EventSource(url)
  let hasReceivedData = false

  eventSource.onmessage = (event) => {
    hasReceivedData = true
    if (onMessage) onMessage(event.data)
  }

  eventSource.addEventListener('sources', (event) => {
    hasReceivedData = true
    try {
      const result = JSON.parse(event.data)
      if (onSources) onSources(result)
    } catch (e) {
      if (onError) onError(new Error('来源数据解析失败'))
    }
  })

  eventSource.onerror = (error) => {
    // EventSource 在数据流结束后触发 error 时 readyState 可能为 CONNECTING
    // 只要已经接收过数据，就视为正常结束
    if (eventSource.readyState === EventSource.CLOSED || hasReceivedData) {
      if (onComplete) onComplete()
    } else {
      if (onError) onError(error)
    }
    eventSource.close()
  }

  return eventSource
}
