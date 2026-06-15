import { ElMessage } from 'element-plus'

/**
 * 统一 API 错误处理
 * @param {Error} error - Axios 错误对象
 * @param {Object} options - 配置项
 * @param {string} [options.defaultMessage] - 默认错误提示
 * @param {boolean} [options.silent] - 是否静默处理（不显示 ElMessage）
 * @returns {string} 错误信息
 */
export function handleApiError(error, options = {}) {
  const { defaultMessage = '操作失败', silent = false } = options

  let message = defaultMessage

  if (error?.response?.data?.message) {
    message = error.response.data.message
  } else if (error?.response?.data?.msg) {
    message = error.response.data.msg
  } else if (error?.message) {
    message = error.message
  }

  if (!silent) {
    ElMessage.error(message)
  }

  return message
}

/**
 * 统一响应结果处理
 * @param {Object} res - API 返回结果
 * @param {Object} options - 配置项
 * @param {string} [options.successMessage] - 成功提示
 * @param {boolean} [options.silent] - 是否静默处理
 * @returns {boolean} 是否成功
 */
export function handleApiResponse(res, options = {}) {
  const { successMessage, silent = false } = options

  if (res?.code === 200) {
    if (successMessage && !silent) {
      ElMessage.success(successMessage)
    }
    return true
  }

  if (!silent) {
    ElMessage.error(res?.message || '操作失败')
  }
  return false
}
