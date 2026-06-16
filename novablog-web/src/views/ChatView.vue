<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore, useChatStore } from '../stores'
import { askStream, deleteMessage, editMessage } from '../api/chat'
import { copyToClipboard } from '../utils/clipboard'
import { exportSession } from '../utils/export'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

// 聊天输入与加载状态
const inputQuestion = ref('')
const loading = ref(false)
const messagesContainer = ref(null)
let currentEventSource = null

// 侧边栏状态
const sidebarCollapsed = ref(false)
const isMobile = ref(false)

// 示例问题
const exampleQuestions = [
  '博客里有哪些关于 SpringBoot 的文章？',
  '作者对 Vue3 有什么看法？',
  '总结一下博客里的技术文章'
]

// 初始化移动端检测
const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) {
    sidebarCollapsed.value = false
  }
}

// Markdown 渲染
const renderMarkdown = (content) => {
  if (!content) return ''
  const rawHtml = marked.parse(content, { breaks: true })
  return DOMPurify.sanitize(rawHtml)
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 进入页面加载会话列表
onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)

  if (userStore.isLoggedIn()) {
    chatStore.loadSessions()
  }

  nextTick(() => {
    const input = document.querySelector('.chat-input textarea')
    if (input) input.focus()
  })
})

// 创建新会话
const handleNewSession = async () => {
  if (!checkLogin()) return
  await chatStore.createNewSession()
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

// 切换会话
const handleSwitchSession = async (sessionId) => {
  await chatStore.switchSession(sessionId)
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

// 重命名会话
const handleRenameSession = async (session, event) => {
  event.stopPropagation()
  try {
    const { value } = await ElMessageBox.prompt('请输入新标题', '重命名会话', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputValue: session.title,
      inputValidator: (value) => {
        if (!value || !value.trim()) {
          return '标题不能为空'
        }
        if (value.length > 200) {
          return '标题长度不能超过200字符'
        }
        return true
      }
    })
    await chatStore.renameSession(session.id, value.trim())
    ElMessage.success('重命名成功')
  } catch {
    // 取消
  }
}

// 删除会话
const handleDeleteSession = async (session, event) => {
  event.stopPropagation()
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await chatStore.removeSession(session.id)
    ElMessage.success('已删除')
  } catch {
    // 取消
  }
}

