# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供本项目代码开发指导。

## 项目概述

NovaBlog 是一个全栈个人博客系统。开发遵循**后端先行、立即联调**的工作流：每个后端功能完成后，立即开发对应前端页面并进行接口联调。

## 技术栈

**后端：**
- Java 17, SpringBoot 3, MyBatis, MySQL 8, Redis, JWT, Maven

**前端：**
- Vue 3, Vite, Element Plus, Axios, Pinia

## 开发工作流

每个功能严格按照以下顺序执行：
1. 需求分析
2. **规划与设计文档** — 写出具体实现方案，**经用户确认后再进行下一步**
3. 后端接口实现
4. Postman 测试
5. 前端页面开发
6. 前后端联调
7. Git 提交

### 设计文档规范

每开发一个功能前，必须先编写设计文档，明确以下内容：
- **接口设计**：URL、HTTP 方法、请求参数、响应结构、状态码
- **数据库变更**：新增/修改的表、字段、索引
- **核心逻辑**：业务流程、关键算法、异常处理
- **安全考虑**：权限校验、输入验证、防攻击措施
- **前端交互**：页面结构、组件划分、状态管理

设计文档存放于 `design/` 目录，命名格式：`{日期}-{功能名称}.md`，例如 `20250602-article-crud.md`。

### 设计文档迭代优化

设计文档不是一次性产物，需要经过多轮检查和完善：
- **第一轮**：完成基础结构（接口、数据库、核心逻辑）
- **第二轮**：检查完整性（缺少的接口、边界条件、异常场景）
- **第三轮**：检查一致性（前后端数据结构对齐、命名统一）
- **第四轮**：检查可实施性（任务拆分是否合理、依赖是否清晰）

每轮检查发现问题后更新文档，直到没有明显遗漏为止。

### 启动前检查

开始编码前，先确认项目当前状态，避免重复工作：
- 检查已有代码：浏览 `novablog-server/src/` 和 `novablog-web/src/` 确认已实现的功能
- 检查数据库：`SHOW TABLES;` 确认已有表结构
- 检查 Git 状态：`git status` 确认当前分支和未提交更改


## 项目结构（规划）

```
NovaBlog/
├── novablog-server/        # SpringBoot 后端
│   ├── pom.xml
│   ├── sql/                # 数据库初始化脚本
│   └── src/main/java/com/novablog/
│       ├── NovaBlogApplication.java
│       ├── config/         # Security, Redis, Web, Cors 配置
│       ├── controller/     # REST 控制器（按路线图不使用 /api 前缀）
│       ├── service/        # 业务逻辑 + impl/
│       ├── mapper/         # MyBatis Mapper
│       ├── entity/         # 数据库实体
│       ├── dto/            # 请求/响应 DTO
│       ├── vo/             # 视图对象
│       ├── common/         # Result, PageResult, 异常, 注解
│       ├── interceptor/    # JWT 认证拦截器
│       ├── task/           # 定时任务（Redis 同步）
│       └── util/           # JwtUtil, RedisUtil, PasswordUtil
│   └── src/main/resources/
│       ├── application.yml     # 公共配置（不含密码，提交仓库）
│       ├── application-dev.yml # 本地开发配置（含密码，gitignore）
│       └── mapper/*.xml        # MyBatis XML 映射
├── novablog-web/           # Vue3 前端
│   ├── src/
│   │   ├── api/            # Axios API 模块（按实体划分）
│   │   ├── views/          # 页面组件
│   │   ├── router/         # Vue Router 配置
│   │   ├── stores/         # Pinia 状态管理
│   │   └── utils/          # 请求拦截器、工具函数
│   └── dist/               # 构建输出
└── CLAUDE.md
```

## 数据库设计

表结构：
- `user` — id, username, password, nickname, avatar, email, role(ADMIN/USER), status
- `article` — id, title, content, summary, cover, view_count, like_count, user_id, category_id, status
- `category` — id, name, description
- `tag` — id, name
- `article_tag` — article_id, tag_id（多对多关联）
- `comment` — id, content, article_id, user_id, parent_id, reply_to_id, status

## API 路径规范（来自路线图）

后端 Controller **不使用 `/api` 前缀**，采用 RESTful 风格：

