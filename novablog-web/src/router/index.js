import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useUserStore } from '../stores'
import { getProfile } from '../api/user'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: HomeView
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

/**
 * 路由守卫：需要登录/ADMIN权限的页面拦截
 */
router.beforeEach(async (to, from, next) => {
  if (to.meta.requiresAuth || to.meta.requiresAdmin) {
    const token = localStorage.getItem('token')
    if (!token) {
      return next('/login')
    }
  }

  if (to.meta.requiresAdmin) {
    const userStore = useUserStore()
    if (!userStore.userInfo) {
      try {
        const res = await getProfile()
        if (res.code === 200) {
          userStore.setUserInfo(res.data)
        } else {
          userStore.clearToken()
          return next('/login')
        }
      } catch (error) {
        userStore.clearToken()
        return next('/login')
      }
    }
    if (userStore.userInfo?.role !== 'ADMIN') {
      return next('/')
    }
  }

  next()
})

export default router
