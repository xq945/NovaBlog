<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyArticles, deleteArticle } from '../api/article'
import { getProfile, updateProfile } from '../api/user'
import { uploadFile } from '../api/upload'
import { useUserStore } from '../stores'

const router = useRouter()
const userStore = useUserStore()

// ========== 个人信息 ==========
const profile = ref(null)
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const avatarLoading = ref(false)
const editForm = reactive({
  nickname: '',
  email: '',
  avatar: ''
})

const fetchProfile = async () => {
  loading.value = true
  try {
    const res = await getProfile()
    if (res.code === 200) {
      profile.value = res.data
    }
  } catch (error) {
    console.error('加载个人信息失败', error)
  } finally {
    loading.value = false
  }
}

const startEdit = () => {
  editForm.nickname = profile.value?.nickname || ''
  editForm.email = profile.value?.email || ''
  editForm.avatar = profile.value?.avatar || ''
  editing.value = true
}

const cancelEdit = () => {
  editing.value = false
  avatarLoading.value = false
}

const handleSave = async () => {
  const nickname = editForm.nickname.trim()
  if (!nickname) {
    ElMessage.error('昵称不能为空')
    return
  }
  if (nickname.length > 20) {
    ElMessage.error('昵称长度不能超过20位')
    return
  }

  const email = editForm.email.trim()
  if (email && email.length > 100) {
    ElMessage.error('邮箱长度不能超过100位')
    return
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.error('邮箱格式不正确')
    return
  }

  saving.value = true
  try {
    const res = await updateProfile({
      nickname,
      email: email || null,
      avatar: editForm.avatar || null
    })
    if (res.code === 200) {
      ElMessage.success('保存成功')
      editing.value = false
      await fetchProfile()
      // 同步全局状态
      if (profile.value) {
        userStore.setUserInfo(profile.value)
      }
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/**
 * 头像上传前的校验
 */
const beforeAvatarUpload = (file) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif' || file.type === 'image/webp'
  if (!isJpgOrPng) {
    ElMessage.error('仅支持 jpg/png/gif/webp 格式的图片')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    ElMessage.error('头像大小不能超过 2MB')
    return false
  }
  return true
}

/**
 * 自定义头像上传
 */
const handleAvatarUpload = async (options) => {
  avatarLoading.value = true
  try {
    const res = await uploadFile(options.file)
    if (res.code === 200) {
      editForm.avatar = res.data.url
      ElMessage.success('头像上传成功')
    } else {
      ElMessage.error(res.message || '上传失败')
      options.onError()
    }
  } catch (error) {
    ElMessage.error('上传失败')
    options.onError()
  } finally {
    avatarLoading.value = false
  }
}

const getRoleLabel = (role) => {
  return role === 'ADMIN' ? '管理员' : '普通用户'
}

const getRoleType = (role) => {
  return role === 'ADMIN' ? 'danger' : 'info'
}

// ========== 我的文章 ==========
const articles = ref([])
const total = ref(0)
const articleLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)

const fetchMyArticles = async () => {
  articleLoading.value = true
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
    articleLoading.value = false
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
  fetchProfile()
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
        <span v-if="userStore.userInfo?.role === 'ADMIN'" class="nav-link" @click="router.push('/admin')">
          <el-icon><Setting /></el-icon> 后台管理
        </span>
        <span class="nav-link" @click="router.push('/article/create')">
          <el-icon><Plus /></el-icon> 写文章
        </span>
      </div>
    </nav>

    <!-- 内容区域 -->
    <div class="content-area">
      <!-- 个人信息卡片 -->
      <div class="profile-card" v-loading="loading">
        <div class="profile-main">
          <!-- 头像 -->
          <div class="avatar-wrapper">
            <template v-if="!editing">
              <el-avatar :size="80" :src="profile?.avatar">
                <span class="avatar-fallback">{{ profile?.nickname?.charAt(0)?.toUpperCase() || '?' }}</span>
              </el-avatar>
            </template>
            <template v-else>
              <el-upload
                class="avatar-uploader"
                :http-request="handleAvatarUpload"
                :before-upload="beforeAvatarUpload"
                accept="image/jpeg,image/png,image/gif,image/webp"
                :show-file-list="false"
              >
                <div class="avatar-edit" v-loading="avatarLoading">
                  <el-avatar :size="80" :src="editForm.avatar">
                    <span class="avatar-fallback">{{ editForm.nickname?.charAt(0)?.toUpperCase() || '?' }}</span>
                  </el-avatar>
                  <div class="avatar-overlay">
                    <el-icon :size="20"><Camera /></el-icon>
                    <span>更换头像</span>
                  </div>
                </div>
              </el-upload>
            </template>
          </div>

          <!-- 信息区域 -->
          <div class="profile-info">
            <!-- 展示模式 -->
            <template v-if="!editing">
              <div class="info-row">
                <h2 class="profile-name">{{ profile?.nickname || '-' }}</h2>
                <el-tag :type="getRoleType(profile?.role)" size="small" class="role-tag">
                  {{ getRoleLabel(profile?.role) }}
                </el-tag>
              </div>
              <div class="info-row meta">
                <span class="meta-item">
                  <el-icon><User /></el-icon>
                  用户名：{{ profile?.username || '-' }}
                </span>
                <span class="meta-item">
                  <el-icon><Message /></el-icon>
                  邮箱：{{ profile?.email || '未设置' }}
                </span>
                <span class="meta-item">
                  <el-icon><Clock /></el-icon>
                  注册时间：{{ formatTime(profile?.createTime) }}
                </span>
              </div>
              <div class="info-actions">
                <el-button type="primary" @click="startEdit">
                  <el-icon><Edit /></el-icon> 编辑资料
                </el-button>
              </div>
            </template>

            <!-- 编辑模式 -->
            <template v-else>
              <div class="edit-form">
                <div class="form-row">
                  <label>昵称</label>
                  <el-input
                    v-model="editForm.nickname"
                    placeholder="请输入昵称"
                    maxlength="20"
                    show-word-limit
                    class="edit-input"
                  />
                </div>
                <div class="form-row">
                  <label>邮箱</label>
                  <el-input
                    v-model="editForm.email"
                    placeholder="请输入邮箱（可选）"
                    maxlength="100"
                    class="edit-input"
                  />
                </div>
                <div class="form-actions">
                  <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
                  <el-button @click="cancelEdit">取消</el-button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 我的文章列表 -->
      <div class="article-section">
        <h2 class="section-title">我的文章</h2>

        <div class="article-table-wrapper" v-loading="articleLoading">
          <div v-if="articles.length > 0" class="table-debug">共 {{ total }} 篇文章</div>

          <el-table
            v-if="articles.length > 0"
            :data="articles"
            style="width: 100%"
            border
          >
            <el-table-column prop="title" label="标题" min-width="200">
              <template #default="{ row }">
                <span class="article-title" @click="router.push(`/article/${row.id}`)">
                  {{ row.title }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="分类" width="120">
              <template #default="{ row }">
                {{ row.category?.name || '-' }}
              </template>
            </el-table-column>
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
            <el-table-column label="操作" width="140">
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

/* 个人信息卡片 */
.profile-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 32px;
}

.profile-main {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.avatar-wrapper {
  flex-shrink: 0;
}

.avatar-uploader :deep(.el-upload) {
  display: block;
}

.avatar-edit {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  overflow: hidden;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
  border-radius: 50%;
}

.avatar-edit:hover .avatar-overlay {
  opacity: 1;
}

.avatar-fallback {
  font-size: 32px;
  font-weight: 600;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.profile-name {
  color: #fff;
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
}

.role-tag {
  font-size: 12px;
}

.info-row.meta {
  gap: 20px;
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
}

.info-actions {
  margin-top: 8px;
}

/* 编辑表单 */
.edit-form {
  max-width: 400px;
}

.form-row {
  margin-bottom: 16px;
}

.form-row label {
  display: block;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  margin-bottom: 8px;
}

.edit-input {
  width: 100%;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

/* 文章区域 */
.article-section {
  margin-top: 32px;
}

.section-title {
  color: #fff;
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 16px 0;
}

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

.table-debug {
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  margin-bottom: 12px;
}

/* Element Plus 暗色覆盖 */
:deep(.el-table) {
  background: transparent !important;
  --el-table-bg-color: transparent;
  --el-table-header-bg-color: rgba(255, 255, 255, 0.08);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-table-tr-bg-color: transparent;
  --el-table-text-color: rgba(255, 255, 255, 0.85);
  --el-table-header-text-color: rgba(255, 255, 255, 0.9);
  --el-table-border-color: rgba(255, 255, 255, 0.15);
}

:deep(.el-table__header-wrapper),
:deep(.el-table__body-wrapper) {
  background: transparent;
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-table th) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: rgba(255, 255, 255, 0.9) !important;
  font-weight: 600;
}

:deep(.el-table td) {
  background: transparent !important;
  color: rgba(255, 255, 255, 0.85) !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-table tr) {
  background: transparent !important;
}

:deep(.el-table tr:hover td) {
  background: rgba(255, 255, 255, 0.08) !important;
}

:deep(.el-table--border) {
  border-color: rgba(255, 255, 255, 0.15);
}

:deep(.el-table--border .el-table__cell) {
  border-color: rgba(255, 255, 255, 0.15);
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

:deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.08) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.2) inset !important;
}

:deep(.el-input__inner) {
  color: #fff !important;
}

:deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.4) !important;
}

:deep(.el-input__count) {
  color: rgba(255, 255, 255, 0.5) !important;
}
</style>
