# 设计文档：文章模块

## 1. 需求概述

- **功能名称**：文章 CRUD
- **目标**：实现文章的发布、列表查询、详情查看、编辑、删除功能
- **涉及角色**：访客（查看列表/详情）、登录用户（发布/编辑/删除自己的文章）、管理员（编辑/删除任意文章）

## 2. 接口设计

### 2.1 接口列表

> **认证说明**：所有标记为"登录用户"的接口，请求头需携带 `Authorization: Bearer {accessToken}`。Token 由 `/user/login` 接口获取，有效期 2 小时。Token 过期后需重新登录获取。

| 接口 | 方法 | 路径 | 权限 |
|------|------|------|------|
| 发布文章 | POST | /article | 登录用户 |
| 查询文章列表 | GET | /article/list | 公开 |
| 查询文章详情 | GET | /article/{id} | 公开 |
| 修改文章 | PUT | /article | 登录用户（自己或管理员） |
| 删除文章 | DELETE | /article/{id} | 登录用户（自己或管理员） |
| 查询分类列表 | GET | /category/list | 公开 |
| 查询标签列表 | GET | /tag/list | 公开 |
| 我的文章 | GET | /user/article/list | 登录用户 |

### 2.2 DTO/VO 定义

**ArticleDTO**（发布/修改请求）：
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 文章ID（修改时必填） |
| title | String | 标题 |
| content | String | 正文（Markdown） |
| summary | String | 摘要（为空自动生成） |
| cover | String | 封面URL |
| categoryId | Long | 分类ID |
| tagIds | List<Long> | 标签ID列表，最多 10 个 |
| status | Integer | 状态：1-已发布（默认）0-草稿，可选 |

**ArticleVO**（列表项）：
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 文章ID |
| title | String | 标题 |
| summary | String | 摘要 |
| cover | String | 封面 |
| viewCount | Integer | 浏览量 |
| likeCount | Integer | 点赞数 |
| status | Integer | 状态：1-已发布 0-草稿（公开列表中该字段恒为 1，我的文章列表返回实际状态） |
| author | UserVO | 作者信息（id, nickname, avatar） |
| category | CategoryVO | 分类信息（id, name） |
| tags | List<String> | 标签名称列表 |
| createTime | LocalDateTime | 创建时间（JSON序列化后显示为 yyyy-MM-dd HH:mm:ss 格式字符串） |

**ArticleDetailVO**（详情）：在 ArticleVO 基础上增加 `content`、`updateTime`，并将 `tags` 替换为 `List<TagVO>`（含标签ID和名称，用于编辑回显）

**TagVO**：
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 标签ID |
| name | String | 标签名称 |

### 2.3 请求与响应

#### 接口1：发布文章

- **请求**：

```json
{
  "title": "文章标题",
  "content": "# Markdown正文\n\n内容...",
  "summary": "文章摘要",
  "cover": "https://xxx.jpg",
  "categoryId": 1,
  "tagIds": [1, 2, 3]
}
```

- **请求参数校验**：
  - title：必填，1-100 字符
  - content：必填，最大 50000 字符
  - summary：可选，最大 500 字符；为空时自动从 content 截取前 150 字符（去除 Markdown 标记）
  - cover：可选，URL 格式（需以 http:// 或 https:// 开头）。**本阶段不实现文件上传**，封面图 URL 由用户手动填写外部图床链接
  - categoryId：必填，需校验该分类在数据库中存在
  - tagIds：可选，数组，最多 10 个标签，超出部分截断，自动去重并过滤 null 元素和无效标签ID
  - status：可选，0-草稿 1-已发布（默认 1），传入其他值时视为 1

