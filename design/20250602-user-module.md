# 设计文档：用户模块

## 1. 需求概述

- **功能名称**：用户注册与登录
- **目标**：实现用户注册、登录功能，密码使用 BCrypt 加密，登录成功后返回用户信息；支持 ADMIN/USER 两种角色，注册时默认为 USER；管理员账号由数据库初始化脚本创建
- **涉及角色**：访客（注册/登录）、普通用户（USER）、管理员（ADMIN）

## 2. 接口设计

### 2.1 接口列表

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 用户注册 | POST | /user/register | 新用户注册 |
| 用户登录 | POST | /user/login | 用户登录验证 |

### 2.2 请求与响应

#### 接口1：用户注册

- **请求**：

```json
{
  "username": "zhangsan",
  "nickname": "张三",
  "password": "Abcd@1234"
}
```

- **请求参数校验**：
  - username：必填，3-20 位字母/数字/下划线
  - nickname：必填，1-20 位任意字符
  - password：必填，8-20 位，必须同时包含大写字母、小写字母、数字、特殊符号（`!@#$%^&*()` 等）

- **响应（成功）**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

- **响应（失败）**：

```json
{
  "code": 500,
  "message": "用户名已存在",
  "data": null
}
```

- **状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 注册成功 | 参数合法且用户名不重复 |
| 500 | 用户名已存在 | username 已存在于数据库 |
| 500 | 参数校验失败 | username/nickname/password 不符合规则 |

#### 接口2：用户登录

- **请求**：

```json
{
  "username": "zhangsan",
  "password": "Abcd@1234"
}
```

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

- **响应（失败）**：

```json
{
  "code": 500,
  "message": "用户名或密码错误",
  "data": null
}
```

- **状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 登录成功 | 用户名和密码匹配 |
| 500 | 用户名或密码错误 | 用户名不存在或密码不匹配 |
| 500 | 用户被禁用 | status = 0 |

## 3. 数据库设计

### 3.1 新增表

| 表名 | 说明 | 变更类型 |
|------|------|----------|
| user | 用户信息表 | 新增 |

### 3.2 字段定义

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 用户名 |
| password | VARCHAR(255) | NOT NULL | BCrypt 加密后的密码 |
| nickname | VARCHAR(50) | NOT NULL | 昵称 |
| avatar | VARCHAR(500) | NULL | 头像 URL |
| email | VARCHAR(100) | NULL | 邮箱 |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | 角色：ADMIN/USER |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：1-启用，0-禁用 |
| create_time | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

### 3.3 索引

| 索引名 | 字段 | 类型 | 说明 |
|--------|------|------|------|
| uk_username | username | UNIQUE | 用户名唯一 |

## 4. 核心逻辑

### 4.1 注册业务流程

1. 接收注册参数（username, nickname, password）
2. 参数校验（长度、格式）
3. 查询数据库，检查 username 是否已存在
4. 若存在，返回"用户名已存在"
5. 使用 BCrypt 对 password 进行加密（强度因子 10）
6. 插入 user 表，role 默认 USER，status 默认 1
7. 返回成功

### 4.2 登录业务流程

1. 接收登录参数（username, password）
2. 根据 username 查询用户
3. 若用户不存在，返回"用户名或密码错误"
4. 若 status = 0，返回"用户已被禁用"
5. 使用 BCrypt 比对 password
6. 若不匹配，返回"用户名或密码错误"
7. 返回用户信息（不含 password 字段）

### 4.3 异常处理

| 异常场景 | 处理方式 | 返回结果 |
|----------|----------|----------|
| 用户名已存在 | Service 层抛出自定义异常 | Result.error("用户名已存在") |
| 用户名或密码错误 | Service 层抛出自定义异常 | Result.error("用户名或密码错误") |
| 用户被禁用 | Service 层抛出自定义异常 | Result.error("用户已被禁用") |
| 参数校验失败 | Controller 层参数校验 | Result.error("参数校验失败：xxx") |

## 5. 安全考虑

- [x] 密码存储：使用 BCrypt 加密，强度因子 10
- [x] 密码复杂度：8-20 位，必须同时包含大写字母、小写字母、数字、特殊符号（正则：`^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).{8,20}$`）
- [x] 输入验证：username 3-20 位字母数字下划线，nickname 1-20 位
- [x] SQL 注入防护：使用 MyBatis #{} 预编译参数
- [x] 用户名唯一性校验：数据库 UNIQUE 约束 + 代码层校验
- [x] 登录响应不返回 password 字段
- [x] 权限管理：登录返回 role 字段，前端根据 role 控制页面访问；管理员接口后端校验 role = ADMIN
- [x] 注册限制：接口固定 role 为 USER，禁止普通用户注册为管理员

## 6. 前端交互

### 6.1 注册页面

- 路由路径：`/register`
- 页面组件：`RegisterView.vue`
- Element Plus 组件：`el-form`, `el-input`, `el-button`
- 表单字段：用户名、昵称、密码、确认密码
- 前端校验：
  - 用户名：3-20 位字母数字下划线
  - 昵称：1-20 位
  - 密码：8-20 位，必须同时包含大写字母、小写字母、数字、特殊符号
  - 确认密码：与密码一致

### 6.2 登录页面

- 路由路径：`/login`
- 页面组件：`LoginView.vue`
- Element Plus 组件：`el-form`, `el-input`, `el-button`
- 表单字段：用户名、密码
- 登录成功后：将用户信息保存到 Pinia Store

### 6.3 与后端的交互流程

```
用户填写表单 → 前端校验 → 调用 API → 后端校验 → 数据库操作 → 返回结果 → 前端处理
```

## 7. Redis / 缓存设计

不涉及。

## 8. 任务拆分

| 序号 | 子任务 | 预计耗时 | 依赖 |
|------|--------|----------|------|
| 1 | 创建 user 表（SQL） | 5 min | 无 |
| 2 | 创建 User 实体类 | 5 min | 1 |
| 3 | 创建 UserMapper + XML | 10 min | 2 |
| 4 | 创建 UserService（注册/登录逻辑） | 15 min | 3 |
| 5 | 创建 UserController | 10 min | 4 |
| 6 | Postman 测试注册/登录接口 | 10 min | 5 |
| 7 | 创建注册页面 | 15 min | 无 |
| 8 | 创建登录页面 | 15 min | 无 |
| 9 | 配置路由（/login, /register） | 5 min | 7, 8 |
| 10 | 前后端联调 | 10 min | 6, 9 |

---

**确认状态**：
- [x] 待评审
- [ ] 已确认
- [ ] 需要修改
