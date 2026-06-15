<script setup>
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminUserList, updateUserStatus } from '../../api/user'
import { useFormatTime } from '../../composables/useFormatTime'
import { useAdminTable } from '../../composables/useAdminTable'

const { formatTime } = useFormatTime()
const { loading, list, total, page, size, fetch } = useAdminTable((params) =>
  getAdminUserList({ page: params.page, size: params.size })
)

const getRoleLabel = (role) => (role === 'ADMIN' ? '管理员' : '普通用户')
const getRoleType = (role) => (role === 'ADMIN' ? 'danger' : 'info')

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
    <el-table :data="list" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
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
      <el-table-column prop="createTime" label="注册时间" width="160">
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-pagination .el-pager li.is-active) {
  color: #fff;
}
</style>
