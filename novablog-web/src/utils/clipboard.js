import { ElMessage } from 'element-plus'

/**
 * 复制文本到剪贴板
 * 优先使用 Clipboard API，不支持时降级到 execCommand
 *
 * @param {string} text - 要复制的文本
 * @param {string} [successMsg='已复制到剪贴板'] - 成功提示文案
 * @param {string} [errorMsg='复制失败，请手动复制'] - 失败提示文案
 * @returns {Promise<boolean>} 是否复制成功
 */
export async function copyToClipboard(
  text,
  successMsg = '已复制到剪贴板',
  errorMsg = '复制失败，请手动复制'
) {
  if (!text) {
    ElMessage.warning('内容为空')
    return false
  }

  // 优先使用现代 Clipboard API（需要 HTTPS 或 localhost）
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success(successMsg)
      return true
    } catch (err) {
      console.warn('Clipboard API 复制失败，尝试降级方案', err)
      return fallbackCopy(text, successMsg, errorMsg)
    }
  }

  return fallbackCopy(text, successMsg, errorMsg)
}

/**
 * 降级复制方案：通过临时 textarea 和 execCommand 实现
 */
function fallbackCopy(text, successMsg, errorMsg) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  textarea.setAttribute('readonly', '') // 防止 iOS 弹出键盘
  document.body.appendChild(textarea)

  const selection = document.getSelection()
  const selected = selection.rangeCount > 0 ? selection.getRangeAt(0) : null

  textarea.select()
  textarea.setSelectionRange(0, textarea.value.length) // 兼容 iOS

  let success = false
  try {
    success = document.execCommand('copy')
    if (success) {
      ElMessage.success(successMsg)
    } else {
      ElMessage.error(errorMsg)
    }
  } catch (err) {
    console.error('execCommand 复制失败', err)
    ElMessage.error(errorMsg)
  }

  document.body.removeChild(textarea)

  // 恢复之前的选区
  if (selected) {
    selection.removeAllRanges()
    selection.addRange(selected)
  }

  return success
}
