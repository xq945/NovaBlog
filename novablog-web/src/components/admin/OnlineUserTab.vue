<script setup>
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOnlineUsers, revokeUserSessions } from '../../api/session'
import { useFormatTime } from '../../composables/useFormatTime'
import { useAdminTable } from '../../composables/useAdminTable'

const { formatTime } = useFormatTime()

const { loading, list, total, page, size, fetch } = useAdminTable(getOnlineUsers)

const handleKick = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.nickname || row.username}」的全部设备强制下线吗？该用户需要重新登录。`,
      '强制下线',
      { confirmButtonText: '下线', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await revokeUserSessions(row.userId)
    if (res.code === 200) {
      ElMessage.success(`已撤销 ${res.data.count} 个会话`)
      fetch()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

onMounted(() => {
  fetch()
})
</script>

<template>
  <div v-loading="loading" class="tab-content">
    <div class="toolbar">
      <span class="online-hint">
        当前在线 <strong>{{ total }}</strong> 人
        <span class="hint-sub">（最近 5 分钟内有请求）</span>
      </span>
      <el-button @click="fetch">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="当前没有在线用户" />

    <el-table v-else :data="list" border stripe style="width: 100%">
      <el-table-column label="用户" min-width="220">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="32" :src="row.avatar">
              {{ (row.nickname || row.username || '?').charAt(0) }}
            </el-avatar>
            <div class="user-meta">
              <span class="nickname">{{ row.nickname || row.username }}</span>
              <span class="username">@{{ row.username }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="deviceCount" label="在线设备" width="110">
        <template #default="{ row }">
          <el-tag size="small" type="success">{{ row.deviceCount }} 台</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastActive" label="最后活跃" min-width="180">
        <template #default="{ row }">{{ formatTime(row.lastActive) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" size="small" text @click="handleKick(row)">强制下线</el-button>
        </template>
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
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
}

.online-hint {
  color: var(--nb-text-secondary);
  font-size: 14px;
}

.online-hint strong {
  color: var(--nb-accent);
  font-size: 18px;
  margin: 0 2px;
}

.hint-sub {
  color: var(--nb-text-muted);
  font-size: 12px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.nickname {
  color: var(--nb-text-primary);
}

.username {
  color: var(--nb-text-muted);
  font-size: 12px;
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
