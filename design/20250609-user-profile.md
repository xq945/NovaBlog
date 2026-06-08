# 设计文档：个人中心模块

## 1. 需求概述

- **功能名称**：个人中心
- **目标**：实现用户个人信息查询与修改，提供个人信息展示和文章管理入口
- **涉及角色**：登录用户（查询/修改自己的信息）
- **设计决策**：个人信息修改限制为昵称和邮箱，用户名和角色不可修改；头像修改依赖后续文件上传模块，本次预留占位

## 2. 接口设计

### 2.1 接口列表

| 接口 | 方法 | 路径 | 权限 |
|------|------|------|------|
| 查询个人信息 | GET | /user/profile | 登录用户 |
| 修改个人信息 | PUT | /user/profile | 登录用户 |

> 查询接口已存在，本次新增修改接口。

### 2.2 DTO

**UpdateProfileDTO**

| 字段 | 类型 | 说明 | 校验规则 |
|------|------|------|----------|
| nickname | String | 昵称 | 必填，1-20位 |
| email | String | 邮箱 | 可选，长度0-100位，符合邮箱格式 |

### 2.3 请求与响应

#### 查询个人信息（已有接口，扩展返回字段）

**请求**：`GET /user/profile`

**响应**：`Result<Map<String, Object>>`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "testuser",
    "nickname": "测试用户",
    "avatar": null,
    "email": "test@example.com",
    "role": "USER",
    "createTime": "2025-06-01 10:00:00"
  }
}
```

> **变更**：新增 `createTime` 字段返回，供前端展示注册时间。Controller 中 `profile()` 方法需补充 `userInfo.put("createTime", user.getCreateTime())`。

#### 修改个人信息（新增）

**请求**：`PUT /user/profile`

```json
{
  "nickname": "新昵称",
  "email": "new@example.com"
}
```

**响应**：`Result<Void>`

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 成功 | — |
| 400 | 参数错误 | 昵称为空/超长，邮箱格式错误 |
| 401 | 未登录 | — |

## 3. 数据库变更

无需新增表或字段，已有 `user` 表字段已覆盖需求：
- `nickname` — 昵称
- `email` — 邮箱
- `avatar` — 头像URL（本次预留，后续文件上传模块实现）

### 3.1 Mapper 新增方法

**UserMapper 接口新增**：

```java
// 更新用户个人信息（nickname 必填，email 可为 null 表示清空）
int updateProfile(@Param("id") Long id,
                  @Param("nickname") String nickname,
                  @Param("email") String email);
```

**UserMapper.xml 新增 SQL**：

```xml
<update id="updateProfile">
    UPDATE user
    SET nickname = #{nickname},
        email = #{email}
    WHERE id = #{id}
</update>
```

> **全字段更新说明**：直接更新 nickname 和 email 两个字段。email 传入 null 时表示清空邮箱，传入原值时保持不变。不通过影响行数判断成功与否，避免 MySQL 值未变化时返回 0 的误判。
>
> **update_time**：数据库已配置 `on update CURRENT_TIMESTAMP`，无需手动设置。

## 4. 后端实现

### 4.1 Service 接口扩展

在 `UserService` 中新增方法：

```java
/**
 * 修改当前登录用户的个人信息
 *
 * @param nickname 昵称
 * @param email    邮箱
 */
void updateProfile(String nickname, String email);
```

### 4.2 Service 实现逻辑

`UserServiceImpl.updateProfile`：

1. **参数校验**：
   - `nickname`：非空，长度 1-20
   - `email`：非空时校验格式（`^[^\s@]+@[^\s@]+\.[^\s@]+$`），长度不超过 100

2. **获取当前用户ID**：从 `UserContext` 获取

3. **校验用户状态**：查询数据库确认用户存在且未被禁用
   - 用户不存在 → 401 "用户不存在"
   - 用户被禁用（status = 0）→ 401 "用户已被禁用"

4. **执行更新**：调用 `userMapper.updateProfile`
   - 不检查影响行数（MySQL 值未变化时返回 0，属正常行为）

5. **返回成功**

> 不涉及事务（单条 UPDATE），无需 `@Transactional`。

### 4.3 Controller 新增接口

```java
/**
 * 修改个人信息
 *
 * @param updateProfileDTO 修改参数
 * @return 成功结果
 */
