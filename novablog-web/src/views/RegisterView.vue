<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/user'

const router = useRouter()
const loading = ref(false)

const form = ref({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const errors = ref({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const validate = () => {
  errors.value = { username: '', nickname: '', password: '', confirmPassword: '' }
  let valid = true

  if (!form.value.username.trim()) {
    errors.value.username = '请输入用户名'
    valid = false
  } else if (form.value.username.length < 3 || form.value.username.length > 20) {
    errors.value.username = '用户名长度为3-20位'
    valid = false
  } else if (!/^[a-zA-Z0-9_]+$/.test(form.value.username)) {
    errors.value.username = '用户名只能包含字母、数字、下划线'
    valid = false
  }

  if (!form.value.nickname.trim()) {
    errors.value.nickname = '请输入昵称'
    valid = false
  } else if (form.value.nickname.length < 1 || form.value.nickname.length > 20) {
    errors.value.nickname = '昵称长度为1-20位'
    valid = false
  }

  const pwdPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]).{8,20}$/
  if (!form.value.password) {
    errors.value.password = '请输入密码'
    valid = false
  } else if (!pwdPattern.test(form.value.password)) {
    errors.value.password = '密码需8-20位，包含大小写+数字+特殊符号'
    valid = false
  }

  if (form.value.confirmPassword !== form.value.password) {
    errors.value.confirmPassword = '两次输入的密码不一致'
    valid = false
  }

  return valid
}

const handleRegister = async () => {
  if (!validate()) return

  loading.value = true
  try {
    const res = await register({
      username: form.value.username,
      nickname: form.value.nickname,
      password: form.value.password
    })
    if (res.code === 200) {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } else {
      ElMessage.error(res.message || '注册失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-card">
      <h1 class="title">创建账号</h1>
      <p class="subtitle">加入 NovaBlog</p>

      <form class="register-form" @submit.prevent="handleRegister">
        <div class="form-item">
          <input
            v-model="form.username"
            type="text"
            placeholder="用户名（3-20位字母/数字/下划线）"
            class="form-input"
          />
          <span v-if="errors.username" class="error-msg">{{ errors.username }}</span>
        </div>

        <div class="form-item">
          <input
            v-model="form.nickname"
            type="text"
            placeholder="昵称（1-20位）"
            class="form-input"
          />
          <span v-if="errors.nickname" class="error-msg">{{ errors.nickname }}</span>
        </div>

        <div class="form-item">
          <input
            v-model="form.password"
            type="password"
            placeholder="密码（8-20位，需包含大小写+数字+特殊符号）"
            class="form-input"
          />
          <span v-if="errors.password" class="error-msg">{{ errors.password }}</span>
        </div>

        <div class="form-item">
          <input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            class="form-input"
          />
          <span v-if="errors.confirmPassword" class="error-msg">{{ errors.confirmPassword }}</span>
        </div>

        <button
          type="submit"
          class="submit-btn"
          :disabled="loading"
        >
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>

      <div class="footer">
        已有账号？
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

.register-card {
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

.register-form {
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
