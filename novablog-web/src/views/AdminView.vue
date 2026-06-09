<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminUserList,
  updateUserStatus
} from '../api/user'
import {
  getAdminArticleList,
  deleteArticle
} from '../api/article'
import {
  getAdminCommentList,
  deleteComment
} from '../api/comment'
import {
  getCategoryList,
  createCategory,
  updateCategory,
  deleteCategory
} from '../api/category'
import {
  getTagList,
  createTag,
  updateTag,
  deleteTag
} from '../api/tag'
import { useUserStore } from '../stores'

const router = useRouter()
const userStore = useUserStore()

// ========== 通用状态 ==========
const activeTab = ref('users')

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

// ========== 用户管理 ==========
const userLoading = ref(false)
const userList = ref([])
const userTotal = ref(0)
const userPage = ref(1)
const userSize = ref(10)

const fetchUsers = async () => {
  userLoading.value = true
  try {
    const res = await getAdminUserList({
      page: userPage.value,
      size: userSize.value
    })
    if (res.code === 200) {
      userList.value = res.data.list
      userTotal.value = res.data.total
    }
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    userLoading.value = false
  }
}

const handleUserStatusChange = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    const res = await updateUserStatus({
      userId: row.id,
      status: newStatus
    })
    if (res.code === 200) {
      row.status = newStatus
      ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const getRoleLabel = (role) => {
  return role === 'ADMIN' ? '管理员' : '普通用户'
}

const getRoleType = (role) => {
  return role === 'ADMIN' ? 'danger' : 'info'
}

// ========== 文章管理 ==========
const articleLoading = ref(false)
const articleList = ref([])
const articleTotal = ref(0)
const articlePage = ref(1)
const articleSize = ref(10)
const articleKeyword = ref('')

const fetchArticles = async () => {
  articleLoading.value = true
  try {
    const res = await getAdminArticleList({
      page: articlePage.value,
      size: articleSize.value,
      keyword: articleKeyword.value || undefined
    })
    if (res.code === 200) {
      articleList.value = res.data.list
      articleTotal.value = res.data.total
    }
  } catch (error) {
    ElMessage.error('加载文章列表失败')
  } finally {
    articleLoading.value = false
  }
}

const handleArticleSearch = () => {
  articlePage.value = 1
  fetchArticles()
}

const handleArticleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这篇文章吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteArticle(row.id)
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

const getStatusLabel = (status) => {
  return status === 1 ? '已发布' : '草稿'
}

const getStatusType = (status) => {
  return status === 1 ? 'success' : 'warning'
}

// ========== 评论管理 ==========
const commentLoading = ref(false)
const commentList = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentSize = ref(10)
const commentArticleId = ref(null)

const fetchComments = async () => {
  commentLoading.value = true
  try {
    const params = {
      page: commentPage.value,
      size: commentSize.value
    }
    if (commentArticleId.value) {
      params.articleId = commentArticleId.value
    }
    const res = await getAdminCommentList(params)
    if (res.code === 200) {
      commentList.value = res.data.list
      commentTotal.value = res.data.total
    }
  } catch (error) {
    ElMessage.error('加载评论列表失败')
  } finally {
    commentLoading.value = false
  }
}

const handleCommentFilter = () => {
  commentPage.value = 1
  fetchComments()
}

const handleCommentDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteComment(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchComments()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// ========== 分类管理 ==========
const categoryLoading = ref(false)
const categoryList = ref([])
const categoryDialogVisible = ref(false)
const categoryDialogTitle = ref('')
const categoryForm = reactive({
  id: null,
  name: '',
  description: ''
})
const categoryFormRef = ref(null)
const categoryRules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 1, max: 20, message: '长度为1-20位', trigger: 'blur' }
  ]
}
const categorySubmitting = ref(false)

const fetchCategories = async () => {
  categoryLoading.value = true
  try {
    const res = await getCategoryList()
    if (res.code === 200) {
      categoryList.value = res.data
    }
  } catch (error) {
    ElMessage.error('加载分类列表失败')
  } finally {
    categoryLoading.value = false
  }
}

const openCategoryCreate = () => {
  categoryDialogTitle.value = '新增分类'
  categoryForm.id = null
  categoryForm.name = ''
  categoryForm.description = ''
  categoryDialogVisible.value = true
}

const openCategoryEdit = (row) => {
  categoryDialogTitle.value = '编辑分类'
  categoryForm.id = row.id
  categoryForm.name = row.name
  categoryForm.description = row.description || ''
  categoryDialogVisible.value = true
}

