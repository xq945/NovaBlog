import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  createSession,
  getSessionList,
  getSessionDetail,
  deleteSession,
  updateSessionTitle
} from '../api/chat'

/**
 * AI 对话状态管理
 */
export const useChatStore = defineStore('chat', () => {
  // 会话列表
  const sessions = ref([])
  const sessionsTotal = ref(0)
  const currentSessionId = ref(null)

  // 当前会话
  const currentSession = computed(() => {
    return sessions.value.find(s => s.id === currentSessionId.value) || null
  })

  // 当前会话的消息
  const messages = ref([])

  // 加载状态
  const loadingSessions = ref(false)
  const loadingMessages = ref(false)

  /**
   * 加载会话列表
   */
  const loadSessions = async (page = 1, size = 20) => {
    loadingSessions.value = true
    try {
      const res = await getSessionList(page, size)
      if (res.code === 200) {
        sessions.value = res.data.list
        sessionsTotal.value = res.data.total
      }
    } finally {
      loadingSessions.value = false
    }
  }

  /**
   * 创建新会话并选中
   */
  const createNewSession = async (title) => {
    const res = await createSession(title)
    if (res.code === 200) {
      sessions.value.unshift(res.data)
      currentSessionId.value = res.data.id
      messages.value = []
      return res.data
    }
    return null
  }

  /**
   * 切换到指定会话
   */
  const switchSession = async (sessionId) => {
    if (sessionId === currentSessionId.value) return

    currentSessionId.value = sessionId
    messages.value = []

    if (!sessionId) return

    loadingMessages.value = true
    try {
      const res = await getSessionDetail(sessionId)
      if (res.code === 200) {
        messages.value = (res.data.messages || []).map(msg => ({
          id: msg.id,
          role: msg.role,
          content: msg.content,
          sources: msg.sources || [],
          isError: false
        }))
      }
    } finally {
      loadingMessages.value = false
    }
  }

  /**
   * 删除会话
   */
  const removeSession = async (sessionId) => {
    const res = await deleteSession(sessionId)
    if (res.code === 200) {
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = null
        messages.value = []
      }
    }
  }

  /**
   * 重命名会话
   */
  const renameSession = async (sessionId, title) => {
    const res = await updateSessionTitle(sessionId, title)
    if (res.code === 200) {
      const session = sessions.value.find(s => s.id === sessionId)
      if (session) {
        session.title = title
      }
    }
  }

  /**
   * 添加消息到当前会话
   */
  const addMessage = (message) => {
    messages.value.push(message)
  }

  /**
   * 更新最后一条消息
   */
  const updateLastMessage = (updater) => {
    if (messages.value.length === 0) return
    const lastIndex = messages.value.length - 1
    const updated = updater(messages.value[lastIndex])
    if (updated) {
      messages.value[lastIndex] = updated
    }
  }

  /**
   * 更新会话消息计数
   */
  const updateSessionCount = (sessionId, count) => {
    const session = sessions.value.find(s => s.id === sessionId)
    if (session) {
      session.messageCount = count
    }
  }

  return {
    sessions,
    sessionsTotal,
    currentSessionId,
    currentSession,
    messages,
    loadingSessions,
    loadingMessages,
    loadSessions,
    createNewSession,
    switchSession,
    removeSession,
    renameSession,
    addMessage,
    updateLastMessage,
    updateSessionCount
  }
})
