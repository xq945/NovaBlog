/**
 * 通用文件下载
 *
 * @param {string} content - 文件内容
 * @param {string} filename - 文件名
 * @param {string} [mimeType='text/markdown'] - MIME 类型
 */
export function downloadFile(content, filename, mimeType = 'text/markdown') {
  const blob = new Blob([content], { type: `${mimeType};charset=utf-8` })
  const url = URL.createObjectURL(blob)

  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()

  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * 格式化日期为文件名安全格式
 *
 * @param {Date} [date=new Date()] - 日期
 * @returns {string} 格式：YYYYMMDD_HHMM
 */
function formatDateForFilename(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}`
}

/**
 * 清理会话标题，使其适合作为文件名
 *
 * @param {string} title - 原始标题
 * @returns {string} 安全文件名
 */
function sanitizeFilename(title) {
  if (!title) return '未命名会话'
  return title
    .replace(/[\\/:*?"<>|]/g, '_')
    .trim()
    .substring(0, 50)
}

/**
 * 格式化日期时间为中文可读格式
 *
 * @param {string|Date|number} time - 时间
 * @returns {string} 格式化后的时间
 */
function formatDateTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

/**
 * 将会话导出为 Markdown 内容
 *
 * @param {Object} session - 会话对象，包含 title 等字段
 * @param {Array} messages - 消息数组，元素结构 { role, content, sources, createTime }
 * @returns {string} Markdown 文本
 */
export function exportSessionToMarkdown(session, messages) {
  const sessionTitle = session?.title || '未命名会话'
  const exportTime = formatDateTime(new Date())

  let md = `# ${sessionTitle}\n\n`
  md += `> 导出时间：${exportTime}\n`
  md += `> 消息数量：${messages.length} 条\n\n`
  md += `---\n\n`

  for (const msg of messages) {
    const time = formatDateTime(msg.createTime)

    if (msg.role === 'user') {
      md += `## 提问\n\n`
      md += `**时间**：${time}\n\n`
      md += `${msg.content}\n\n`
    } else if (msg.role === 'assistant') {
      md += `## 回答\n\n`
      md += `**时间**：${time}\n\n`
      md += `${msg.content}\n\n`

      if (msg.sources && msg.sources.length > 0) {
        md += `### 参考来源\n\n`
        for (const source of msg.sources) {
          md += `- **${source.title || '未知文章'}**\n`
          const summary = source.summary || source.snippet
          if (summary) {
            md += `  > ${summary}\n`
          }
          if (source.score !== undefined) {
            md += `  > 相关度：${(source.score * 100).toFixed(1)}%\n`
          }
          md += `\n`
        }
      }
    }

    md += `---\n\n`
  }

  md += `*由 NovaBlog AI 对话导出*\n`
  return md
}

/**
 * 触发当前会话导出为 Markdown 文件下载
 *
 * @param {Object} session - 会话对象
 * @param {Array} messages - 消息数组
 */
export function exportSession(session, messages) {
  if (!messages || messages.length === 0) {
    return
  }

  const mdContent = exportSessionToMarkdown(session, messages)
  const safeTitle = sanitizeFilename(session?.title)
  const filename = `${safeTitle}_${formatDateForFilename()}.md`

  downloadFile(mdContent, filename)
}