const handleCategorySubmit = async () => {
  const valid = await categoryFormRef.value.validate().catch(() => false)
  if (!valid) return

  categorySubmitting.value = true
  try {
    const data = {
      name: categoryForm.name.trim(),
      description: categoryForm.description.trim() || null
    }
    let res
    if (categoryForm.id) {
      data.id = categoryForm.id
      res = await updateCategory(data)
    } else {
      res = await createCategory(data)
    }
    if (res.code === 200) {
      ElMessage.success(categoryForm.id ? '修改成功' : '创建成功')
      categoryDialogVisible.value = false
      fetchCategories()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    categorySubmitting.value = false
  }
}

const handleCategoryDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个分类吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteCategory(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchCategories()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// ========== 标签管理 ==========
const tagLoading = ref(false)
const tagList = ref([])
const tagDialogVisible = ref(false)
const tagDialogTitle = ref('')
const tagForm = reactive({
  id: null,
  name: ''
})
const tagFormRef = ref(null)
const tagRules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { min: 1, max: 20, message: '长度为1-20位', trigger: 'blur' }
  ]
}
const tagSubmitting = ref(false)

const fetchTags = async () => {
  tagLoading.value = true
  try {
    const res = await getTagList()
    if (res.code === 200) {
      tagList.value = res.data
    }
  } catch (error) {
    ElMessage.error('加载标签列表失败')
  } finally {
    tagLoading.value = false
  }
}

const openTagCreate = () => {
  tagDialogTitle.value = '新增标签'
  tagForm.id = null
  tagForm.name = ''
  tagDialogVisible.value = true
}

const openTagEdit = (row) => {
  tagDialogTitle.value = '编辑标签'
  tagForm.id = row.id
  tagForm.name = row.name
  tagDialogVisible.value = true
}

