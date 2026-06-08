<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getArticleList, deleteArticle } from '../api/article'
import { getCategoryList } from '../api/category'
import { useUserStore } from '../stores'

const router = useRouter()
const userStore = useUserStore()

// ========== 文章列表 ==========
const articles = ref([])
const total = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')
const selectedCategory = ref(null)
const categories = ref([])

// 搜索防抖定时器
let searchTimer = null

const fetchArticles = async () => {
  loading.value = true
  try {
    const res = await getArticleList({
      page: currentPage.value,
      size: pageSize.value,
      categoryId: selectedCategory.value || undefined,
      keyword: searchKeyword.value || undefined
    })
    if (res.code === 200) {
      articles.value = res.data.list
      total.value = res.data.total
    }
  } catch (error) {
    ElMessage.error('加载文章失败')
  } finally {
    loading.value = false
  }
}

const fetchCategories = async () => {
  try {
    const res = await getCategoryList()
    if (res.code === 200) {
      categories.value = res.data
    }
  } catch (error) {
    console.error('加载分类失败', error)
  }
}

const handleSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    fetchArticles()
  }, 300)
}

const handleCategoryChange = () => {
  currentPage.value = 1
  fetchArticles()
}

const handlePageChange = (page) => {
  currentPage.value = page
  fetchArticles()
}

const goToDetail = (id) => {
  router.push(`/article/${id}`)
}

const goToCreate = () => {
  router.push('/article/create')
}

const goToEdit = (id, event) => {
  event.stopPropagation()
  router.push(`/article/edit/${id}`)
}

const handleDelete = async (id, event) => {
  event.stopPropagation()
  try {
    await ElMessageBox.confirm('确定要删除这篇文章吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteArticle(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchArticles()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
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

// ========== 退出登录 ==========
const handleLogout = () => {
  userStore.clearToken()
  ElMessage.success('已退出登录')
  window.location.reload()
}

onMounted(() => {
  fetchCategories()
  fetchArticles()
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<template>
  <div class="home">
    <!-- 顶部导航 -->
    <nav class="navbar">
      <div class="nav-brand" @click="router.push('/')">NovaBlog</div>
      <div class="nav-links">
        <template v-if="userStore.userInfo">
          <span class="nav-link" @click="goToCreate">
            <el-icon><Plus /></el-icon> 写文章
          </span>
          <span class="nav-link" @click="router.push('/profile')">
            <el-icon><Document /></el-icon> 我的文章
          </span>
          <span class="user-name">{{ userStore.userInfo.nickname }}</span>
          <span class="nav-link" @click="handleLogout">退出</span>
        </template>
        <template v-else>
          <span class="nav-link" @click="router.push('/login')">登录</span>
          <span class="nav-link nav-btn" @click="router.push('/register')">注册</span>
        </template>
      </div>
    </nav>

    <!-- 文章列表区域 -->
    <div class="content-area">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-select
          v-model="selectedCategory"
          placeholder="全部分类"
          clearable
          @change="handleCategoryChange"
          class="category-select"
        >
          <el-option
            v-for="cat in categories"
            :key="cat.id"
            :label="cat.name"
            :value="cat.id"
          />
        </el-select>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文章..."
          clearable
          @input="handleSearch"
          class="search-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 文章列表 -->
      <div v-loading="loading" class="article-list">
        <el-empty v-if="!loading && articles.length === 0" description="暂无文章" />

        <div
          v-for="article in articles"
          :key="article.id"
          class="article-card"
          @click="goToDetail(article.id)"
        >
          <div class="article-cover" v-if="article.cover">
            <img :src="article.cover" :alt="article.title" />
          </div>
          <div class="article-body">
            <div class="article-header">
              <h3 class="article-title">{{ article.title }}</h3>
              <div class="article-actions" v-if="userStore.userInfo && userStore.userInfo.id === article.author?.id">
                <el-button
                  type="primary"
                  size="small"
                  text
                  @click="goToEdit(article.id, $event)"
                >
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  text
                  @click="handleDelete(article.id, $event)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <p class="article-summary">{{ article.summary || '暂无摘要' }}</p>
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
                {{ article.viewCount || 0 }}
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                {{ formatTime(article.createTime) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
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
.home {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

/* 导航栏 */
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 48px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-brand {
  font-size: 1.5rem;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 16px;
}

.nav-link {
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 0.2s;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

.nav-btn {
  background: #409eff;
  color: #fff;
}

.nav-btn:hover {
  background: #66b1ff;
}

.user-name {
  color: #fff;
  font-size: 14px;
}

/* 内容区域 */
.content-area {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 20px;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.category-select {
  width: 160px;
}

.search-input {
  flex: 1;
}

/* 文章列表 */
.article-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.article-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.article-card:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

.article-cover {
  width: 100%;
  height: 200px;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
}

.article-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.article-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.article-title {
  color: #fff;
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0;
  line-height: 1.4;
}

.article-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.article-summary {
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  line-height: 1.6;
  margin: 8px 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>

<style>
/* 覆盖 Element Plus 分页在暗色背景下的样式 */
.home .el-pagination {
  --el-pagination-bg-color: transparent;
  --el-pagination-text-color: rgba(255, 255, 255, 0.7);
  --el-pagination-button-color: rgba(255, 255, 255, 0.7);
}

.home .el-pagination .el-pager li {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.home .el-pagination .el-pager li.is-active {
  background: #409eff;
  border-color: #409eff;
}

.home .el-pagination .btn-prev,
.home .el-pagination .btn-next {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* 覆盖输入框和选择框样式 */
.home .el-input {
  --el-input-bg-color: rgba(255, 255, 255, 0.08);
  --el-input-text-color: #fff;
  --el-input-border-color: rgba(255, 255, 255, 0.2);
  --el-input-hover-border-color: rgba(255, 255, 255, 0.4);
  --el-input-focus-border-color: #409eff;
  --el-input-placeholder-color: rgba(255, 255, 255, 0.4);
  --el-input-icon-color: rgba(255, 255, 255, 0.5);
}

.home .el-input__wrapper {
  background-color: var(--el-input-bg-color) !important;
  box-shadow: 0 0 0 1px var(--el-input-border-color) inset !important;
}

.home .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px var(--el-input-focus-border-color) inset !important;
}

.home .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px var(--el-input-hover-border-color) inset !important;
}

.home .el-input__inner {
  color: var(--el-input-text-color) !important;
}

.home .el-input__inner::placeholder {
  color: var(--el-input-placeholder-color) !important;
}

.home .el-input__icon {
  color: var(--el-input-icon-color) !important;
}

.home .el-select {
  --el-select-input-focus-border-color: #409eff;
}

.home .el-form-item__error {
  color: #f56c6c;
}
</style>