@PutMapping("/profile")
public Result<Void> updateProfile(@RequestBody UpdateProfileDTO updateProfileDTO) {
    userService.updateProfile(updateProfileDTO.getNickname(), updateProfileDTO.getEmail());
    return Result.success();
}
```

## 5. 核心逻辑

### 5.1 修改个人信息流程

1. **参数校验**：
   - nickname 为空 → 400 "昵称不能为空"
   - nickname 长度不在 1-20 范围内 → 400 "昵称长度必须为1-20位"
   - email 非空且长度 > 100 → 400 "邮箱长度不能超过100位"
   - email 非空但格式非法 → 400 "邮箱格式不正确"

2. **获取用户ID**：从 `UserContext.getUserId()` 获取
   - 未登录 → 401（由拦截器处理，理论上不会到达此处）

3. **校验用户状态**：
   - 查询用户不存在 → 401 "用户不存在"
   - 用户 status = 0 → 401 "用户已被禁用"

4. **执行更新**：调用 Mapper 的 `updateProfile`
   - 不检查影响行数（MySQL 值未变化时返回 0，属正常行为）

5. **返回成功**

### 5.2 异常处理

| 异常场景 | 返回结果 |
|----------|----------|
| nickname 为空 | 400 "昵称不能为空" |
| nickname 长度 > 20 | 400 "昵称长度必须为1-20位" |
| email 长度 > 100 | 400 "邮箱长度不能超过100位" |
| email 格式错误 | 400 "邮箱格式不正确" |
| 用户不存在 | 401 "用户不存在" |
| 用户被禁用 | 401 "用户已被禁用" |
| 未登录 | 401 "请先登录" |

## 6. 安全考虑

### 6.1 权限控制

| 接口 | 权限要求 | 说明 |
|------|----------|------|
| GET /user/profile | 登录用户 | 只能查询自己的信息 |
| PUT /user/profile | 登录用户 | 只能修改自己的信息 |

> 通过 `UserContext` 获取当前用户ID，不依赖请求参数中的用户ID，防止越权修改他人信息。

### 6.2 输入验证

- nickname：长度限制，防止超长字符串注入
- email：正则校验，防止非法格式

### 6.3 不可修改字段

以下字段**不允许**通过本接口修改：
- `username` — 用户唯一标识，注册后不可变更
- `role` — 权限角色，仅管理员可修改
- `avatar` — 依赖文件上传模块，后续单独实现
- `password` — 密码修改需单独接口（旧密码验证+新密码确认）

## 7. 前端交互

### 7.1 页面结构

ProfileView.vue 改造为双区域布局：

```
个人中心页面
├── 顶部导航（已有）
├── 个人信息卡片（新增）
│   ├── 头像区域（预留，显示默认头像或首字母）
│   ├── 昵称（展示/编辑模式切换）
│   ├── 用户名（只读）
│   ├── 邮箱（展示/编辑模式切换）
│   ├── 角色标签（只读）
│   ├── 注册时间（只读）
│   └── 编辑/保存/取消按钮
└── 我的文章列表（已有，下移）
```

### 7.2 状态管理

| 状态 | 类型 | 说明 |
|------|------|------|
| profile | Ref<Object> | 当前用户个人信息 |
| editing | Ref<boolean> | 是否处于编辑模式 |
| editForm | Reactive<Object> | 编辑表单数据（nickname、email） |
| saving | Ref<boolean> | 保存中 loading |
| loading | Ref<boolean> | 个人信息加载中 |

### 7.3 交互流程

1. **页面加载**：
   - 调用 `GET /user/profile` 加载个人信息
   - 初始化 `editForm` 为当前个人信息副本

2. **点击编辑**：
   - `editing = true`
   - 复制当前信息到 `editForm`

3. **点击保存**：
   - `saving = true`
   - 调用 `PUT /user/profile`
   - 成功：`editing = false`，刷新 profile 数据，`ElMessage.success('保存成功')`
   - 失败：`ElMessage.error` 显示错误
   - `saving = false`

4. **点击取消**：
   - `editing = false`
   - 恢复 `editForm` 为原始数据

5. **修改成功后同步全局状态**：
   - 调用 `userStore.setUserInfo()` 更新全局用户信息
   - 确保导航栏等位置显示最新昵称

### 7.4 API 模块

```js
// api/user.js 中新增
export function updateProfile(data) {
  return request({
    url: '/user/profile',
    method: 'put',
    data
  })
}
```

### 7.5 头像占位处理

本次不实现头像上传，头像区域显示规则：
- 有 avatar URL：显示头像图片
- 无 avatar：显示昵称首字母的默认头像（使用 Element Plus 的 `el-avatar` 的默认插槽）

## 8. 任务拆分

| 序号 | 子任务 | 依赖 | 说明 |
|------|--------|------|------|
| 1 | 新增 UpdateProfileDTO | — | 修改个人信息请求参数 |
| 2 | UserMapper 新增 updateProfile | — | XML 中新增 UPDATE SQL（全字段更新） |
| 3 | UserService 扩展 updateProfile | 1 | 参数校验 + 调用 Mapper |
| 4 | UserController 新增 PUT /user/profile | 1,3 | 控制器接口 |
| 5 | 后端 Postman 测试 | 4 | 正常/参数错误/未登录 |
| 6 | 前端 API 模块扩展 | — | user.js 新增 updateProfile |
| 7 | 前端 ProfileView 改造 | 6 | 个人信息卡片 + 编辑模式 |
| 8 | 前后端联调 | 5,7 | 完整流程验证 |
| 9 | Git 提交 | 8 | — |

## 9. 补充说明

### 9.1 前后端数据结构对齐

**查询响应**：扩展返回 `createTime` 字段，其余字段保持不变

**修改请求**：
- 后端 `UpdateProfileDTO` 字段：`nickname` (String)、`email` (String)
- 前端 `editForm` 字段：`nickname`、`email`

**修改响应**：
- 成功：`Result.success()`，code=200
- 前端收到 200 后重新调用 `GET /user/profile` 刷新数据

### 9.2 email 为 null 的处理

前端编辑表单中，email 输入框清空后提交时：
- 方案：编辑表单中 email 初始值为 `profile.email || ''`，提交时若为空字符串则转为 `null`
- 后端接收 null 时，SQL 直接设置 `email = null`，数据库中的邮箱被清空
- 这样用户可以自由修改、清空邮箱，逻辑直观一致

### 9.3 全局状态同步

修改个人信息成功后，需要同步更新 `userStore.userInfo`，确保：
- 导航栏显示最新昵称
- 其他引用用户信息的地方数据一致

### 9.4 头像功能预留

头像上传功能依赖文件上传模块（第九阶段），本次仅预留展示区域：
- 头像区域使用 `el-avatar` 组件
- 有 avatar 时显示图片，无 avatar 时显示昵称首字母
- 头像区域不显示上传按钮，等文件上传模块实现后再添加

---

**确认状态**：
- [x] 待评审
- [ ] 已确认
- [ ] 需要修改
