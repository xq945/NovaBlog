<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead
} from '../api/notification'
import { useNotificationStore } from '../stores/notification'
import { useFormatTime } from '../composables/useFormatTime'

const router = useRouter()
const notificationStore = useNotificationStore()
const { formatTime } = useFormatTime()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const unreadOnly = ref(false)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getNotifications({
      page: page.value,
      size: size.value,
      unreadOnly: unreadOnly.value
    })
    if (res.code === 200) {
      list.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    ElMessage.error(error.message || '加载通知失败')
  } finally {
    loading.value = false
  }
}

const switchFilter = (onlyUnread) => {
  unreadOnly.value = onlyUnread
  page.value = 1
  fetchList()
}

const handleClick = async (item) => {
  if (!item.isRead) {
    try {
      await markNotificationRead(item.id)
      item.isRead = true
      await notificationStore.refreshUnread()
    } catch {
      // 标记失败不阻断跳转
    }
  }

  if (item.articleId) {
    router.push(`/article/${item.articleId}`)
  } else if (item.type === 'FOLLOW' && item.sender?.id) {
    router.push(`/user/${item.sender.id}`)
  }
}

const handleReadAll = async () => {
  try {
    const res = await markAllNotificationsRead()
    if (res.code === 200) {
      ElMessage.success('已全部标记为已读')
      notificationStore.clearUnread()
      fetchList()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const typeIcon = (type) => ({
  LIKE: 'StarFilled',
  COMMENT: 'ChatDotRound',
  REPLY: 'ChatLineRound',
  FOLLOW: 'UserFilled',
  SYSTEM: 'Bell'
}[type] || 'Bell')

const handlePageChange = (value) => {
  page.value = value
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="notifications-page">
    <main class="page-main">
      <div class="page-header">
        <h1 class="page-title">消息通知</h1>
        <el-button size="small" @click="handleReadAll">全部已读</el-button>
      </div>

      <div class="filter-bar">
        <el-button
          size="small"
          :type="!unreadOnly ? 'primary' : 'default'"
          @click="switchFilter(false)"
        >
          全部
        </el-button>
        <el-button
          size="small"
          :type="unreadOnly ? 'primary' : 'default'"
          @click="switchFilter(true)"
        >
          未读
        </el-button>
      </div>

      <div v-loading="loading">
        <div v-if="list.length > 0" class="notification-list">
          <div
            v-for="item in list"
            :key="item.id"
            class="notification-item"
            :class="{ unread: !item.isRead }"
            @click="handleClick(item)"
          >
            <el-avatar :size="36" :src="item.sender?.avatar">
              {{ (item.sender?.nickname || '?').charAt(0).toUpperCase() }}
            </el-avatar>

            <div class="notification-body">
              <div class="notification-text">
                <strong v-if="item.sender">{{ item.sender.nickname }}</strong>
                <span>{{ item.content }}</span>
              </div>
              <span class="notification-time">{{ formatTime(item.createTime) }}</span>
            </div>

            <el-icon class="notification-icon">
              <component :is="typeIcon(item.type)" />
            </el-icon>
            <span v-if="!item.isRead" class="unread-dot"></span>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="暂无通知" />
      </div>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.notifications-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

.page-main {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  color: var(--nb-text-primary);
  font-size: 1.5rem;
  font-weight: 600;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notification-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.notification-item:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.18);
}

.notification-item.unread {
  background: rgba(64, 158, 255, 0.08);
  border-color: rgba(64, 158, 255, 0.25);
}

.notification-body {
  flex: 1;
  min-width: 0;
}

.notification-text {
  color: var(--nb-text-secondary);
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.notification-text strong {
  color: var(--nb-text-primary);
  margin-right: 6px;
}

.notification-time {
  display: block;
  margin-top: 6px;
  color: var(--nb-text-muted);
  font-size: 12px;
}

.notification-icon {
  color: var(--nb-text-muted);
  font-size: 18px;
}

.unread-dot {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--nb-danger);
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
