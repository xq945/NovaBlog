import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores'
import { getProfile } from '../api/user'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/HomeView.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/RegisterView.vue')
  },
  {
    path: '/article/:id',
    name: 'ArticleDetail',
    component: () => import('../views/ArticleDetailView.vue')
  },
  {
    path: '/article/create',
    name: 'ArticleCreate',
    component: () => import('../views/ArticleEditView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/article/edit/:id',
    name: 'ArticleEdit',
    component: () => import('../views/ArticleEditView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let isLoadingUserInfo = false
let pendingUserInfoPromise = null

/**
 * 确保用户信息已加载，避免路由守卫中重复请求
 */
async function ensureUserInfo() {
  const userStore = useUserStore()
  if (userStore.userInfo) {
    return userStore.userInfo
  }

  if (isLoadingUserInfo && pendingUserInfoPromise) {
    return pendingUserInfoPromise
  }

  isLoadingUserInfo = true
  pendingUserInfoPromise = getProfile()
    .then((res) => {
      if (res.code === 200) {
        userStore.setUserInfo(res.data)
        return res.data
      }
      throw new Error(res.message || '获取用户信息失败')
    })
    .finally(() => {
      isLoadingUserInfo = false
      pendingUserInfoPromise = null
    })

  return pendingUserInfoPromise
}

/**
 * 路由守卫：需要登录/ADMIN权限的页面拦截
 */
router.beforeEach(async (to, from, next) => {
  const requiresAuth = to.meta.requiresAuth || to.meta.requiresAdmin

  // 1. 同步检查 Token 是否存在
  if (requiresAuth) {
    const token = localStorage.getItem('token')
    if (!token) {
      return next('/login')
    }
  }

  // 2. 需要管理员权限时，确保用户信息已加载
  if (to.meta.requiresAdmin) {
    try {
      const userInfo = await ensureUserInfo()
      if (userInfo?.role !== 'ADMIN') {
        return next('/')
      }
    } catch (error) {
      const userStore = useUserStore()
      userStore.clearToken()
      return next('/login')
    }
  }

  next()
})

export default router
