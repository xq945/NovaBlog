<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getArticleDetail, likeArticle, unlikeArticle, getLikeStatus } from '../api/article'
import { publishComment, getCommentList, deleteComment } from '../api/comment'
import { useUserStore } from '../stores'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ========== 文章详情 ==========
const article = ref(null)
const loading = ref(false)

const fetchArticle = async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('文章ID不能为空')
    return
  }

  loading.value = true
  try {
    const res = await getArticleDetail(id)
    if (res.code === 200) {
      article.value = res.data
    } else {
      ElMessage.error(res.message || '文章不存在')
      router.push('/')
    }
  } catch (error) {
    ElMessage.error('加载文章失败')
    router.push('/')
  } finally {
    loading.value = false
  }
}

const renderMarkdown = (content) => {
  if (!content) return ''
  const rawHtml = marked.parse(content, { breaks: true })
  return DOMPurify.sanitize(rawHtml)
}

const goBack = () => {
  router.push('/')
}

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

// ========== 点赞区域 ==========
const liked = ref(false)
const likeCount = ref(0)
const likeLoading = ref(false)

const fetchLikeStatus = async () => {
  if (!userStore.userInfo) return
  const articleId = route.params.id
  if (!articleId) return

  try {
    const res = await getLikeStatus(articleId)
    if (res.code === 200) {
      liked.value = res.data.liked
      likeCount.value = res.data.likeCount
    }
  } catch (error) {
    console.error('获取点赞状态失败', error)
  }
}