| 资源 | 创建 | 查询 | 更新 | 删除 |
|------|------|------|------|------|
| 用户 | `POST /user/register` | `GET /user/profile` | `PUT /user/profile` | — |
| 文章 | `POST /article` | `GET /article/list`, `GET /article/{id}` | `PUT /article` | `DELETE /article/{id}` |
| 评论 | `POST /comment` | `GET /comment/list` | — | — |
| 文件 | `POST /upload` | — | — | — |

**前后端路径对齐说明**：后端接口无 `/api` 前缀，但前端 Vite 代理配置以 `/api` 作为匹配路径。因此前端 Axios 请求需带 `/api` 前缀，经代理转发到后端时去掉该前缀。例如前端请求 `/api/article/list`，代理后到达后端的路径为 `/article/list`。

## Redis Key 规范

- `article:view:{id}` — 浏览量计数（String）
- `article:like:{id}` — 点赞用户集合（Set）
- `blog:hot` — 热门文章排行（ZSet）

### Redis + MySQL 双写策略

Redis 用于缓存实时计数，MySQL 用于持久化存储，避免 Redis 故障丢数据：
- **写**：用户操作时先写 Redis（保证响应速度）
- **读**：优先从 Redis 读取，Redis 异常时降级读 MySQL
- **同步**：定时任务（建议每小时）将 Redis 中的 `article:view:{id}` 同步回 MySQL `article.view_count` 字段
- **初始化**：应用启动时，若 Redis 为空，从 MySQL 加载初始值到 Redis

## 常用命令

```bash
# 后端
cd C:/files/github/NovaBlog/novablog-server
mvn clean compile
mvn spring-boot:run

# 前端
cd C:/files/github/NovaBlog/novablog-web
npm install
npm run dev        # 开发服务器，端口 3000
npm run build      # 生产构建

# 数据库
cd C:/files/github/NovaBlog/novablog-server
mysql -u root -p123456 < sql/init.sql
```

## 配置说明

### 配置文件分层规则

| 文件 | 用途 | 提交仓库 | 值类型 |
|------|------|----------|--------|
| `application.yml` | 全局默认配置 | 是 | 纯环境变量占位符 `${VAR}`，无默认值 |
| `application-dev.yml` | 本地开发配置 | 否（gitignore） | 真实固定值 |
| `application-dev.yml.example` | 开发配置模板 | 是 | 占位符提示 |

**规则**：
- `application.yml` 中所有敏感/环境相关配置使用 `${VAR}` 纯占位符，不写入任何真实值或默认值
- `application-dev.yml` 中写入本地开发环境的真实固定值，不通过环境变量注入
- 生产环境通过环境变量注入真实值，不依赖 `application-dev.yml`
- 如果没有 `application-dev.yml` 且环境变量未设置，Spring Boot 启动报错，强制要求配置

### 默认密码

- MySQL 密码：`123456`
- Redis 密码：`123456`

## 代码注释规范

### 基本原则
- **使用中文注释**，保持与项目文档语言一致
- 注释说明 **WHY（为什么）** 而非 **WHAT（是什么）**，代码本身已经说明了"做什么"
- 禁止无意义注释，如 `// 初始化`、`// 循环遍历` 等重复代码语义的注释

### 配置文件注释
- `application.yml` 等配置文件**必须**为每个配置项添加注释，说明用途和取值含义
- 按功能模块分组，每组前加编号和模块名（如 `# 3. 数据库配置`）
- 数值型配置注明单位（如毫秒、分钟、MB）

### Java 代码注释
- **类/接口**：类上方用多行注释说明职责和作者
- **公共方法**：说明功能、参数、返回值、异常场景
- **复杂业务逻辑**：在关键判断、算法、边界处理处添加行内注释
- **魔法数字**：必须注释说明数值含义，或提取为带注释的常量

### 前端代码注释
- Vue 组件：在 `<script setup>` 顶部简要说明组件职责
- 复杂计算属性/方法：说明计算逻辑和边界情况
- API 调用：说明接口用途和错误处理策略

## 测试规范

### 后端接口测试

每个后端接口完成后，必须使用 Postman 或等价工具测试，覆盖以下场景：
- **正常流程**：参数合法，返回预期成功结果
- **边界值**：空值、最大/最小长度、特殊字符
- **异常流程**：重复操作、权限不足、资源不存在
- **安全测试**：SQL 注入尝试、XSS Payload、越权访问

### 前后端联调测试

