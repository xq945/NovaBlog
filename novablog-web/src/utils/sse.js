/**
 * Server-Sent Events 流解析
 *
 * 浏览器原生的 EventSource 不能自定义请求头，而令牌放在 URL 上会进入
 * 网关日志、浏览器历史和 Referer。这里统一用 fetch + ReadableStream，
 * 让令牌走 Authorization 头，解析逻辑供通知流与聊天流共用。
 */

/**
 * 逐个消费 SSE 事件
 *
 * @param {ReadableStream<Uint8Array>} body fetch 响应的 body
 * @param {(event: string, data: string) => void} onEvent 事件名与已按规范拼接的 data
 */
export async function consumeSseStream(body, onEvent) {
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
      handleChunk(buffer.slice(0, boundary), onEvent)
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
  }
}

/**
 * 解析单个事件块。
 * 以冒号开头的是注释（服务端心跳发 ":ping"），直接忽略
 */
function handleChunk(chunk, onEvent) {
  if (!chunk) return

  let event = 'message'
  const dataLines = []
  for (const line of chunk.split('\n')) {
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (dataLines.length === 0) return
  // SSE 规范里多行 data 用换行拼接
  onEvent(event, dataLines.join('\n'))
}
