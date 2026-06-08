# 设计文档：评论模块

## 1. 需求概述

- **功能名称**：评论系统
- **目标**：实现文章评论的发布、列表查询、删除，支持一级评论和二级回复
- **涉及角色**：访客（查看列表）、登录用户（发表、删除自己的评论）、管理员（删除任意评论）
- **设计决策**：评论发布后**不支持编辑**

## 2. 接口设计

### 2.1 接口列表

> 标记为"登录用户"的接口，请求头需携带 `Authorization: Bearer {accessToken}`

| 接口 | 方法 | 路径 | 权限 |
|------|------|------|------|
| 发表评论 | POST | /comment | 登录用户 |
| 评论列表 | GET | /comment/list | 公开 |
| 删除评论 | DELETE | /comment/{id} | 登录用户（自己或管理员） |

### 2.2 DTO/VO

**CommentDTO**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| articleId | Long | 是 | 文章ID |
| content | String | 是 | 评论内容，trim后长度 1-500 |
| parentId | Long | 否 | 父评论ID（回复时必填，指向一级评论） |
| replyToId | Long | 否 | 被回复用户ID（回复时必填，后端可自动推断） |

**CommentVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 评论ID |
| content | String | 评论内容 |
| articleId | Long | 文章ID |
| parentId | Long | 父评论ID（一级评论为 null） |
| user | UserVO | 评论者信息（id, nickname, avatar） |
| replyToUser | UserVO | 被回复者信息（仅 id、nickname；为 null 时不展示） |
| createTime | LocalDateTime | 创建时间 |
| children | List<CommentVO> | 二级回复列表（Service层初始化为空列表） |

### 2.3 请求与响应

#### 发表评论

**请求**：

```json
// 一级评论
{ "articleId": 1, "content": "写得很好！", "parentId": null, "replyToId": null }

// 回复评论（@前缀由用户自由输入）
{ "articleId": 1, "content": "有个问题想请教...", "parentId": 5, "replyToId": 2 }
```

**响应**：`Result<CommentVO>`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 10,
    "content": "写得很好！",
    "articleId": 1,
    "parentId": null,
    "user": { "id": 2, "nickname": "小明", "avatar": null },
    "replyToUser": null,
    "createTime": "2025-06-08 15:30:00",
    "children": []
  }
}
```

**状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 成功 | — |
| 400 | 参数错误 | content为空/超过500字符、parentId为null但replyToId不为null等 |
| 404 | 资源不存在 | 文章不存在 |
| 401 | 未登录 | Token无效或缺失 |

#### 评论列表

**请求**：`GET /comment/list?articleId=1&page=1&size=10`

> page 默认 1，size 默认 10、最大 50

**响应**：`Result<PageResult<CommentVO>>`

> `total` 是一级评论总数。页面标题显示"评论"，不显示精确总数。

**状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 成功 | — |
| 400 | 参数错误 | articleId为空 |

#### 删除评论

**请求**：`DELETE /comment/{id}`

**响应**：`Result<Void>`

**状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 成功 | — |
| 401 | 未登录 | — |
| 403 | 无权限 | 非作者且非管理员 |
| 404 | 不存在 | 评论ID不存在或已删除 |

## 3. 数据库设计

### 3.1 新增表

```sql
CREATE TABLE `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content` VARCHAR(500) NOT NULL COMMENT '评论内容',
    `article_id` BIGINT NOT NULL COMMENT '文章ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（一级评论为NULL）',
    `reply_to_id` BIGINT DEFAULT NULL COMMENT '被回复用户ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-已删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_comment_article_id` (`article_id`),
    KEY `idx_comment_parent_id` (`parent_id`),
    KEY `idx_comment_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';
```

### 3.2 索引

| 索引名 | 字段 | 说明 |
|--------|------|------|
| PRIMARY | id | 主键 |
| idx_comment_article_id | article_id | 按文章查询 |
| idx_comment_parent_id | parent_id | 查询某条评论的回复 |
| idx_comment_user_id | user_id | 查询用户发表的评论 |

## 4. 后端实现

### 4.1 实体类

```java
package com.novablog.entity;

@Data
public class Comment {
    private Long id;
    private String content;
    private Long articleId;
    private Long userId;
    private Long parentId;
    private Long replyToId;
    private Integer status;
    private LocalDateTime createTime;
}
```

### 4.2 Service 接口

```java
package com.novablog.service;

public interface CommentService {
    CommentVO publish(CommentDTO dto);
    PageResult<CommentVO> findList(Long articleId, Integer page, Integer size);
    void delete(Long id);
}
```

