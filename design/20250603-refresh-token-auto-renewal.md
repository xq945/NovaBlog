# 设计文档：Refresh Token 自动续期

## 1. 需求概述

- **功能名称**：Refresh Token 自动续期
- **目标**：解决 Access Token 过期（2小时）后用户被强制登出的问题，实现无感知续期
- **背景**：当前后端已提供 `/auth/refresh` 接口，前端已保存 `refreshToken`，但尚未实现自动调用逻辑
- **涉及角色**：所有已登录用户

## 2. 接口设计

### 2.1 接口变更

| 接口 | 变更类型 | 说明 |
|------|----------|------|
| POST /auth/refresh | 修复 | 生成新 Access Token 时需从数据库查询完整用户信息 |
| GET /user/profile | 新增 | 获取当前登录用户信息（用于 App.vue 启动时验证 token 有效性）|

### 2.2 POST /auth/refresh（修复后）

```http
POST /auth/refresh
```

- **请求头**：`Authorization: Bearer {refreshToken}`
- **说明**：该接口**不走 JWT 拦截器**（已放行），由 AuthController 自己验证 Refresh Token
- **响应（成功）**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 7200,
    "userInfo": {
      "id": 1,
      "username": "zhangsan",
      "nickname": "张三",
      "avatar": null,
      "email": null,
      "role": "USER"
    }
  }
}
```

**修复点**：原实现中 `userDTO.setUsername("")` 导致新 Access Token 缺少用户名和角色信息，需改为从数据库查询完整用户后生成 Token。

- **响应（Refresh Token 过期）**：

```json
{
  "code": 401,
  "message": "登录已过期，请重新登录",
  "data": null
}
```

- **响应（Refresh Token 无效/类型错误）**：

```json
{
  "code": 401,
  "message": "Token 无效",
  "data": null
}
```

### 2.3 GET /user/profile（新增）

```http
GET /user/profile
```

- **请求头**：`Authorization: Bearer {accessToken}`
- **说明**：获取当前登录用户的完整信息，用于 App.vue 启动时验证 token 是否仍然有效
- **响应（成功）**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "nickname": "张三",
    "avatar": null,
    "email": null,
    "role": "USER"
  }
}
```

- **响应（Token 过期/无效）**：返回 401，走标准 JWT 拦截器逻辑

## 3. 数据库设计

不涉及表变更。

## 4. 核心逻辑

### 4.1 后端：/auth/refresh 修复流程

```
收到 POST /auth/refresh
  ↓
从 Authorization 头提取 refreshToken
  ↓
验证 token 类型 == "refresh"
  ├─ 否 → 返回 401
  ↓
解析 token 获取 userId
  ↓
根据 userId 从数据库查询完整用户信息
  ↓
生成新的 Access Token（含完整 username, role）
  ↓
生成新的 Refresh Token
  ↓
返回 { token, refreshToken, expiresIn, userInfo }
```

### 4.2 后端：GET /user/profile 流程

```
收到 GET /user/profile
  ↓
JWT 拦截器校验 Access Token
  ↓
从 ThreadLocal 获取 userId
  ↓
根据 userId 从数据库查询用户信息（不含密码）
  ↓
返回 userInfo
```

### 4.3 前端：Axios 响应拦截器（自动续期核心）

```
API 请求返回 401
  ↓
判断 refreshToken 是否存在
  ├─ 否 → 清除登录状态，弹出登录框
  ↓
判断是否正在刷新中
  ├─ 是 → 将当前请求加入待重试队列
  ├─ 否 → 开始刷新流程
  ↓
调用 POST /auth/refresh（携带 refreshToken）
  ↓
刷新成功？
  ├─ 是 → 更新 Pinia 中的 token / refreshToken / userInfo
  │        重试待处理队列中的所有请求
  │        重试当前请求
  └─ 否 → 清除登录状态，弹出登录框
```

### 4.4 前端：App.vue 启动验证

```
应用启动（App.vue onMounted）
  ↓
localStorage 中 token 是否存在？
  ├─ 否 → 不做任何操作
  ↓
恢复 token 到 Pinia Store
  ↓
调用 GET /user/profile 验证 token 有效性
  ↓
返回 200？
  ├─ 是 → 更新 userInfo，保持登录状态
  └─ 否（401）→ 清除登录状态，显示未登录
```

**注意**：App.vue 启动时不验证是为了避免阻塞页面渲染。如果用户访问的页面不需要登录，不应强制验证。实际方案是：恢复 token 到 Store，让后续 API 请求自然触发续期或 401 处理。

### 4.5 并发刷新保护机制

**问题场景**：多个 API 请求同时返回 401，如果不加保护会并发发送多个 refresh 请求。

**解决方案**：

```javascript
let isRefreshing = false        // 标记是否正在刷新
let refreshSubscribers = []     // 待重试请求队列

// 刷新完成后，通知队列中所有请求重试
function onTokenRefreshed(newToken) {
  refreshSubscribers.forEach(callback => callback(newToken))
  refreshSubscribers = []
}

// 将请求加入待重试队列
function addRefreshSubscriber(callback) {
  refreshSubscribers.push(callback)
}
```

