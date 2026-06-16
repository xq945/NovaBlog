import request from '../utils/request'

/**
 * RAG 知识库问答 API
 */

/**
 * 向博客知识库提问（同步）
 * @param {string} question - 用户问题
 */
export function askQuestion(question) {
  return request({
    url: '/rag/ask',
    method: 'post',
    data: { question }
  })
}

/**
 * 向博客知识库提问（SSE 流式）
 * @param {string} question - 用户问题
 * @param {Function} onMessage - 接收到文本片段时的回调
 * @param {Function} onSources - 接收到来源文章时的回调
 * @param {Function} onError - 发生错误时的回调
 * @param {Function} onComplete - 流结束时的回调
 * @returns {EventSource} EventSource 实例，调用方可用于关闭连接
 */
export function askStream(question, onMessage, onSources, onError, onComplete) {
  const eventSource = new EventSource(
    `/api/rag/ask/stream?question=${encodeURIComponent(question)}`
  )

  eventSource.onmessage = (event) => {
    if (onMessage) onMessage(event.data)
  }

  eventSource.addEventListener('sources', (event) => {
    try {
      const result = JSON.parse(event.data)
      if (onSources) onSources(result)
    } catch (e) {
      if (onError) onError(new Error('来源数据解析失败'))
    }
  })

  eventSource.onerror = (error) => {
    if (eventSource.readyState === EventSource.CLOSED) {
      if (onComplete) onComplete()
    } else {
      if (onError) onError(error)
    }
    eventSource.close()
  }

  return eventSource
}
