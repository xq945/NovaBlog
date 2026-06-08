<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getArticleDetail } from '../api/article'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const route = useRoute()
const router = useRouter()

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

onMounted(() => {
  fetchArticle()
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
      </template>

      <el-empty v-else-if="!loading" description="文章不存在" />
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
</style>