- **响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": 5
}
```

#### 接口2：查询文章列表

- **请求**：`GET /article/list?page=1&size=10&categoryId=1&keyword=xxx`

- **请求参数**：
  - page：页码，默认 1，最小 1，page ≤ 0 时自动修正为 1
  - size：每页数量，默认 10，最小 1，最大 50，size ≤ 0 时自动修正为 10，size > 50 时自动修正为 50
  - categoryId：分类筛选，可选
  - keyword：关键词搜索（标题/摘要），可选，最大 50 字符，去除首尾空格后过滤掉 `%`、`_`、`*` 等 SQL 通配符，再使用 `LIKE '%keyword%'` 模糊匹配

- **筛选规则**：只查询 `status = 1`（已发布）的文章，草稿不对外显示
- **排序规则**：默认按 `create_time DESC`（最新发布在前）

- **响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "list": [
      {
        "id": 1,
        "title": "文章标题",
        "summary": "摘要",
        "cover": "https://xxx.jpg",
        "viewCount": 123,
        "likeCount": 45,
        "author": {
          "id": 1,
          "nickname": "张三",
          "avatar": null
        },
        "category": {
          "id": 1,
          "name": "技术"
        },
        "status": 1,
        "tags": ["Java", "SpringBoot"],
        "createTime": "2026-06-03 10:00:00"
      }
    ]
  }
}
```

#### 接口3：查询分类列表

- **请求**：`GET /category/list`
- **响应**：分类数组 `[{id, name, description}]`

#### 接口4：查询标签列表

- **请求**：`GET /tag/list`
- **响应**：标签数组 `[{id, name}]`

#### 接口5：我的文章

- **请求**：`GET /user/article/list?page=1&size=10`
- **请求参数**：
  - page：页码，默认 1，最小 1，page ≤ 0 时自动修正为 1
  - size：每页数量，默认 10，最小 1，最大 50，size ≤ 0 时自动修正为 10，size > 50 时自动修正为 50
- **说明**：查询当前登录用户发布的文章（包含草稿和已发布），从 UserContext 获取用户ID，按 `create_time DESC` 排序（最新创建在前）
- **响应**：分页结构，与 `/article/list` 相同，但不受 `status = 1` 限制，返回的 `status` 为实际状态值

#### 接口6：查询文章详情

- **请求**：`GET /article/{id}`

- **权限控制**：
  - 已发布文章（status = 1）：任何人可查看
  - 草稿（status = 0）：仅作者本人可查看，其他人返回 404 "文章不存在"
- **JWT 拦截器策略**：`GET /article/{id}` 为公开接口，不强制要求登录；但如有携带有效 Token，需解析并设置 UserContext，供 Service 层判断是否为作者