const handleLike = async () => {
  if (!userStore.userInfo) {
    router.push('/login')
    return
  }
  const articleId = route.params.id
  if (!articleId) return

  likeLoading.value = true
  try {
    const res = await likeArticle(articleId)
    if (res.code === 200) {
      liked.value = true
      likeCount.value++
      ElMessage.success('点赞成功')
    } else {
      ElMessage.error(res.message || '点赞失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '点赞失败')
  } finally {
    likeLoading.value = false
  }
}

const handleUnlike = async () => {
  const articleId = route.params.id
  if (!articleId) return

  likeLoading.value = true
  try {
    const res = await unlikeArticle(articleId)
    if (res.code === 200) {
      liked.value = false
      likeCount.value--
      ElMessage.success('已取消点赞')
    } else {
      ElMessage.error(res.message || '取消点赞失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '取消点赞失败')
  } finally {
    likeLoading.value = false
  }
}

// ========== 评论区域 ==========
const comments = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentPageSize = ref(10)
const commentContent = ref('')
const replyingTo = ref(null)
const commentLoading = ref(false)
const publishing = ref(false)

const fetchComments = async () => {
  const articleId = route.params.id
  if (!articleId) return

  commentLoading.value = true
  try {
    const res = await getCommentList({
      articleId,
      page: commentPage.value,
      size: commentPageSize.value
    })
    if (res.code === 200) {
      comments.value = res.data.list || []
      commentTotal.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载评论失败', error)
  } finally {
    commentLoading.value = false
  }
}

const handlePublish = async () => {
  const content = commentContent.value.trim()
  if (!content) return

  const articleId = Number(route.params.id)
  if (!articleId) return

  publishing.value = true
  try {
    const res = await publishComment({
      articleId,
      content,
      parentId: replyingTo.value ? replyingTo.value.parentId || replyingTo.value.id : null,
      replyToId: replyingTo.value ? replyingTo.value.user?.id : null
    })

    if (res.code === 200) {
      ElMessage.success('发表成功')
      commentContent.value = ''
      replyingTo.value = null
      // 一级评论回到第一页刷新，回复直接插入
      if (!replyingTo.value) {
        commentPage.value = 1
        await fetchComments()
      } else {
        // 找到对应的一级评论，push到children
        const parentId = replyingTo.value.parentId || replyingTo.value.id
        const parent = comments.value.find(c => c.id === parentId)
        if (parent) {
          if (!parent.children) parent.children = []
          parent.children.push(res.data)
        }
        commentContent.value = ''
        replyingTo.value = null
      }
    } else {
      ElMessage.error(res.message || '发表失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '发表失败')
  } finally {
    publishing.value = false
  }
}

const handleReply = (comment) => {
  replyingTo.value = comment
  commentContent.value = ''
}

const cancelReply = () => {
  replyingTo.value = null
  commentContent.value = ''
}

const handleDelete = async (comment) => {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await deleteComment(comment.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (comment.parentId === null) {
        // 一级评论：重新加载当前页
        if (comments.value.length === 1 && commentPage.value > 1) {
          commentPage.value--
        }
        await fetchComments()
      } else {
        // 二级回复：从children中过滤
        const parent = comments.value.find(c => c.id === comment.parentId)
        if (parent && parent.children) {
          parent.children = parent.children.filter(r => r.id !== comment.id)
        }
      }
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
  fetchComments()
}

const canDelete = (comment) => {
  if (!userStore.userInfo || !comment.user) return false
  return userStore.userInfo.id === comment.user.id
      || userStore.userInfo.role === 'ADMIN'
}

const canReply = () => !!userStore.userInfo

onMounted(() => {
  fetchArticle()
  fetchLikeStatus()
  fetchComments()
})
</script>

<template>
  <div class="article-detail-page">
    <!-- 顶部导航 -->
    <nav class="navbar">
      <div class="nav-brand" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> NovaBlog
      </div>
    </nav>

    <!-- 文章内容 -->
    <div class="content-area" v-loading="loading">
      <template v-if="article">
        <div class="article-header">
          <h1 class="article-title">{{ article.title }}</h1>
          <div class="article-meta">
            <span class="meta-item">
              <el-icon><User /></el-icon>
              {{ article.author?.nickname || '匿名' }}
            </span>
            <span class="meta-item" v-if="article.category">
              <el-icon><Folder /></el-icon>
              {{ article.category.name }}
            </span>
            <span class="meta-item">
              <el-icon><View /></el-icon>
              {{ article.viewCount || 0 }} 浏览
            </span>
            <span class="meta-item">
              <el-icon><Clock /></el-icon>
              {{ formatTime(article.createTime) }}
            </span>
          </div>
          <div class="article-tags" v-if="article.tags && article.tags.length > 0">
            <el-tag v-for="tag in article.tags" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
        </div>

        <div class="article-content" v-html="renderMarkdown(article.content)"></div>

        <!-- 点赞区域 -->
        <div class="like-section">
          <div class="like-actions">
            <template v-if="!userStore.userInfo">
              <el-button
                type="default"
                size="large"
                class="like-btn"
                @click="router.push('/login')"
              >
                <el-icon><Star /></el-icon>
                <span>点赞</span>
                <span class="like-count">{{ likeCount }}</span>
              </el-button>
            </template>
            <template v-else>
              <el-button
                v-if="!liked"
                type="default"
                size="large"
                class="like-btn"
                :loading="likeLoading"
                @click="handleLike"
              >
                <el-icon><Star /></el-icon>
                <span>点赞</span>
                <span class="like-count">{{ likeCount }}</span>
              </el-button>
              <el-button
                v-else
                type="primary"
                size="large"
                class="like-btn liked"
                :loading="likeLoading"
                @click="handleUnlike"
              >
                <el-icon><StarFilled /></el-icon>
                <span>已赞</span>
                <span class="like-count">{{ likeCount }}</span>
              </el-button>
            </template>
          </div>
          <div class="view-count">
            <el-icon><View /></el-icon>
            <span>{{ article.viewCount || 0 }} 浏览</span>
          </div>
        </div>
      </template>

      <el-empty v-else-if="!loading" description="文章不存在" />
    </div>

    <!-- 评论区域 -->
    <div class="comment-section">
      <h2 class="comment-title">评论</h2>

      <!-- 发表评论框 -->
      <div class="comment-input-area">
        <template v-if="!userStore.userInfo">
          <div class="login-tip">
            <span>登录后参与讨论</span>
            <el-button type="primary" size="small" @click="router.push('/login')">登录</el-button>
            <el-button size="small" @click="router.push('/register')">注册</el-button>
          </div>
        </template>
        <template v-else>
          <div v-if="replyingTo" class="reply-status">
            <span>回复 @{{ replyingTo.user?.nickname || '匿名' }}</span>
            <el-button type="info" size="small" text @click="cancelReply">取消回复</el-button>
          </div>
          <el-input
            v-model="commentContent"
            type="textarea"
            :rows="3"
            :maxlength="500"
            show-word-limit
            :placeholder="replyingTo ? `回复 @${replyingTo.user?.nickname || '匿名'}...` : '写下你的评论...'"
          />
          <div class="comment-actions">
            <el-button
              type="primary"
              :disabled="!commentContent.trim() || publishing"
              :loading="publishing"
              @click="handlePublish"
            >
              发表
            </el-button>
          </div>
        </template>
      </div>

      <!-- 评论列表 -->
      <div v-loading="commentLoading" class="comment-list">
        <el-empty v-if="!commentLoading && comments.length === 0" description="暂无评论，来抢沙发吧~" />

        <div
          v-for="comment in comments"
          :key="comment.id"
          class="comment-item"
        >
          <!-- 一级评论 -->
          <div class="comment-main">
            <el-avatar :size="36" :src="comment.user?.avatar">
              <el-icon><User /></el-icon>
            </el-avatar>
            <div class="comment-body">
              <div class="comment-header">
                <span class="comment-author">{{ comment.user?.nickname || '匿名' }}</span>
                <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
              </div>
              <div class="comment-text">{{ comment.content }}</div>
              <div class="comment-footer">
                <span v-if="canReply() && comment.user" class="comment-action" @click="handleReply(comment)">回复</span>
                <span v-if="canDelete(comment)" class="comment-action delete" @click="handleDelete(comment)">删除</span>
              </div>
            </div>
          </div>

          <!-- 二级回复 -->
          <div v-if="comment.children && comment.children.length > 0" class="reply-list">
            <div
              v-for="reply in comment.children"
              :key="reply.id"
              class="reply-item"
            >
              <el-avatar :size="28" :src="reply.user?.avatar">
                <el-icon><User /></el-icon>
              </el-avatar>
              <div class="reply-body">
                <div class="reply-header">
                  <span class="reply-author">{{ reply.user?.nickname || '匿名' }}</span>
                  <span v-if="reply.replyToUser" class="reply-to">
                    回复 @{{ reply.replyToUser.nickname }}
                  </span>
                  <span class="reply-time">{{ formatTime(reply.createTime) }}</span>
                </div>
                <div class="reply-text">{{ reply.content }}</div>
                <div class="reply-footer">
                  <span v-if="canReply() && reply.user" class="comment-action" @click="handleReply(reply)">回复</span>
                  <span v-if="canDelete(reply)" class="comment-action delete" @click="handleDelete(reply)">删除</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="commentTotal > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="commentPage"
          v-model:page-size="commentPageSize"
          :total="commentTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.article-detail-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

/* 导航栏 */
.navbar {
  display: flex;
  align-items: center;
  padding: 16px 48px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-brand {
  font-size: 1.25rem;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-brand:hover {
  color: #409eff;
}

/* 内容区域 */
.content-area {
  max-width: 800px;
  margin: 0 auto;
  padding: 40px 20px;
}

/* 文章头部 */
.article-header {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.article-title {
  color: #fff;
  font-size: 2rem;
  font-weight: 700;
  margin: 0 0 16px 0;
  line-height: 1.3;
}

.article-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
}

.article-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* 点赞区域 */
.like-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.like-actions {
  display: flex;
  gap: 12px;
}

.like-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.7);
  transition: all 0.2s;
}

.like-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.3);
}

.like-btn.liked {
  background: rgba(64, 158, 255, 0.2);
  border-color: #409eff;
  color: #409eff;
}

.like-count {
  font-weight: 600;
}

.view-count {
  display: flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 14px;
}

/* Markdown 内容渲染 */
.article-content {
  color: rgba(255, 255, 255, 0.85);
  font-size: 16px;
  line-height: 1.8;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4),
.article-content :deep(h5),
.article-content :deep(h6) {
  color: #fff;
  margin: 24px 0 16px;
  font-weight: 600;
}

.article-content :deep(h1) { font-size: 1.75rem; }
.article-content :deep(h2) { font-size: 1.5rem; }
.article-content :deep(h3) { font-size: 1.25rem; }

.article-content :deep(p) {
  margin: 12px 0;
}

.article-content :deep(a) {
  color: #409eff;
  text-decoration: none;
}

.article-content :deep(a:hover) {
  text-decoration: underline;
}

.article-content :deep(code) {
  background: rgba(255, 255, 255, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.9em;
}

.article-content :deep(pre) {
  background: rgba(0, 0, 0, 0.3);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 16px 0;
}

.article-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.article-content :deep(blockquote) {
  border-left: 4px solid #409eff;
  margin: 16px 0;
  padding: 8px 16px;
  background: rgba(64, 158, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
}

.article-content :deep(li) {
  margin: 4px 0;
}

.article-content :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 16px 0;
}

.article-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  margin: 24px 0;
}

.article-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
}

.article-content :deep(th),
.article-content :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 8px 12px;
  text-align: left;
}

.article-content :deep(th) {
  background: rgba(255, 255, 255, 0.05);
  font-weight: 600;
}

/* ========== 评论区域 ========== */
.comment-section {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 20px 40px;
}

.comment-title {
  color: #fff;
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 20px 0;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

/* 发表评论框 */
.comment-input-area {
  margin-bottom: 24px;
}

.login-tip {
  display: flex;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.reply-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #409eff;
  font-size: 14px;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* 评论列表 */
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
}

.comment-main {
  display: flex;
  gap: 12px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-author {
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}

.comment-time {
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}

.comment-text {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-footer {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.comment-action {
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s;
}

.comment-action:hover {
  color: #409eff;
}

.comment-action.delete:hover {
  color: #f56c6c;
}

/* 二级回复 */
.reply-list {
  margin-top: 12px;
  margin-left: 48px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reply-item {
  display: flex;
  gap: 10px;
}

.reply-body {
  flex: 1;
  min-width: 0;
}

.reply-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.reply-author {
  color: rgba(255, 255, 255, 0.9);
  font-weight: 600;
  font-size: 13px;
}

.reply-to {
  color: #409eff;
  font-size: 12px;
}

.reply-time {
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}

.reply-text {
  color: rgba(255, 255, 255, 0.75);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.reply-footer {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>

<style>
/* 覆盖 Element Plus 分页在暗色背景下的样式 */
.comment-section .el-pagination {
  --el-pagination-bg-color: transparent;
  --el-pagination-text-color: rgba(255, 255, 255, 0.7);
  --el-pagination-button-color: rgba(255, 255, 255, 0.7);
}

.comment-section .el-pagination .el-pager li {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.comment-section .el-pagination .el-pager li.is-active {
  background: #409eff;
  border-color: #409eff;
}

.comment-section .el-pagination .btn-prev,
.comment-section .el-pagination .btn-next {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* 覆盖输入框样式 */
.comment-section .el-textarea__inner {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.comment-section .el-textarea__inner::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.comment-section .el-input__count {
  color: rgba(255, 255, 255, 0.5);
  background: transparent;
}
</style>