联调时按以下顺序验证：
1. 前端页面渲染正常，无控制台报错
2. 表单提交触发正确 API 调用（Network 面板确认）
3. 后端返回数据结构符合预期
4. 前端正确处理成功/失败响应，UI 反馈正确
5. 数据库数据变更与操作一致

### 问题排查信息

遇到 Bug 时，提供以下信息以便快速定位：
- **后端**：控制台异常堆栈（完整的报错信息，不只是最后一行）
- **前端**：浏览器 Network 面板中对应请求的 Request / Response
- **数据库**：相关表的数据快照（脱敏后）
- **复现步骤**：从打开页面到触发错误的精确操作序列

## 开发服务器代理

Vite 开发服务器代理 `/api` 到 `http://localhost:8080`，代理时去掉 `/api` 前缀：
```js
// vite.config.js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, '')
    }
  }
}
```

前端 Axios 基地址配置为 `/api`，请求 `/api/article/list` 经代理后转发到后端 `GET /article/list`。

## UI 设计规范

基于 2026 年博客设计趋势：
- **Bento 网格布局**：使用 `el-row` + `el-col` 实现模块化卡片网格，featured 大卡片 + 侧边小卡片组合
- **暗黑模式 2.0**：Element Plus 内置暗黑主题，色板使用深灰 `#0f172a` 和 `#1e293b`，避免纯黑
- **毛玻璃效果**：头部导航使用 `backdrop-filter: blur(20px)` + 半透明背景 + 细边框
- **首屏大标题**：使用 `clamp()` 实现响应式字体：`font-size: clamp(2.5rem, 8vw, 6rem)`
- **移动端优先纵向卡片**：单列表布局，触控目标最小 48×48px
- **微交互**：列表入场动画用 `<TransitionGroup>`，骨架屏用 `el-skeleton`
- **多巴胺配色**：强调色（电光蓝、霓虹粉）用于分类标签和 CTA 按钮

Element Plus 主题变量覆盖：
```css
--el-color-primary: #409eff;
--bg-primary: #0f172a;
--bg-secondary: #1e293b;
```

## 可用技能（Skills）

开发过程中可调用以下技能辅助，按领域分类：

### 后端技能

| 技能 | 触发时机 | 说明 |
|------|----------|------|
| `springboot-patterns` | 编写 Java 后端代码 | Spring Boot 最佳实践、分层架构、依赖注入、异常处理 |
| `api-design` | 设计 REST 接口 | RESTful 规范、状态码、请求/响应设计、版本控制 |
| `database-design` | 设计表结构 / SQL | 表结构设计、规范化、索引策略、查询优化 |
| `test-driven-development` | 编写测试 / 新功能 | 先写测试再实现，红-绿-重构循环 |

### 前端技能

| 技能 | 触发时机 | 说明 |
|------|----------|------|
| `vue3-expert` | 编写 Vue 组件 | Composition API、响应式系统、性能优化、组件设计 |
| `element-plus-expert` | 使用 Element Plus 组件 | 组件选择、表单设计、表格配置、主题定制 |
| `vue-router-architecture` | 配置路由 | 路由配置、导航守卫、懒加载、权限控制 |
| `pinia-patterns` | 全局状态管理 | Store 设计、模块化、状态操作、组件集成 |

### 通用技能

| 技能 | 触发时机 | 说明 |
|------|----------|------|
| `code-review` | 审查代码 / PR | 正确性、安全性、性能、可维护性检查 |
| `git-workflow` | Git 操作 | 提交规范、分支策略、合并变基、协作流程 |
| `documentation-writer` | 编写文档 | README、API 文档、架构决策、代码注释 |
| `systematic-debugging` | 排查 Bug | 系统化调试流程：复现→隔离→假设→测试→修复→验证 |

### 系统技能

| 技能 | 触发时机 | 说明 |
|------|----------|------|
| `verify` | 完成代码修改后 | 验证变更是否按预期工作 |
| `simplify` | 代码审查阶段 | 检查已修改代码的可重用性、质量和效率 |
| `run` | 启动应用查看效果 | 启动项目并在浏览器中验证功能 |
| `security-review` | 涉及安全代码 | 对待提交变更进行安全审查 |

**建议开发流程**：
1. 后端开发时优先调用 `springboot-patterns` + `api-design` + `database-design`
2. 前端开发时优先调用 `vue3-expert` + `element-plus-expert`
3. 每完成一个功能调用 `verify` 验证
4. 提交前调用 `code-review` 自检