### 4.3 Mapper 接口

```java
package com.novablog.mapper;

@Mapper
public interface CommentMapper {
    int insert(Comment comment);
    Comment selectById(Long id);
    List<CommentVO> selectTopLevelByArticleId(@Param("articleId") Long articleId,
                                               @Param("offset") Integer offset,
                                               @Param("limit") Integer limit);
    Long countTopLevelByArticleId(Long articleId);
    List<CommentVO> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int batchUpdateStatusByParentId(@Param("parentId") Long parentId,
                                    @Param("status") Integer status);
}
```

### 4.4 Mapper XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.novablog.mapper.CommentMapper">

    <resultMap id="commentVOResultMap" type="com.novablog.vo.CommentVO">
        <id property="id" column="id"/>
        <result property="content" column="content"/>
        <result property="articleId" column="article_id"/>
        <result property="parentId" column="parent_id"/>
        <result property="createTime" column="create_time"/>
        <association property="user" javaType="com.novablog.vo.UserVO" notNullColumn="uid">
            <id property="id" column="uid"/>
            <result property="nickname" column="unickname"/>
            <result property="avatar" column="uavatar"/>
        </association>
        <association property="replyToUser" javaType="com.novablog.vo.UserVO" notNullColumn="ruid">
            <id property="id" column="ruid"/>
            <result property="nickname" column="runickname"/>
        </association>
    </resultMap>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO comment (content, article_id, user_id, parent_id, reply_to_id)
        VALUES (#{content}, #{articleId}, #{userId}, #{parentId}, #{replyToId})
    </insert>

    <select id="selectById" resultType="com.novablog.entity.Comment">
        SELECT * FROM comment WHERE id = #{id} LIMIT 1
    </select>

    <select id="selectTopLevelByArticleId" resultMap="commentVOResultMap">
        SELECT
            c.id, c.content, c.article_id, c.user_id, c.parent_id,
            c.reply_to_id, c.status, c.create_time,
            u.id as uid, u.nickname as unickname, u.avatar as uavatar,
            ru.id as ruid, ru.nickname as runickname
        FROM comment c
        LEFT JOIN user u ON c.user_id = u.id
        LEFT JOIN user ru ON c.reply_to_id = ru.id
        WHERE c.article_id = #{articleId}
          AND c.status = 1
          AND c.parent_id IS NULL
        ORDER BY c.create_time DESC
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countTopLevelByArticleId" resultType="java.lang.Long">
        SELECT COUNT(*) FROM comment
        WHERE article_id = #{articleId} AND status = 1 AND parent_id IS NULL
    </select>

    <select id="selectRepliesByParentIds" resultMap="commentVOResultMap">
        SELECT
            c.id, c.content, c.article_id, c.user_id, c.parent_id,
            c.reply_to_id, c.status, c.create_time,
            u.id as uid, u.nickname as unickname, u.avatar as uavatar,
            ru.id as ruid, ru.nickname as runickname
        FROM comment c
        LEFT JOIN user u ON c.user_id = u.id
        LEFT JOIN user ru ON c.reply_to_id = ru.id
        WHERE c.parent_id IN
        <foreach collection="parentIds" item="pid" open="(" separator="," close=")">
            #{pid}
        </foreach>
          AND c.status = 1
        ORDER BY c.create_time ASC
    </select>

    <update id="updateStatus">
        UPDATE comment SET status = #{status} WHERE id = #{id}
    </update>

    <update id="batchUpdateStatusByParentId">
        UPDATE comment SET status = #{status}
        WHERE parent_id = #{parentId} AND status = 1
    </update>

</mapper>
```

> **注意**：`user` 和 `replyToUser` 均使用 `notNullColumn`，当 JOIN 不到对应用户时（如用户被物理删除），association 为 null。前端需做防御式判断：`comment.user?.nickname || '匿名'`。

## 5. 核心逻辑

### 5.1 业务流程

**发表评论**

1. **参数校验**
   - content 为 null → 报错；不为 null → trim，trim后为空 → 报错
   - content 长度 1-500（trim后的值存入数据库）
   - parentId 为 null 时 replyToId 必须为 null
2. **校验文章**存在且 status = 1（已发布），否则抛异常
3. **若 parentId 不为空**：
   - 查询父评论，校验存在、status = 1、parent_id 为 NULL
   - 校验父评论的 article_id 与当前 articleId 一致
   - 若 replyToId 为空，默认取父评论的 user_id
4. **若 replyToId 不为空**，校验被回复用户是否存在
5. 从 UserContext 获取当前用户ID，插入评论记录
6. 查询当前用户信息组装 UserVO；若用户被物理删除，以 username 作为 nickname 后备
7. 若 replyToId 不为空，查询被回复用户信息组装 replyToUser
8. 返回 CommentVO（children 初始化为 `new ArrayList<>()`）

**评论列表**

1. 校验 articleId 非空
2. 校验文章存在且 status = 1；文章不存在或草稿均返回**空列表**（不暴露草稿信息）
3. 修正分页参数：page < 1 → 1，size < 1 → 10，size > 50 → 50
4. 分页查询一级评论（parent_id IS NULL 且 status = 1），按 create_time DESC
5. 统计一级评论总数
6. 若一级评论不为空，批量查询二级回复（parent_id IN (...) 且 status = 1），按 create_time ASC；若为空则跳过
7. Map 按 parentId 分组回复，遍历一级评论组装 children（每个 children 初始化为空列表）
8. 返回 PageResult

**删除评论**

1. 查询评论，校验存在且 status = 1（status = 0 视为不存在，返回 404）
2. 校验当前用户是评论作者或管理员
3. 逻辑删除：更新该评论 status = 0
4. 若删除一级评论（parent_id 为 NULL），级联将其下所有 status = 1 的二级回复 status 设为 0
5. 整个操作在 **@Transactional** 事务中执行

### 5.2 异常处理

| 异常场景 | 返回结果 |
|----------|----------|
| content 为 null / trim后为空 / 长度>500 | 400 "评论内容不能为空" / "评论内容不能超过500字" |
| articleId 为 null | 400 "文章ID不能为空" |
| articleId 不存在 | 404 "文章不存在" |
| 文章 status = 0（草稿） | 400 "草稿文章禁止评论" |
| parentId 为 null 但 replyToId 不为 null | 400 "参数错误" |
| 父评论不存在 / 已删除 / 不是一级评论 / 属于其他文章 | 400 "回复的评论不存在" / "已删除" / "只能回复一级评论" / "与文章不匹配" |
| replyToId 对应的用户不存在 | 400 "被回复用户不存在" |
| 删除时评论不存在或已删除 | 404 "评论不存在" |
| 非作者/管理员删除评论 | 403 "无权删除该评论" |

## 6. 安全考虑

- **权限校验**：发表/删除需登录（拦截器拦截），列表查询公开（WebConfig 已放行 `/comment/list`）
- **输入验证**：content trim后长度 1-500；articleId/parentId/replyToId 为正整数；size 限制 1-50
- **参数一致性**：parentId 为 null 时 replyToId 必须为 null
- **SQL 注入防护**：MyBatis `#{}` 预编译；foreach 使用 `#{}`
- **XSS 防护**：前端 Vue 文本插值 `{{ content }}` 自动转义；**禁止**对评论内容使用 `v-html`
- **越权防护**：删除时校验 user_id 匹配或角色为 ADMIN
- **回复合法性**：parentId 对应的评论必须是该文章下的一级评论（parent_id IS NULL 且 status = 1）
- **逻辑删除**：使用 status 字段，保留数据可追溯

## 7. 前端交互

### 7.1 页面结构

集成在 **ArticleDetailView.vue** 底部：

```
评论区域
├── 标题栏："评论"
├── 发表评论框
│   ├── 未登录："登录后参与讨论" + 登录/注册按钮
│   └── 已登录：
│       ├── 回复状态提示（如"回复 @小明"，带取消按钮）
│       ├── el-input textarea（placeholder 动态变化）
│       ├── 字数统计 0/500
│       └── 发表按钮（content.trim() 为空时禁用）
├── 评论列表
│   └── 一级评论项（:key="comment.id"）
│       ├── el-avatar + 昵称（avatar 为 null 显示默认图标）
│       ├── 评论内容（{{ content }}，white-space: pre-wrap）
│       ├── 时间
│       ├── 回复按钮（登录用户可见）
│       ├── 删除按钮（作者/管理员可见）
│       └── 二级回复列表（v-if="comment.children.length > 0"）
│           └── 回复项（:key="reply.id"）
│               ├── el-avatar + 昵称
│               ├── "@被回复者:" + 内容（replyToUser 为 null 时不显示@）
│               ├── 时间
│               ├── 回复按钮（登录用户且 reply.user 不为 null）
│               └── 删除按钮（作者/管理员）
├── 分页器（layout="total, sizes, prev, pager, next"，page-sizes="[10, 20, 50]"）
└── 空状态（el-empty："暂无评论，来抢沙发吧~"）
```

### 7.2 状态管理

| 状态 | 类型 | 说明 |
|------|------|------|
| comments | Ref<CommentVO[]> | 评论列表 |
| commentTotal | Ref<number> | 一级评论总数 |
| commentPage | Ref<number> | 当前页码，默认 1 |
| commentPageSize | Ref<number> | 每页数量，默认 10 |
| commentContent | Ref<string> | 输入内容 |
| replyingTo | Ref<CommentVO \| null> | 当前回复对象 |
| commentLoading | Ref<boolean> | 列表加载中 |
| publishing | Ref<boolean> | 发表中（按钮 loading） |

### 7.3 交互流程

**公共错误处理**：所有 API 调用失败时，`ElMessage.error` 显示错误信息。

1. **页面加载**：`commentLoading = true` → 调用 `GET /comment/list?articleId={id}` → `commentLoading = false`

2. **发表评论（一级）**：`publishing = true` → `POST /comment`（parentId = null, replyToId = null）→ `publishing = false` → 重置 page = 1，重新加载列表，清空 replyingTo 和 commentContent

3. **回复评论**：点击"回复" → 设置 replyingTo，清空 commentContent → 输入内容 → `publishing = true` → `POST /comment` → `publishing = false` → push 到对应一级评论的 children 末尾，清空 replyingTo 和 commentContent

   请求参数：
   - 回复一级评论：`{ articleId, content, parentId: 评论.id, replyToId: 评论.user.id }`
   - 回复二级回复：`{ articleId, content, parentId: 回复.parentId, replyToId: 回复.user?.id }`

4. **删除评论**：`ElMessageBox.confirm` → `DELETE /comment/{id}`
   - 一级评论：重新加载当前页；若当前页为空且 page > 1，page-- 后重新加载
   - 二级回复：找到 parentId 对应的一级评论，从其 children 中过滤掉该条

5. **分页切换**：更新 commentPage / commentPageSize → `commentLoading = true` → 重新加载 → `commentLoading = false`

### 7.4 权限判断

```js
const canDelete = (comment) => {
  if (!userStore.userInfo || !comment.user) return false
  return userStore.userInfo.id === comment.user.id
      || userStore.userInfo.role === 'ADMIN'
}

const canReply = () => !!userStore.userInfo
```

### 7.5 边界处理

| 场景 | 处理 |
|------|------|
| 未登录 | 隐藏发表评论框和回复按钮 |
| 输入为空 | 发表按钮禁用 |
| 超 500 字 | maxlength + show-word-limit 拦截 |
| 切换回复对象 | 自动清空 commentContent |
| 发表一级评论时不在第一页 | 自动回到第一页并刷新 |
| 删除一级评论后该页为空且 page > 1 | page-- 后重新加载 |
| 评论含换行 | `white-space: pre-wrap` 保留格式 |

## 8. 任务拆分

| 序号 | 子任务 | 依赖 |
|------|--------|------|
| 1 | 数据库：创建 comment 表 | — |
| 2 | 后端：Comment 实体类 | 1 |
| 3 | 后端：CommentDTO + CommentVO | — |
| 4 | 后端：CommentMapper 接口 + XML | 2, 3 |
| 5 | 后端：CommentService 接口 + 实现 | 3, 4 |
| 6 | 后端：CommentController | 5 |
| 7 | 后端：Postman 接口测试 | 6 |
| 8 | 前端：API 模块 comment.js | — |
| 9 | 前端：文章详情页集成评论区 | 8 |
| 10 | 前后端联调 | 7, 9 |
| 11 | Git 提交 | 10 |

**前端 API 命名**：

```js
export function publishComment(data)   // POST /comment
export function getCommentList(params) // GET /comment/list
export function deleteComment(id)      // DELETE /comment/{id}
```

## 9. 补充说明

### 9.1 parentIds 为空防护

Service 层调用 `selectRepliesByParentIds` 前校验 parentIds 不为空。若一级评论列表为空，跳过二级回复查询，直接返回空列表。

### 9.2 事务边界

| 方法 | 是否需要 @Transactional | 原因 |
|------|------------------------|------|
| publish | 否 | 单表插入 |
| delete | **是** | 自身更新 + 级联更新 |
| findList | 否 | 读操作 |

---

**确认状态**：
- [x] 待评审
- [ ] 已确认
- [ ] 需要修改
