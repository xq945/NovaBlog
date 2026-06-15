import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 管理后台表格通用逻辑组合式函数
 * @param {Function} fetchFn - 加载列表的 API 方法
 * @param {Object} options - 配置项
 * @param {Function} [options.deleteFn] - 删除 API 方法
 * @param {string} [options.deleteMessage] - 删除确认提示
 * @returns {Object} 表格状态和操作方法
 */
export function useAdminTable(fetchFn, options = {}) {
  const { deleteFn, deleteMessage = '确定要删除吗？' } = options

  const loading = ref(false)
  const list = ref([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(10)

  const fetch = async (extraParams = {}) => {
    loading.value = true
    try {
      const res = await fetchFn({
        page: page.value,
        size: size.value,
        ...extraParams
      })
      if (res.code === 200) {
        list.value = res.data.list
        total.value = res.data.total
      }
    } catch (error) {
      ElMessage.error(error.message || '加载失败')
    } finally {
      loading.value = false
    }
  }

  const handleDelete = async (row, message = deleteMessage) => {
    if (!deleteFn) return
    try {
      await ElMessageBox.confirm(message, '确认', { type: 'warning' })
      const res = await deleteFn(row.id)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        await fetch()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error(error.message || '删除失败')
      }
    }
  }

  const handlePageChange = () => {
    fetch()
  }

  return {
    loading,
    list,
    total,
    page,
    size,
    fetch,
    handleDelete,
    handlePageChange
  }
}
