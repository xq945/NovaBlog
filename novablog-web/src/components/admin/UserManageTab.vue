<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUserList, updateUserStatus, batchDeleteUsers } from '../../api/user'
import { useFormatTime } from '../../composables/useFormatTime'
import { useAdminTable } from '../../composables/useAdminTable'

const { formatTime } = useFormatTime()
const keyword = ref('')
const selectedUsers = ref([])
const tableRef = ref(null)

const { loading, list, total, page, size, fetch } = useAdminTable((params) =>
  getAdminUserList({ page: params.page, size: params.size, keyword: keyword.value || undefined })
)

const getRoleLabel = (role) => (role === 'ADMIN' ? '管理员' : '普通用户')
const getRoleType = (role) => (role === 'ADMIN' ? 'danger' : 'info')

const handleSearch = () => {
  page.value = 1
  fetch()
}

const handleSelectionChange = (selection) => {
  selectedUsers.value = selection
}

const handleBatchDelete = async () => {
  if (selectedUsers.value.length === 0) {
    ElMessage.warning('请先选择要删除的用户')
    return
  }

  // 统计可删除用户（前端也做一层过滤，提升提示准确性）
  const deletable = selectedUsers.value.filter((user) => user.role !== 'ADMIN')
  if (deletable.length === 0) {
    ElMessage.warning('选中的用户均为管理员，无法删除')
    return
  }

  const userIds = deletable.map((user) => user.id)
  const adminCount = selectedUsers.value.length - deletable.length

  let confirmText = `确定删除选中的 ${deletable.length} 个用户吗？`
  if (adminCount > 0) {
    confirmText += `（${adminCount} 个管理员将自动跳过）`
  }
  confirmText += '\n存在文章或评论的用户会被自动跳过。'

  try {
    await ElMessageBox.confirm(confirmText, '批量删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await batchDeleteUsers(userIds)
    if (res.code === 200) {
      const { deletedCount, skippedCount, skippedReason } = res.data
      ElMessage.success(`成功删除 ${deletedCount} 个用户`)
      if (skippedCount > 0 && skippedReason) {
        ElMessage.warning(`${skippedCount} 个用户未删除：${skippedReason}`)
      }
      selectedUsers.value = []
      tableRef.value?.clearSelection()
      fetch()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除失败')
    }
  }
}

const handleStatusChange = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    const res = await updateUserStatus({ userId: row.id, status: newStatus })
    if (res.code === 200) {
      row.status = newStatus
      ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

onMounted(() => {
  fetch()
})
</script>

<template>
  <div v-loading="loading" class="tab-content">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名/昵称/邮箱..."
        clearable
        style="width: 300px"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon> 查询
      </el-button>
      <el-button
        type="danger"
        :disabled="selectedUsers.length === 0"
        @click="handleBatchDelete"
      >
        <el-icon><Delete /></el-icon> 批量删除
      </el-button>
    </div>

    <el-table
      ref="tableRef"
      :data="list"
      border
      stripe
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="getRoleType(row.role)" size="small">{{ getRoleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            @change="handleStatusChange(row)"
            active-text="启用"
            inactive-text="禁用"
            inline-prompt
            :disabled="row.role === 'ADMIN'"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetch"
        @size-change="fetch"
      />
    </div>
  </div>
</template>

<style scoped>
.tab-content {
  padding: 10px 0;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-pagination .el-pager li.is-active) {
  color: #fff;
}
</style>
