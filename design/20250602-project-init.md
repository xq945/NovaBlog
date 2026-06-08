# 设计文档：项目初始化

## 1. 需求概述

- **功能名称**：项目初始化
- **目标**：创建前后端项目骨架，完成基础配置，确保两端服务都能正常启动
- **涉及角色**：开发者

## 2. 接口设计

本阶段不涉及业务接口，仅需验证后端启动成功的健康检查。

## 3. 数据库设计

本阶段不创建业务表，仅需：
- 确认 MySQL 服务可连接
- 准备 `sql/init.sql` 脚本文件（留空或仅含注释，后续阶段填充）

## 4. 核心逻辑

### 4.1 后端项目创建流程

1. 使用 Spring Initializr 或手动创建 Maven 项目
2. 配置 `pom.xml` 引入依赖
3. 创建目录结构（config, controller, service, mapper, entity, dto, vo, common, interceptor, task, util）
4. 配置 `application.yml`（公共配置，不含敏感信息）
5. 配置 `application-dev.yml`（本地开发配置，含真实数据库/Redis密码，**加入 .gitignore**）
6. 创建 `application-dev.yml.example`（模板，密码用占位符，提交仓库）
7. 编写 `NovaBlogApplication.java`
8. 创建统一返回结果类 `Result<T>`
9. 启动测试

### 4.2 前端项目创建流程

1. 使用 `npm create vite@latest` 创建 Vue3 项目
2. 安装依赖：Element Plus, Axios, Pinia, Vue Router
3. 配置 Vue Router（基础路由结构）
4. 配置 Axios（基础实例 + 拦截器占位）
5. 配置 Pinia（store 目录结构）
6. 配置 Vite 代理（`/api` → `http://localhost:8080`）
7. 启动测试

## 5. 安全考虑

- [ ] `application-dev.yml` 加入 `.gitignore`，不提交真实密码
- [ ] `application-dev.yml.example` 作为模板提交，密码用占位符

## 6. 前端交互

本阶段无具体页面，仅需：
- 一个基础 `App.vue`
- 路由占位（首页路由 `/`）

## 7. Redis / 缓存设计

不涉及。

## 8. 任务拆分

| 序号 | 子任务 | 预计耗时 | 依赖 |
|------|--------|----------|------|
| 1 | 创建 SpringBoot 项目骨架 + pom.xml | 15 min | 无 |
| 2 | 配置 application.yml / application-dev.yml.example | 10 min | 1 |
| 3 | 创建统一返回 Result 类 | 10 min | 1 |
| 4 | 启动后端验证 | 5 min | 2, 3 |
| 5 | 创建 Vue3 项目（Vite） | 10 min | 无 |
| 6 | 安装并配置 Element Plus / Axios / Pinia / Vue Router | 15 min | 5 |
| 7 | 配置 Vite 代理 | 5 min | 6 |
| 8 | 启动前端验证 | 5 min | 7 |

---

**确认状态**：
- [x] 待评审
- [ ] 已确认
- [ ] 需要修改