## 5. 安全考虑

- [x] **Token 刷新频率限制**：Refresh Token 本身 7 天过期，每次刷新同时换发新的 Refresh Token（旋转机制），降低被盗风险
- [x] **并发保护**：多个请求同时 401 时只发一个 refresh 请求，避免滥用
- [x] **Refresh Token 类型校验**：后端严格校验 token type 必须为 "refresh"，防止用 Access Token 冒充
- [x] **刷新失败立即清除**：refresh 返回 401 时，前端立即清除所有 token，强制重新登录
- [x] **用户信息同步**：刷新成功后返回最新 userInfo，保证前端显示的用户信息是最新的

## 6. 前端交互

### 6.1 前端 Axios 拦截器改造

```javascript
// 响应拦截器
request.interceptors.response.use(
  response => response.data,
  async error => {
    const originalRequest = error.config
    const res = error.response?.data

    // 401 且不是刷新请求本身
    if (res?.code === 401 && !originalRequest._isRefreshRequest) {
      const userStore = useUserStore()

      // 没有 refreshToken，直接清除状态
      if (!userStore.refreshToken) {
        userStore.clearToken()
        window.dispatchEvent(new CustomEvent('show-login', {
          detail: { message: res.message || '登录已过期，请重新登录' }
        }))
        return Promise.reject(error)
      }

      // 正在刷新中，加入队列等待
      if (isRefreshing) {
        return new Promise(resolve => {
          addRefreshSubscriber(newToken => {
            originalRequest.headers.Authorization = 'Bearer ' + newToken
            resolve(request(originalRequest))
          })
        })
      }

      // 开始刷新
      isRefreshing = true
      originalRequest._isRefreshRequest = true

      try {
        const refreshRes = await request.post('/auth/refresh', null, {
          headers: { Authorization: 'Bearer ' + userStore.refreshToken }
        })

        if (refreshRes.code === 200) {
          const { token, refreshToken, userInfo } = refreshRes.data
          userStore.setToken(token, refreshToken)
          userStore.setUserInfo(userInfo)

          // 重试队列中所有请求
          onTokenRefreshed(token)

          // 重试当前请求
          originalRequest.headers.Authorization = 'Bearer ' + token
          return request(originalRequest)
        }
      } catch (refreshError) {
        // 刷新失败，清除状态
        userStore.clearToken()
        window.dispatchEvent(new CustomEvent('show-login', {
          detail: { message: '登录已过期，请重新登录' }
        }))
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)
```

### 6.2 前端 API 模块新增

`api/user.js` 新增：

```javascript
// 获取当前登录用户信息
export const getProfile = () => request.get('/user/profile')
```

### 6.3 App.vue 启动恢复逻辑（调整）

```javascript
onMounted(async () => {
  const token = localStorage.getItem('token')
  const refreshToken = localStorage.getItem('refreshToken')

  if (token && refreshToken) {
    userStore.token = token
    userStore.refreshToken = refreshToken
    // userInfo 不再从 localStorage 恢复，避免显示过期状态
    // 第一次需要用户信息的 API 调用会自然触发续期或 401 处理
  }
})
```

**调整说明**：`userInfo` 不再在 App.vue 启动时从 localStorage 恢复。因为即使 token 过期，localStorage 中的 `userInfo` 仍然存在，会导致页面错误显示登录状态。改为仅在登录成功或刷新成功时写入 `userInfo`，token 过期后页面正确显示未登录。

### 6.4 Pinia Store 调整

```javascript
const useUserStore = defineStore('user', () => {
  const token = ref('')
  const refreshToken = ref('')
  const userInfo = ref(null)

  const setToken = (newToken, newRefreshToken) => {
    token.value = newToken
    refreshToken.value = newRefreshToken
    localStorage.setItem('token', newToken)
    localStorage.setItem('refreshToken', newRefreshToken)
  }

  const setUserInfo = (info) => {
    userInfo.value = info
    // userInfo 不再持久化到 localStorage
  }

  const clearToken = () => {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
  }

  return { token, refreshToken, userInfo, setToken, setUserInfo, clearToken }
})
```

## 7. Redis / 缓存设计

不涉及。

## 8. 任务拆分

| 序号 | 子任务 | 预计耗时 | 依赖 |
|------|--------|----------|------|
| 1 | 修复后端 /auth/refresh：从数据库查询完整用户信息 | 15 min | 无 |
| 2 | 后端新增 GET /user/profile 接口 | 15 min | 无 |
| 3 | Postman 测试 /auth/refresh 和 /user/profile | 10 min | 1, 2 |
| 4 | 前端改造 Pinia Store：userInfo 不持久化到 localStorage | 5 min | 无 |
| 5 | 前端改造 Axios 响应拦截器：实现自动续期 + 并发保护 | 20 min | 4 |
| 6 | 前端新增 /user/profile API 调用 | 5 min | 2 |
| 7 | 调整 App.vue 启动恢复逻辑 | 5 min | 4 |
| 8 | 前后端联调测试 | 15 min | 3, 5, 6, 7 |

---

**确认状态**：
- [x] 待评审
- [ ] 已确认
- [ ] 需要修改
