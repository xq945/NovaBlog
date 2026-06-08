<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { login, register } from '../api/user'
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

// ========== 登录弹窗（保留原有逻辑）==========
const loginDialogVisible = ref(false)
const loginLoading = ref(false)
const loginFormRef = ref(null)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20位', trigger: 'blur' }
  ]
}

const openLoginDialog = () => {
  loginDialogVisible.value = true
}

const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loginLoading.value = true
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password
    })
    if (res.code === 200) {
      const { token, refreshToken, userInfo } = res.data
      userStore.setToken(token, refreshToken)
      userStore.setUserInfo(userInfo)
      ElMessage.success('登录成功')
      loginDialogVisible.value = false
      loginForm.username = ''
      loginForm.password = ''
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loginLoading.value = false
  }
}

const handleLoginDialogClose = () => {
  loginForm.username = ''
  loginForm.password = ''
  loginFormRef.value?.resetFields()
}

// ========== 注册弹窗（保留原有逻辑）==========
const registerDialogVisible = ref(false)
const registerLoading = ref(false)
const registerFormRef = ref(null)

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const validatePassword = (rule, value, callback) => {
  const pattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]).{8,20}$/
  if (!pattern.test(value)) {
    callback(new Error('密码必须为8-20位，且同时包含大写字母、小写字母、数字、特殊符号'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字、下划线', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 20, message: '昵称长度为1-20位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const openRegisterDialog = () => {
  registerDialogVisible.value = true
}

const handleRegister = async () => {
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  registerLoading.value = true
  try {
    const res = await register({
      username: registerForm.username,
      nickname: registerForm.nickname,
      password: registerForm.password
    })
    if (res.code === 200) {
      ElMessage.success('注册成功，请登录')
      registerDialogVisible.value = false
      registerForm.username = ''
      registerForm.nickname = ''
      registerForm.password = ''
      registerForm.confirmPassword = ''
      loginDialogVisible.value = true
    } else {
      ElMessage.error(res.message || '注册失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '注册失败')
  } finally {
    registerLoading.value = false
  }
}

const handleRegisterDialogClose = () => {
  registerForm.username = ''
  registerForm.nickname = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  registerFormRef.value?.resetFields()
}

const switchToLogin = () => {
  registerDialogVisible.value = false
  loginDialogVisible.value = true
}

const switchToRegister = () => {
  loginDialogVisible.value = false
  registerDialogVisible.value = true
}

// ========== 退出登录 ==========
const handleLogout = () => {
  userStore.clearToken()
  ElMessage.success('已退出登录')
  window.location.reload()
}

// ========== 全局事件监听 ==========
const handleShowLogin = (event) => {
  loginDialogVisible.value = true
  if (event.detail?.message) {
    ElMessage.warning(event.detail.message)
  }
}

onMounted(() => {
  window.addEventListener('show-login', handleShowLogin)
  fetchCategories()
  fetchArticles()
})

onUnmounted(() => {
  window.removeEventListener('show-login', handleShowLogin)
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
          <span class="user-name">{{ userStore.userInfo.nickname }}</span>
          <span class="nav-link" @click="handleLogout">退出</span>
        </template>
        <template v-else>
          <span class="nav-link" @click="openLoginDialog">登录</span>
          <span class="nav-link nav-btn" @click="openRegisterDialog">注册</span>
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

    <!-- 登录弹窗 -->
    <el-dialog
      v-model="loginDialogVisible"
      title="用户登录"
      width="420px"
      align-center
      :close-on-click-modal="false"
      @closed="handleLoginDialogClose"
      class="auth-dialog"
    >
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="dialog-submit-btn"
            :loading="loginLoading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="dialog-footer">
        还没有账号？
        <span class="link-text" @click="switchToRegister">立即注册</span>
      </div>
    </el-dialog>

    <!-- 注册弹窗 -->
    <el-dialog
      v-model="registerDialogVisible"
      title="创建账号"
      width="420px"
      align-center
      :close-on-click-modal="false"
      @closed="handleRegisterDialogClose"
      class="auth-dialog"
    >
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="用户名（3-20位字母/数字/下划线）"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input
            v-model="registerForm.nickname"
            placeholder="昵称（1-20位）"
            size="large"
            prefix-icon="Avatar"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="密码（8-20位，需包含大小写+数字+特殊符号）"
            size="large"
            show-password
            prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            size="large"
            show-password
            prefix-icon="Key"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="dialog-submit-btn"
            :loading="registerLoading"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="dialog-footer">
        已有账号？
        <span class="link-text" @click="switchToLogin">立即登录</span>
      </div>
    </el-dialog>
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

/* 弹窗 */
.dialog-submit-btn {
  width: 100%;
}

.dialog-footer {
  text-align: center;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.5);
}

.link-text {
  color: #409eff;
  cursor: pointer;
}

.link-text:hover {
  text-decoration: underline;
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
.home .el-input__wrapper,
.home .el-select .el-input__wrapper {
  background: rgba(255, 255, 255, 0.05) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
}

.home .el-input__inner {
  color: #fff !important;
}

.home .el-input__inner::placeholder {
  color: rgba(255, 255, 255, 0.3) !important;
}
</style>
