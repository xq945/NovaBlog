import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUnreadCount } from '../api/notification'
import {
  connectNotificationStream,
  disconnectNotificationStream
} from '../utils/notificationStream'

/**
 * 通知状态
 *
 * 未读数以服务端为准：SSE 推来新通知时重新拉一次，而不是本地 +1，
 * 这样多标签页、断线重连、重复事件都不会把角标算花。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const connected = ref(false)

  let pollTimer = null

  const refreshUnread = async () => {
    try {
      const res = await getUnreadCount()
      if (res.code === 200) {
        unreadCount.value = res.data?.count || 0
      }
    } catch {
      // 未登录或网络异常时保持原值，不打断用户
    }
  }

  const clearUnread = () => {
    unreadCount.value = 0
  }

  const start = () => {
    if (connected.value) return
    connected.value = true

    refreshUnread()
    connectNotificationStream({
      onNotification: (payload) => {
        refreshUnread()
        const who = payload?.sender?.nickname
        ElMessage.info(who ? `${who} ${payload.content}` : '收到一条新通知')
      }
    })

    // SSE 不可用时的兜底：每分钟拉一次未读数
    pollTimer = setInterval(refreshUnread, 60000)
  }

  const stop = () => {
    connected.value = false
    disconnectNotificationStream()
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    unreadCount.value = 0
  }

  return {
    unreadCount,
    connected,
    refreshUnread,
    clearUnread,
    start,
    stop
  }
})