- **响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "title": "文章标题",
    "content": "# Markdown正文...",
    "summary": "摘要",
    "cover": "https://xxx.jpg",
    "viewCount": 123,
    "likeCount": 45,
    "status": 1,
    "author": {
      "id": 1,
      "nickname": "张三",
      "avatar": null
    },
    "category": {
      "id": 1,
      "name": "技术"
    },
    "tags": [{"id": 1, "name": "Java"}],
    "createTime": "2026-06-03 10:00:00",
    "updateTime": "2026-06-03 12:00:00"
  }
}
```

#### 接口7：修改文章

- **请求**：

```json
{
  "id": 1,
  "title": "修改后的标题",
  "content": "# 修改后的正文",
  "summary": "修改后的摘要",
  "cover": "https://xxx.jpg",
  "categoryId": 1,
  "tagIds": [1, 2],
  "status": 1
}
```

- **请求参数校验**：
  - id：必填，文章ID
  - title：可选，若传入则 1-100 字符
  - content：可选，若传入则不能为空，最大 50000 字符
  - summary：可选，传入 null 或空字符串时从 content 重新生成；传入具体值则使用该值（最大 500 字符）
  - cover：可选，URL 格式（需以 http:// 或 https:// 开头）。本阶段不实现文件上传，URL 需用户手动填写
  - categoryId：可选，若传入需校验分类是否存在
  - tagIds：可选，null 表示不修改标签，空数组表示清空标签，非空数组表示替换标签（最多 10 个，超出截断；自动去重并过滤 null 元素和无效标签ID）
  - status：可选，仅允许传入 0 或 1。传 0 则将文章设为草稿，传 1 则设为已发布，传 null 或不传则保持原状态不变

- **权限校验**：只能修改自己的文章，管理员可修改任意文章

- **响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

#### 接口8：删除文章

- **请求**：`DELETE /article/{id}`

- **权限校验**：只能删除自己的文章，管理员可删除任意文章

- **异常场景**：文章不存在时返回 404 "文章不存在"

- **响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

## 3. 数据库设计

> **外键约束说明**：article 表的 `user_id`、`category_id` 以及 article_tag 表的 `article_id`、`tag_id` 在逻辑上为外键关系，但数据库层面不创建物理外键约束（应用层通过业务逻辑保证数据一致性）。此做法便于开发和测试阶段灵活操作，避免外键约束带来的级联限制。

### 3.1 新增表

| 表名 | 说明 | 变更类型 |
|------|------|----------|
| article | 文章表 | 新增 |
| category | 分类表 | 新增 |
| tag | 标签表 | 新增 |
| article_tag | 文章-标签关联表 | 新增 |

### 3.2 字段定义

**article 表**：

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| title | VARCHAR(100) | NOT NULL | 标题 |
| content | LONGTEXT | NOT NULL | 正文（Markdown） |
| summary | VARCHAR(500) | NULL | 摘要 |
| cover | VARCHAR(500) | NULL | 封面图 URL |
| view_count | INT | NOT NULL, DEFAULT 0 | 浏览量 |
| like_count | INT | NOT NULL, DEFAULT 0 | 点赞数 |
| user_id | BIGINT | NOT NULL, FK | 作者ID |
| category_id | BIGINT | NOT NULL, FK | 分类ID |
| status | TINYINT | NOT NULL, DEFAULT 1 | 状态：1-已发布 0-草稿 |
| create_time | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 更新时间（代码手动维护，不使用 ON UPDATE，避免浏览量+1等操作误触更新） |

**category 表**：

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| name | VARCHAR(50) | NOT NULL | 分类名称 |
| description | VARCHAR(200) | NULL | 分类描述 |

**tag 表**：

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| name | VARCHAR(50) | NOT NULL, UNIQUE | 标签名称 |

**article_tag 表**：

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| article_id | BIGINT | NOT NULL, FK | 文章ID |
| tag_id | BIGINT | NOT NULL, FK | 标签ID |
| PRIMARY KEY | (article_id, tag_id) | | 联合主键 |

### 3.3 索引

| 索引名 | 表 | 字段 | 类型 | 说明 |
|--------|-----|------|------|------|
| idx_article_user_id | article | user_id | INDEX | 按作者查询 |
| idx_article_category_id | article | category_id | INDEX | 按分类查询 |
| idx_article_status | article | status | INDEX | 按状态筛选 |
| idx_article_create_time | article | create_time | INDEX | 按时间排序 |
| uk_tag_name | tag | name | UNIQUE | 标签名唯一 |
| idx_tag_id | article_tag | tag_id | INDEX | 按标签查询关联文章 |

### 3.4 初始化数据

分类表预置数据：

| id | name | description |
|----|------|-------------|
| 1 | 技术 | 编程、架构、工具 |
| 2 | 生活 | 日常、随笔、感悟 |
| 3 | 设计 | UI/UX、视觉、创意 |
| 4 | 读书笔记 | 阅读、学习、思考 |

标签表初始为空，本阶段由管理员通过 SQL 预置常用标签，不提供标签创建 API。发布/修改文章时传入的 `tagIds` 中不存在的标签ID会被自动过滤，仅关联已存在的标签。

## 4. 核心逻辑

### 4.1 发布文章流程

1. 校验用户已登录（JWT 拦截器已处理）
2. 参数校验（标题、内容非空，长度限制；校验 categoryId 对应分类是否存在）
3. 如果 summary 为 null，自动生成摘要：
   - 从 content 截取前 150 个字符（按字符数，非字节数）
   - 去除 Markdown 标记：使用正则过滤 `# * _ 
 [ ] ( )` 等符号，只保留纯文本
   - 若去除标记后不足 150 字符则取全部纯文本；若 content 不足 150 字符则取全文纯文本
   - 若去除标记后为空字符串，则摘要为空字符串（不返回 null）
4. 插入 article 表
5. 插入 article_tag 关联表（过滤不存在的标签ID，最多关联 10 个）
6. 返回文章ID

### 4.2 查询文章列表流程

1. 构建动态 SQL（根据 categoryId、keyword 条件筛选）
2. 分页查询
3. 关联查询作者信息（user 表）
4. 关联查询分类信息（category 表）
5. 关联查询标签信息（tag 表，通过 article_tag）
6. 返回分页结果

### 4.3 查询文章详情流程

1. 根据 ID 查询文章
2. 若文章不存在 → 返回 404
3. 若文章为草稿（status = 0）：
   - 从 UserContext 获取当前用户ID
   - 当前用户为空（未登录）或不是作者 → 返回 404（对外统一显示"文章不存在"，不暴露草稿存在性）
   - 当前用户是作者 → 继续返回详情（不增加浏览量；`viewCount` 和 `likeCount` 返回实际值）
4. 若文章已发布（status = 1）→ 浏览量 +1
5. 关联查询作者、分类、标签
6. 返回文章详情

### 4.4 修改文章流程

1. 校验用户已登录
2. 查询文章是否存在
3. 校验权限：当前用户 == 文章作者 或 role == ADMIN
4. 更新 article 表（动态 SQL，仅更新非 null 字段；同时手动设置 `update_time = NOW()`）
5. 标签关联处理：
   - `tagIds` 为 **null** → 不修改标签关联
   - `tagIds` 为 **空数组** → 删除所有标签关联
   - `tagIds` 为 **非空数组** → 删除旧关联，插入新关联（过滤无效ID，最多 10 个，超出截断）
6. 返回成功

### 4.5 删除文章流程

1. 校验用户已登录
2. 查询文章是否存在
3. 校验权限：当前用户 == 文章作者 或 role == ADMIN
4. 删除 comment 表中该文章的关联记录
5. 删除 article_tag 关联记录
6. 删除 article 记录
7. 返回成功

### 4.6 权限校验

```
修改/删除文章时
  ↓