const handleTagSubmit = async () => {
  const valid = await tagFormRef.value.validate().catch(() => false)
  if (!valid) return

  tagSubmitting.value = true
  try {
    const data = {
      name: tagForm.name.trim()
    }
    let res
    if (tagForm.id) {
      data.id = tagForm.id
      res = await updateTag(data)
    } else {
      res = await createTag(data)
    }
    if (res.code === 200) {
      ElMessage.success(tagForm.id ? '修改成功' : '创建成功')
      tagDialogVisible.value = false
      fetchTags()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    tagSubmitting.value = false
  }
}

const handleTagDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个标签吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteTag(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchTags()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// ========== Tab 切换加载 ==========
const handleTabChange = (tab) => {
  if (tab === 'users' && userList.value.length === 0) {
    fetchUsers()
  } else if (tab === 'articles' && articleList.value.length === 0) {
    fetchArticles()
  } else if (tab === 'comments' && commentList.value.length === 0) {
    fetchComments()
  } else if (tab === 'categories' && categoryList.value.length === 0) {
    fetchCategories()
  } else if (tab === 'tags' && tagList.value.length === 0) {
    fetchTags()
  }
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="admin-page">
    <!-- 顶部导航 -->
    <nav class="navbar">
      <div class="nav-brand" @click="router.push('/')">
        <el-icon><ArrowLeft /></el-icon> 返回首页
      </div>
      <div class="nav-title">后台管理</div>
      <div class="nav-user">
        <span class="user-name">{{ userStore.userInfo?.nickname }}</span>
      </div>
    </nav>

    <!-- 主内容区 -->
    <div class="admin-content">
      <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
        <!-- 用户管理 -->
        <el-tab-pane label="用户管理" name="users">
          <div v-loading="userLoading" class="tab-content">
            <el-table :data="userList" border stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="username" label="用户名" width="120" />
              <el-table-column prop="nickname" label="昵称" width="120" />
              <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
              <el-table-column prop="role" label="角色" width="100">
                <template #default="{ row }">
                  <el-tag :type="getRoleType(row.role)" size="small">
                    {{ getRoleLabel(row.role) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-switch
                    :model-value="row.status === 1"
                    @change="handleUserStatusChange(row)"
                    active-text="启用"
                    inactive-text="禁用"
                    inline-prompt
                    :disabled="row.role === 'ADMIN'"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="注册时间" width="160">
                <template #default="{ row }">
                  {{ formatTime(row.createTime) }}
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-wrapper" v-if="userTotal > 0">
              <el-pagination
                v-model:current-page="userPage"
                v-model:page-size="userSize"
                :total="userTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchUsers"
                @size-change="fetchUsers"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 文章管理 -->
        <el-tab-pane label="文章管理" name="articles">
          <div v-loading="articleLoading" class="tab-content">
            <div class="toolbar">
              <el-input
                v-model="articleKeyword"
                placeholder="搜索文章标题..."
                clearable
                style="width: 300px"
                @keyup.enter="handleArticleSearch"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-button type="primary" @click="handleArticleSearch">
                <el-icon><Search /></el-icon> 搜索
              </el-button>
            </div>
            <el-table :data="articleList" border stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
              <el-table-column prop="author.nickname" label="作者" width="120" />
              <el-table-column prop="category.name" label="分类" width="100" />
              <el-table-column prop="status" label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)" size="small">
                    {{ getStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="viewCount" label="浏览" width="80" />
              <el-table-column prop="likeCount" label="点赞" width="80" />
              <el-table-column prop="createTime" label="创建时间" width="160">
                <template #default="{ row }">
                  {{ formatTime(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" text @click="router.push(`/article/${row.id}`)">
                    查看
                  </el-button>
                  <el-button type="primary" size="small" text @click="router.push(`/article/edit/${row.id}`)">
                    编辑
                  </el-button>
                  <el-button type="danger" size="small" text @click="handleArticleDelete(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-wrapper" v-if="articleTotal > 0">
              <el-pagination
                v-model:current-page="articlePage"
                v-model:page-size="articleSize"
                :total="articleTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchArticles"
                @size-change="fetchArticles"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 评论管理 -->
        <el-tab-pane label="评论管理" name="comments">
          <div v-loading="commentLoading" class="tab-content">
            <div class="toolbar">
              <el-input
                v-model="commentArticleId"
                placeholder="按文章ID筛选"
                clearable
                style="width: 200px"
                type="number"
              />
              <el-button type="primary" @click="handleCommentFilter">
                <el-icon><Search /></el-icon> 筛选
              </el-button>
            </div>
            <el-table :data="commentList" border stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
              <el-table-column prop="articleTitle" label="所属文章" width="150" show-overflow-tooltip />
              <el-table-column prop="user.nickname" label="评论者" width="100" />
              <el-table-column prop="parentId" label="类型" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.parentId" type="info" size="small">回复</el-tag>
                  <el-tag v-else type="success" size="small">一级</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="replyToUser.nickname" label="被回复者" width="100">
                <template #default="{ row }">
                  {{ row.replyToUser?.nickname || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" width="160">
                <template #default="{ row }">
                  {{ formatTime(row.createTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }">
                  <el-button type="danger" size="small" text @click="handleCommentDelete(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-wrapper" v-if="commentTotal > 0">
              <el-pagination
                v-model:current-page="commentPage"
                v-model:page-size="commentSize"
                :total="commentTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="fetchComments"
                @size-change="fetchComments"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- 分类管理 -->
        <el-tab-pane label="分类管理" name="categories">
          <div v-loading="categoryLoading" class="tab-content">
            <div class="toolbar">
              <el-button type="primary" @click="openCategoryCreate">
                <el-icon><Plus /></el-icon> 新增分类
              </el-button>
            </div>
            <el-table :data="categoryList" border stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="name" label="名称" width="150" />
              <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" text @click="openCategoryEdit(row)">
                    编辑
                  </el-button>
                  <el-button type="danger" size="small" text @click="handleCategoryDelete(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 标签管理 -->
        <el-tab-pane label="标签管理" name="tags">
          <div v-loading="tagLoading" class="tab-content">
            <div class="toolbar">
              <el-button type="primary" @click="openTagCreate">
                <el-icon><Plus /></el-icon> 新增标签
              </el-button>
            </div>
            <el-table :data="tagList" border stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="name" label="名称" min-width="200" />
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" size="small" text @click="openTagEdit(row)">
                    编辑
                  </el-button>
                  <el-button type="danger" size="small" text @click="handleTagDelete(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 分类对话框 -->
    <el-dialog v-model="categoryDialogVisible" :title="categoryDialogTitle" width="400px">
      <el-form ref="categoryFormRef" :model="categoryForm" :rules="categoryRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="categoryForm.name" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="categoryForm.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCategorySubmit" :loading="categorySubmitting">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 标签对话框 -->
    <el-dialog v-model="tagDialogVisible" :title="tagDialogTitle" width="400px">
      <el-form ref="tagFormRef" :model="tagForm" :rules="tagRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="tagForm.name" maxlength="20" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleTagSubmit" :loading="tagSubmitting">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid #e4e7ed;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 500;
  color: #606266;
  cursor: pointer;
  transition: color 0.2s;
}

.nav-brand:hover {
  color: #409eff;
}

.nav-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.nav-user .user-name {
  font-size: 14px;
  color: #606266;
}

.admin-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.tab-content {
  padding: 10px 0;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 修复分页 is-active 页码蓝底蓝字问题 */
:deep(.el-pagination .el-pager li.is-active) {
  color: #fff;
}
</style>
