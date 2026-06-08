<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyArticles, deleteArticle } from '../api/article'
import { useUserStore } from '../stores'

const router = useRouter()
const userStore = useUserStore()

const articles = ref([])
const total = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)

const fetchMyArticles = async () => {
  loading.value = true
  try {
    const res = await getMyArticles({
      page: currentPage.value,
      size: pageSize.value
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

const handlePageChange = (page) => {
  currentPage.value = page
  fetchMyArticles()
}

const goToEdit = (id) => {
  router.push(`/article/edit/${id}`)
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这篇文章吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteArticle(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchMyArticles()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const getStatusType = (status) => {
  return status === 1 ? 'success' : 'info'
}

const getStatusText = (status) => {
  return status === 1 ? '已发布' : '草稿'
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
  if (!userStore.isLoggedIn()) {
    window.dispatchEvent(new CustomEvent('show-login', {
      detail: { message: '请先登录' }
    }))
    router.push('/')
    return
  }
  fetchMyArticles()
})
</script>

<template>
  <div class="profile-page">
    <!-- 顶部导航 -->
    <nav class="navbar">
      <div class="nav-brand" @click="router.push('/')">
        <el-icon><ArrowLeft /></el-icon> NovaBlog
      </div>
      <div class="nav-links">
        <span class="nav-link" @click="router.push('/')">首页</span>
        <span class="nav-link" @click="router.push('/article/create')">
          <el-icon><Plus /></el-icon> 写文章
        </span>
      </div>
    </nav>

    <!-- 内容区域 -->
    <div class="content-area">
      <div class="profile-header">
        <h1>我的文章</h1>
        <p class="subtitle">管理你的所有文章，包括草稿和已发布</p>
      </div>

      <div class="article-table-wrapper" v-loading="loading">
        <el-table :data="articles" style="width: 100%" v-if="articles.length > 0">
          <el-table-column prop="title" label="标题" min-width="200">
            <template #default="{ row }">
              <span class="article-title" @click="router.push(`/article/${row.id}`)">
                {{ row.title }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="category.name" label="分类" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="浏览量" width="100">
            <template #default="{ row }">
              {{ row.viewCount || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="发布时间" width="160">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" text @click="goToEdit(row.id)">
                编辑
              </el-button>
              <el-button type="danger" size="small" text @click="handleDelete(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-else description="还没有文章，去写一篇吧" />
      </div>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
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

/* 内容区域 */
.content-area {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 20px;
}

.profile-header {
  margin-bottom: 24px;
}

.profile-header h1 {
  color: #fff;
  font-size: 1.75rem;
  margin: 0 0 8px 0;
}

.subtitle {
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  margin: 0;
}

/* 表格 */
.article-table-wrapper {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 16px;
}

.article-title {
  color: #fff;
  cursor: pointer;
}

.article-title:hover {
  color: #409eff;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

:deep(.el-table) {
  background: transparent;
  --el-table-header-bg-color: rgba(255, 255, 255, 0.05);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
}

:deep(.el-table th) {
  background: rgba(255, 255, 255, 0.05) !important;
  color: rgba(255, 255, 255, 0.7);
}

:deep(.el-table td) {
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

:deep(.el-table tr:hover td) {
  background: rgba(255, 255, 255, 0.05) !important;
}

:deep(.el-empty__description) {
  color: rgba(255, 255, 255, 0.5);
}

:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-text-color: rgba(255, 255, 255, 0.7);
  --el-pagination-button-color: rgba(255, 255, 255, 0.7);
}

:deep(.el-pagination .el-pager li) {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

:deep(.el-pagination .el-pager li.is-active) {
  background: #409eff;
  border-color: #409eff;
}

:deep(.el-pagination .btn-prev),
:deep(.el-pagination .btn-next) {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
</style>