从 UserContext 获取当前用户ID和角色
  ↓
查询文章的作者ID
  ↓
当前用户ID == 作者ID ?
  ├─ 是 → 允许操作
  ↓
当前角色 == ADMIN ?
  ├─ 是 → 允许操作
  ↓
返回 403 "无权操作"
```

### 4.7 异常处理

| 异常场景 | 处理方式 | 返回结果 |
|----------|----------|----------|
| 文章不存在 | Service 校验，抛 BusinessException | Result.error(404, "文章不存在") |
| 无权修改/删除 | Service 校验，抛 BusinessException(403) | Result.error(403, "无权操作") |
| 参数校验失败 | Service 校验，抛 BusinessException(400) | Result.error(400, "xxx不能为空") |
| 分页参数越界 | 自动修正为合法值（page < 1 设为 1，size > 50 设为 50） | 正常返回分页结果 |

## 5. 安全考虑

- [x] 发布/修改/删除接口需要登录（JWT 拦截器）
- [x] 修改/删除时校验所有权（只能操作自己的文章）
- [x] 管理员可操作任意文章
- [x] 输入校验：标题/内容长度限制；关键词最大 50 字符，防止超长搜索攻击
- [x] XSS 防护：前端 Markdown 渲染使用可信库，后端不做 HTML 转义（正文存储原始 Markdown，由前端负责渲染安全）
- [x] SQL 注入防护：MyBatis #{} 预编译参数
- [x] 列表接口分页限制，防止大数据量查询
- [x] 草稿存在性保护：草稿对外统一返回 404，不暴露存在性和作者信息

## 6. 前端交互

### 6.1 首页（文章列表）

- 路由路径：`/`
- 展示：文章卡片列表、分页、分类筛选、搜索框
- Element Plus 组件：`el-card`, `el-pagination`, `el-input`, `el-select`
- **API 调用策略**：页面加载时同时请求 `/article/list`（默认第1页）和 `/category/list`，分类数据用于筛选下拉框
- **错误处理**：列表加载失败时显示 `el-empty` 占位图，网络错误时 `ElMessage.error` 提示
- **搜索防抖**：关键词输入使用 300ms 防抖，避免频繁请求

### 6.2 文章详情页

- 路由路径：`/article/{id}`
- 展示：标题、作者、发布时间、正文（Markdown 渲染）、浏览量、点赞按钮
- Markdown 渲染：使用 `markdown-it` 或 `marked`，需配置 XSS 过滤（如 `markdown-it-sanitizer`）
- **图片上传**：本阶段不实现图片上传功能，用户在 Markdown 编辑器中插入图片时需手动填写外部图床 URL（`![alt](https://xxx.jpg)`）
- **错误处理**：文章不存在时（404）跳转首页或显示错误页；加载失败时重试一次
- **浏览量**：每次进入详情页后端自动 +1，前端无需额外调用

### 6.3 发布/修改文章页

- 路由路径：
  - 发布：`/article/create`
  - 修改：`/article/edit/:id`（复用发布页组件，加载已有数据）
- 表单字段：标题、分类选择、标签选择、Markdown 编辑器、封面图 URL
- Markdown 编辑器：使用 `@vavt/md-editor-v3`。本阶段不实现图片上传，用户需手动粘贴外部图床图片链接
- **权限控制**：进入页面前检查登录状态，未登录时拦截并弹出登录弹窗（不跳转路由）
- **修改回显**：进入 `/article/edit/:id` 时先调用 `GET /article/{id}` 加载详情，将 `tags`（`List<TagVO>`）提取 `id` 转为 `tagIds` 回填表单
- **发布流程**：表单校验通过后调用 `POST /article`，成功后在文章列表或个人中心显示新文章
- **错误处理**：发布/修改失败时保留表单数据，提示错误信息，不重置表单

### 6.4 我的文章（个人中心）

- 路由路径：`/profile`（个人中心子页面）
- 展示：当前用户发布的文章列表（包含草稿和已发布），显示状态标签（已发布/草稿）
- 操作：编辑、删除
- **API 调用**：`GET /user/article/list`，支持分页
- **删除确认**：点击删除时弹出 `el-message-box` 确认对话框，防止误操作
- **删除后刷新**：删除成功后从当前列表中移除该条目（或重新加载当前页）

## 7. Redis / 缓存设计

本阶段不涉及 Redis 功能（浏览量/点赞在第六阶段实现），但 article 表预留了 view_count 和 like_count 字段。

## 8. 任务拆分

| 序号 | 子任务 | 预计耗时 | 依赖 |
|------|--------|----------|------|
| 1 | 创建 article/category/tag 表（SQL） | 10 min | 无 |
| 2 | 初始化 category 数据（标签表留空） | 5 min | 1 |
| 3 | 创建 Entity/Mapper/XML | 20 min | 1 |
| 4 | 创建 CategoryService/TagService + Controller | 15 min | 3 |
| 5 | 创建 ArticleService（CRUD + 权限 + 分页） | 30 min | 3, 4 |
| 6 | 创建 ArticleController | 20 min | 5 |
| 7 | Postman 测试文章 CRUD + 分类/标签查询 | 15 min | 4, 6 |
| 8 | 首页文章列表（含分页/筛选） | 25 min | 7 |
| 9 | 文章详情页（Markdown渲染） | 15 min | 7 |
| 10 | 发布/修改文章页（Markdown编辑器） | 30 min | 7 |
| 11 | 前后端联调 | 20 min | 7, 10 |

---

**确认状态**：
- [x] 待评审
- [ ] 已确认
- [ ] 需要修改