// 删除消息
const handleDeleteMessage = async (message) => {
  if (!chatStore.currentSessionId) return
  try {
    await ElMessageBox.confirm('删除这条消息及之后的所有消息？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteMessage(chatStore.currentSessionId, message.id)
    if (res.code === 200) {
      // 前端移除该消息及之后的消息
      const index = chatStore.messages.findIndex(m => m.id === message.id)
      if (index !== -1) {
        chatStore.messages.splice(index)
      }
      await chatStore.loadSessions()
      ElMessage.success('已删除')
    }
  } catch {
    // 取消
  }
}

// 编辑消息
const handleEditMessage = async (message) => {
  if (!chatStore.currentSessionId) return
  try {
    const { value } = await ElMessageBox.prompt('修改问题', '编辑问题', {
      confirmButtonText: '重新生成',
      cancelButtonText: '取消',
      inputValue: message.content,
      inputValidator: (value) => {
        if (!value || !value.trim()) {
          return '问题不能为空'
        }
        if (value.length > 500) {
          return '问题长度不能超过500字符'
        }
        return true
      }
    })

    const newContent = value.trim()
    loading.value = true

    // 关闭之前的流
    if (currentEventSource) {
      currentEventSource.close()
      currentEventSource = null
    }

    // 前端先更新问题内容，移除该消息之后的所有消息
    const index = chatStore.messages.findIndex(m => m.id === message.id)
    if (index !== -1) {
      chatStore.messages[index].content = newContent
      chatStore.messages.splice(index + 1)
    }

    // 创建助理消息占位
    chatStore.addMessage({
      role: 'assistant',
      content: '',
      sources: []
    })
    await scrollToBottom()

    // 调用编辑重问接口（返回 SSE 流）
    currentEventSource = askStream(
      chatStore.currentSessionId,
      newContent,
      (chunk) => {
        chatStore.updateLastMessage((msg) => ({
          ...msg,
          content: msg.content + chunk
        }))
        scrollToBottom()
      },
      (result) => {
        if (result.code === 200) {
          chatStore.updateLastMessage((msg) => ({
            ...msg,
            sources: result.data || []
          }))
        }
      },
      (error) => {
        chatStore.updateLastMessage((msg) => ({
          ...msg,
          content: '服务暂时不可用，请稍后重试',
          isError: true
        }))
        loading.value = false
        scrollToBottom()
      },
      () => {
        loading.value = false
        scrollToBottom()
        chatStore.loadSessions()
      }
    )
  } catch {
    // 取消
  }
}

// 登录检查
const checkLogin = () => {
  if (!userStore.isLoggedIn()) {
    ElMessage.info('请先登录后再使用对话功能')
    router.push('/login')
    return false
  }
  return true
}

// 发送消息
const sendQuestion = async (question) => {
  if (loading.value) return
  if (!question || !question.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  if (!checkLogin()) return

  // 未选择会话时自动创建新会话
  if (!chatStore.currentSessionId) {
    const session = await chatStore.createNewSession(question)
    if (!session) {
      ElMessage.error('创建会话失败')
      return
    }
  }

  const userQuestion = question.trim()
  const currentSessionId = chatStore.currentSessionId

  // 添加用户消息
  chatStore.addMessage({
    role: 'user',
    content: userQuestion,
    sources: []
  })
  inputQuestion.value = ''
  loading.value = true
  await scrollToBottom()

  // 关闭上一条未完成的流
  if (currentEventSource) {
    currentEventSource.close()
    currentEventSource = null
  }

  // 创建助理消息占位
  chatStore.addMessage({
    role: 'assistant',
    content: '',
    sources: []
  })

  currentEventSource = askStream(
    currentSessionId,
    userQuestion,
    (chunk) => {
      chatStore.updateLastMessage((msg) => ({
        ...msg,
        content: msg.content + chunk
      }))
      scrollToBottom()
    },
    (result) => {
      if (result.code === 200) {
        chatStore.updateLastMessage((msg) => ({
          ...msg,
          sources: result.data || []
        }))
      }
    },
    (error) => {
      chatStore.updateLastMessage((msg) => ({
        ...msg,
        content: '服务暂时不可用，请稍后重试',
        isError: true
      }))
      loading.value = false
      scrollToBottom()
    },
    () => {
      loading.value = false
      scrollToBottom()
      // 刷新会话列表（更新消息数和时间）
      chatStore.loadSessions()
    }
  )
}

const handleSend = () => {
  sendQuestion(inputQuestion.value)
}

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const useExample = (question) => {
  sendQuestion(question)
}

const goToArticle = (id) => {
  router.push(`/article/${id}`)
}

// 复制单条 AI 回答
const handleCopyAnswer = (msg) => {
  if (!msg.content) {
    ElMessage.warning('回答内容为空')
    return
  }
  copyToClipboard(msg.content, '回答已复制', '复制失败，请手动复制')
}

// 导出当前会话
const handleExportSession = () => {
  if (!chatStore.currentSession || chatStore.messages.length === 0) {
    ElMessage.warning('当前会话没有消息可导出')
    return
  }
  exportSession(chatStore.currentSession, chatStore.messages)
  ElMessage.success('会话已导出为 Markdown 文件')
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// 当前是否有会话
const hasCurrentSession = computed(() => !!chatStore.currentSessionId)
const hasMessages = computed(() => chatStore.messages.length > 0)
</script>

<template>
  <div class="chat-page">
    <main>
      <div class="chat-layout">
        <!-- 左侧会话列表 -->
        <aside class="session-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-header">
          <el-button type="primary" class="new-session-btn" @click="handleNewSession">
            <el-icon><Plus /></el-icon>
            新对话
          </el-button>
        </div>

        <div v-loading="chatStore.loadingSessions" class="session-list">
          <div
            v-for="session in chatStore.sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === chatStore.currentSessionId }"
            @click="handleSwitchSession(session.id)"
          >
            <div class="session-info">
              <el-icon class="session-icon"><ChatDotRound /></el-icon>
              <span class="session-title">{{ session.title }}</span>
            </div>
            <div class="session-meta">
              <span class="session-count">{{ session.messageCount || 0 }} 条消息 · {{ formatTime(session.updateTime) }}</span>
              <div class="session-actions" @click.stop
              >
                <el-button
                  class="action-btn"
                  type="primary"
                  link
                  size="small"
                  @click="handleRenameSession(session, $event)"
                >
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button
                  class="action-btn"
                  type="danger"
                  link
                  size="small"
                  @click="handleDeleteSession(session, $event)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>

          <div v-if="chatStore.sessions.length === 0 && !chatStore.loadingSessions" class="empty-sessions">
            暂无会话，点击"新对话"开始
          </div>
        </div>
      </aside>

      <!-- 遮罩层 -->
      <div
        v-if="isMobile && !sidebarCollapsed"
        class="sidebar-overlay"
        @click="sidebarCollapsed = true"
      />

      <!-- 右侧聊天区域 -->
      <main class="chat-main">
        <!-- 移动端侧边栏切换按钮 -->
        <div v-if="isMobile" class="mobile-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon :size="20">
            <Fold v-if="!sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesContainer" class="messages-container">
          <!-- 导出栏 -->
          <div v-if="hasMessages" class="export-bar">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleExportSession"
            >
              <el-icon><Download /></el-icon>
              <span class="export-text">导出会话</span>
            </el-button>
          </div>

          <!-- 空状态 -->
          <div
            v-if="!hasCurrentSession || chatStore.messages.length === 0"
            class="empty-state"
          >
            <div class="empty-icon">
              <el-icon :size="64"><ChatDotRound /></el-icon>
            </div>
            <h3>{{ hasCurrentSession ? '开始对话吧' : '选择一个会话或创建新对话' }}</h3>
            <p>我可以基于博客中的文章为你解答问题</p>
            <div class="example-questions">
              <div
                v-for="(q, index) in exampleQuestions"
                :key="index"
                class="example-chip"
                @click="useExample(q)"
              >
                {{ q }}
              </div>
            </div>
          </div>

          <!-- 消息气泡 -->
          <template v-else>
            <div
              v-for="(msg, index) in chatStore.messages"
              :key="index"
              class="message-wrapper"
              :class="msg.role"
            >
              <div class="message-avatar">
                <el-avatar
                  :size="40"
                  :icon="msg.role === 'user' ? 'UserFilled' : 'ChatDotRound'"
                  :class="msg.role === 'user' ? 'user-avatar' : 'ai-avatar'"
                />
              </div>
              <div class="message-content">
                <div class="message-header">
                  <span class="message-time" v-if="msg.createTime">{{ formatTime(msg.createTime) }}</span>
                </div>
                <div
                  class="message-bubble"
                  :class="{ 'error-bubble': msg.isError }"
                  v-html="msg.role === 'assistant' ? renderMarkdown(msg.content) : msg.content"
                />
                <!-- AI 回答的来源文章 -->
                <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length > 0" class="source-cards">
                  <div class="source-title">参考来源</div>
                  <div class="source-list">
                    <div
                      v-for="source in msg.sources"
                      :key="source.id"
                      class="source-card"
                      @click="goToArticle(source.id)"
                    >
                      <div class="source-card-title">{{ source.title }}</div>
                      <div class="source-card-snippet">{{ source.summary || source.snippet }}</div>
                      <div class="source-card-score">相关度: {{ (source.score * 100).toFixed(1) }}%</div>
                    </div>
                  </div>
                </div>

                <!-- AI 回答操作 -->
                <div v-if="msg.role === 'assistant'" class="message-actions assistant-actions">
                  <el-button
                    type="primary"
                    link
                    size="small"
                    title="复制回答"
                    @click="handleCopyAnswer(msg)"
                  >
                    <el-icon><DocumentCopy /></el-icon> 复制
                  </el-button>
                </div>

                <!-- 用户消息操作 -->
                <div v-if="msg.role === 'user'" class="message-actions">
                  <el-button
                    type="primary"
                    link
                    size="small"
                    @click="handleEditMessage(msg)"
                  >
                    <el-icon><Edit /></el-icon> 编辑
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    @click="handleDeleteMessage(msg)"
                  >
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </div>
              </div>
            </div>

            <!-- Loading -->
            <div v-if="loading" class="message-wrapper assistant">
              <div class="message-avatar">
                <el-avatar :size="40" icon="ChatDotRound" class="ai-avatar" />
              </div>
              <div class="message-content">
                <el-skeleton :rows="2" animated />
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <el-input
            v-model="inputQuestion"
            type="textarea"
            :rows="2"
            placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行..."
            class="chat-input"
            resize="none"
            maxlength="500"
            show-word-limit
            @keydown="handleKeydown"
          />
          <el-button
            type="primary"
            :loading="loading"
            class="send-btn"
            @click="handleSend"
          >
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </main>
    </div>
  </main>
</div>
</template>

<style scoped>
.chat-page {
  min-height: 100vh;
  background: var(--bg-primary, #0f172a);
  color: #e2e8f0;
}

.chat-page > main {
  min-height: 100vh;
}

.chat-layout {
  display: flex;
  height: 100vh;
}

.session-sidebar {
  width: 300px;
  min-width: 300px;
  background: rgba(15, 23, 42, 0.95);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  z-index: 60;
  transition: transform 0.3s ease;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.new-session-btn {
  width: 100%;
  border-radius: 8px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.session-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.session-item.active {
  background: rgba(96, 165, 250, 0.15);
  border-left: 3px solid #60a5fa;
}

.session-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.session-icon {
  color: #60a5fa;
  flex-shrink: 0;
}

.session-title {
  color: #e2e8f0;
  font-size: 0.95rem;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.session-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-left: 24px;
}

.session-count {
  font-size: 0.75rem;
  color: #64748b;
}

.session-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-actions {
  opacity: 1;
}

.action-btn {
  padding: 4px;
}

.empty-sessions {
  padding: 24px;
  text-align: center;
  color: #64748b;
  font-size: 0.85rem;
}

.sidebar-overlay {
  position: fixed;
  top: 64px;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 55;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

.mobile-toggle {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 50;
  padding: 8px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  cursor: pointer;
  color: #e2e8f0;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px 48px;
  background: transparent;
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #94a3b8;
}

.empty-icon {
  color: rgba(96, 165, 250, 0.3);
  margin-bottom: 16px;
}

.empty-state h3 {
  color: #e2e8f0;
  margin: 0 0 8px;
  font-size: 1.25rem;
}

.example-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
  justify-content: center;
}

.example-chip {
  padding: 10px 18px;
  background: rgba(96, 165, 250, 0.1);
  border: 1px solid rgba(96, 165, 250, 0.3);
  border-radius: 20px;
  color: #60a5fa;
  cursor: pointer;
  transition: all 0.2s;
}

.example-chip:hover {
  background: rgba(96, 165, 250, 0.2);
}

.message-wrapper {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.message-avatar .user-avatar {
  background: #3b82f6;
}

.message-avatar .ai-avatar {
  background: #8b5cf6;
}

.message-content {
  max-width: min(85%, 1000px);
}

.message-header {
  margin-bottom: 6px;
  text-align: right;
}

.message-wrapper.assistant .message-header {
  text-align: left;
}

.message-time {
  font-size: 0.75rem;
  color: #64748b;
}

.message-bubble {
  padding: 14px 18px;
  border-radius: 16px;
  line-height: 1.7;
  word-break: break-word;
}

.message-wrapper.user .message-bubble {
  background: #3b82f6;
  color: white;
  border-bottom-right-radius: 4px;
}

.message-wrapper.assistant .message-bubble {
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
  border-bottom-left-radius: 4px;
}

.message-wrapper.assistant .message-bubble :deep(p) {
  margin: 0 0 8px;
}

.message-wrapper.assistant .message-bubble :deep(p:last-child) {
  margin-bottom: 0;
}

.message-wrapper.assistant .message-bubble :deep(code) {
  background: rgba(0, 0, 0, 0.3);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', monospace;
}

.error-bubble {
  background: rgba(239, 68, 68, 0.15) !important;
  color: #fca5a5 !important;
}

.message-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  justify-content: flex-end;
  opacity: 0;
  transition: opacity 0.2s;
}

.message-wrapper.user:hover .message-actions,
.message-wrapper.assistant:hover .assistant-actions {
  opacity: 1;
}

.assistant-actions {
  justify-content: flex-start;
}

.export-bar {
  display: flex;
  justify-content: flex-end;
  padding: 0 0 12px;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.export-bar .el-button {
  color: #94a3b8;
}

.export-bar .el-button:hover {
  color: #60a5fa;
}

.source-cards {
  margin-top: 12px;
}

.source-title {
  font-size: 0.85rem;
  color: #94a3b8;
  margin-bottom: 8px;
}

.source-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.source-card {
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.source-card:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(96, 165, 250, 0.4);
}

.source-card-title {
  font-weight: 600;
  color: #60a5fa;
  margin-bottom: 4px;
}

.source-card-snippet {
  font-size: 0.85rem;
  color: #94a3b8;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.source-card-score {
  font-size: 0.75rem;
  color: #64748b;
  margin-top: 6px;
}

.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 16px 48px;
  background: rgba(15, 23, 42, 0.95);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.chat-input {
  flex: 1;
}

.chat-input :deep(.el-textarea__inner) {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e2e8f0;
  border-radius: 12px;
  padding: 12px 16px;
}

.chat-input :deep(.el-textarea__inner::placeholder) {
  color: #64748b;
}

.chat-input :deep(.el-input__count) {
  color: #64748b;
  background: transparent;
}

.send-btn {
  height: 54px;
  padding: 0 28px;
  border-radius: 12px;
  font-size: 1rem;
}

@media (max-width: 768px) {
  .navbar {
    padding: 0 16px;
  }

  .chat-layout {
    padding: 64px 0 0 0;
  }

  .session-sidebar {
    position: fixed;
    left: 0;
    top: 64px;
    bottom: 0;
    transform: translateX(0);
  }

  .session-sidebar.collapsed {
    transform: translateX(-100%);
  }

  .messages-container {
    padding: 56px 16px 16px;
  }

  .input-area {
    padding: 12px 16px;
  }

  .message-content {
    max-width: 90%;
  }

  .example-questions {
    flex-direction: column;
    align-items: stretch;
  }

  .source-list {
    grid-template-columns: 1fr;
  }

  .session-actions {
    opacity: 1;
  }

  .message-actions {
    opacity: 1;
  }
}
</style>
