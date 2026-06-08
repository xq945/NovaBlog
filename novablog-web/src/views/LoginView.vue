<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/user'
import { useUserStore } from '../stores'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const username = ref('')
const password = ref('')
const errors = ref({ username: '', password: '' })

const validate = () => {
  errors.value = { username: '', password: '' }
  let valid = true

  if (!username.value.trim()) {
    errors.value.username = '请输入用户名'
    valid = false
  } else if (username.value.length < 3 || username.value.length > 20) {
    errors.value.username = '用户名长度为3-20位'
    valid = false
  }

  if (!password.value) {
    errors.value.password = '请输入密码'
    valid = false
  } else if (password.value.length < 8 || password.value.length > 20) {
    errors.value.password = '密码长度为8-20位'
    valid = false
  }

  return valid
}

const handleLogin = async () => {
  if (!validate()) return

  loading.value = true
  try {
    const res = await login({
      username: username.value,
      password: password.value
    })
    if (res.code === 200) {
      ElMessage.success('登录成功')
      userStore.userInfo = res.data
      router.push('/')
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="title">NovaBlog</h1>
      <p class="subtitle">欢迎回来</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-item">
          <input
            v-model="username"
            type="text"
            placeholder="用户名"
            class="form-input"
            @keyup.enter="handleLogin"
          />
          <span v-if="errors.username" class="error-msg">{{ errors.username }}</span>
        </div>

        <div class="form-item">
          <input
            v-model="password"
            type="password"
            placeholder="密码"
            class="form-input"
            @keyup.enter="handleLogin"
          />
          <span v-if="errors.password" class="error-msg">{{ errors.password }}</span>
        </div>

        <button
          type="submit"
          class="submit-btn"
          :disabled="loading"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="footer">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

.login-card {
  width: 420px;
  padding: 48px 40px;
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 25px 80px rgba(0, 0, 0, 0.4);
}

.title {
  font-size: clamp(2rem, 5vw, 2.8rem);
  color: #fff;
  margin: 0 0 8px 0;
  text-align: center;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.subtitle {
  color: rgba(255, 255, 255, 0.5);
  text-align: center;
  margin: 0 0 36px 0;
  font-size: 15px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-input {
  width: 100%;
  height: 48px;
  padding: 0 16px;
  font-size: 15px;
  color: #fff;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  outline: none;
  box-sizing: border-box;
  transition: all 0.2s;
}

.form-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.form-input:hover {
  border-color: rgba(255, 255, 255, 0.3);
}

.form-input:focus {
  border-color: #409eff;
  background: rgba(0, 0, 0, 0.35);
}

.error-msg {
  color: #f56c6c;
  font-size: 13px;
  padding-left: 4px;
}

.submit-btn {
  width: 100%;
  height: 48px;
  margin-top: 8px;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
  background: #409eff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}

.submit-btn:hover {
  background: #66b1ff;
}

.submit-btn:disabled {
  background: rgba(64, 158, 255, 0.5);
  cursor: not-allowed;
}

.footer {
  text-align: center;
  margin-top: 28px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
}

.footer a {
  color: #409eff;
  text-decoration: none;
  margin-left: 4px;
}

.footer a:hover {
  text-decoration: underline;
}
</style>
