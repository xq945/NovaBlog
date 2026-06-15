import { computed } from 'vue'

/**
 * 统一时间格式化组合式函数
 * @returns {Object} formatTime 方法
 */
export function useFormatTime() {
  const formatTime = (time) => {
    if (!time) return ''
    const date = new Date(time)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  return {
    formatTime: computed(() => formatTime).value
  }
}
