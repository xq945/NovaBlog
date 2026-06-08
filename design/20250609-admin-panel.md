# 设计文档：后台管理模块

## 1. 需求概述

- **功能名称**：后台管理
- **目标**：为管理员提供系统内容管理入口，包括用户、文章、评论、分类、标签的管理
- **涉及角色**：ADMIN（管理员）
- **设计决策**：
  - 权限控制采用 Controller 方法内手动检查，不引入额外注解/拦截器（接口数量可控，保持简单）
  - 分类删除采用"有文章引用则禁止删除"策略（防止误删导致文章分类丢失）
  - 标签删除采用"级联删除关联"策略（标签是弱关联，不影响文章主体）
  - JwtInterceptor 增加用户状态实时校验（被禁用用户的 token 立即失效）

## 2. 接口设计

### 2.1 接口列表

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 用户列表 | GET | /user/admin/list | ADMIN | 分页查询所有用户 |
| 修改用户状态 | PUT | /user/admin/status | ADMIN | 禁用/启用用户 |
| 文章列表（管理） | GET | /article/admin/list | ADMIN | 分页查询所有文章（含草稿） |
| 评论列表（管理） | GET | /comment/admin/list | ADMIN | 分页查询所有评论 |
| 创建分类 | POST | /category | ADMIN | 新增分类 |
| 修改分类 | PUT | /category | ADMIN | 修改分类 |
| 删除分类 | DELETE | /category/{id} | ADMIN | 删除分类 |
| 创建标签 | POST | /tag | ADMIN | 新增标签 |
| 修改标签 | PUT | /tag | ADMIN | 修改标签 |
| 删除标签 | DELETE | /tag/{id} | ADMIN | 删除标签 |

> 文章修改/删除、评论删除复用已有接口（已支持 ADMIN 权限）。

### 2.2 DTO

**UserStatusDTO**

| 字段 | 类型 | 说明 | 校验规则 |
|------|------|------|----------|
| userId | Long | 用户ID | 必填 |
| status | Integer | 状态：1-启用，0-禁用 | 必填，只能为 0 或 1 |

**CategoryDTO**

| 字段 | 类型 | 说明 | 校验规则 |
|------|------|------|----------|
| id | Long | 分类ID（修改时必填） | — |
| name | String | 分类名称 | 必填，1-20位 |
| description | String | 分类描述 | 可选，最多200位 |

**TagDTO**

| 字段 | 类型 | 说明 | 校验规则 |
|------|------|------|----------|
| id | Long | 标签ID（修改时必填） | — |
| name | String | 标签名称 | 必填，1-20位 |

**AdminCommentVO**（`com.novablog.vo.AdminCommentVO`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 评论ID |
| content | String | 评论内容 |
| articleId | Long | 所属文章ID |
| articleTitle | String | 所属文章标题 |
| user | UserVO | 评论者信息 |
| parentId | Long | 父评论ID（一级为 null） |
| replyToUser | UserVO | 被回复者信息 |
| createTime | LocalDateTime | 创建时间 |

### 2.3 请求与响应

#### 用户列表

**请求**：`GET /user/admin/list?page=1&size=10`

**响应**：`Result<PageResult<AdminUserVO>>`

**AdminUserVO**（不含 password，可直接序列化返回）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户ID |
| username | String | 用户名 |
| nickname | String | 昵称 |
| avatar | String | 头像URL |
| email | String | 邮箱 |
| role | String | 角色 |
| status | Integer | 状态 |
| createTime | LocalDateTime | 注册时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 3,
    "list": [
      {
        "id": 1,
        "username": "admin",
        "nickname": "管理员",
        "avatar": null,
        "email": "admin@nova.blog",
        "role": "ADMIN",
        "status": 1,
        "createTime": "2025-06-01 10:00:00"
      }
    ]
  }
}
```

#### 修改用户状态

**请求**：`PUT /user/admin/status`

```json
{
  "userId": 2,
  "status": 0
}
```

**响应**：`Result<Void>`

#### 文章列表（管理）

**请求**：`GET /article/admin/list?page=1&size=10&keyword=Spring`

> keyword 可选，按标题模糊搜索

**响应**：`Result<PageResult<ArticleVO>>`

> 复用 ArticleVO，包含所有文章（status=0 草稿 和 status=1 已发布）

#### 评论列表（管理）

**请求**：`GET /comment/admin/list?page=1&size=10&articleId=1`

> articleId 可选，用于筛选特定文章的评论

**响应**：`Result<PageResult<AdminCommentVO>>`

**AdminCommentVO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 评论ID |
| content | String | 评论内容 |
| articleId | Long | 所属文章ID |
| articleTitle | String | 所属文章标题 |
| user | UserVO | 评论者信息 |
| parentId | Long | 父评论ID（一级为 null） |
| replyToUser | UserVO | 被回复者信息 |
| createTime | LocalDateTime | 创建时间 |

> 查询所有 status=1 的评论（一级+二级），扁平化展示，每行一条评论。

#### 创建分类

**请求**：`POST /category`

```json
{
  "name": "后端开发",
  "description": "Java、Spring 等后端技术"
}
```

**响应**：`Result<Long>`（分类ID）

#### 修改分类

**请求**：`PUT /category`

```json
{
  "id": 1,
  "name": "后端开发",
  "description": "Java、Spring 等后端技术文章"
}
```

**响应**：`Result<Void>`

#### 删除分类

**请求**：`DELETE /category/1`

**响应**：`Result<Void>`

#### 创建标签

**请求**：`POST /tag`

```json
{
  "name": "Spring Boot"
}
```

**响应**：`Result<Long>`（标签ID）

#### 修改标签

**请求**：`PUT /tag`

```json
{
  "id": 1,
  "name": "SpringBoot"
}
```

**响应**：`Result<Void>`

#### 删除标签

**请求**：`DELETE /tag/1`

**响应**：`Result<Void>`

## 3. 数据库变更

无需新增表或字段，已有表结构覆盖需求。

### 3.1 Mapper 新增方法

**UserMapper**：
```java
// 分页查询用户列表（按注册时间倒序）
List<User> findList(@Param("offset") int offset, @Param("size") int size);

// 查询用户总数
Long countAll();

// 更新用户状态
int updateStatus(@Param("id") Long id, @Param("status") Integer status);
```

> **AdminUserVO 位置**：`com.novablog.vo.AdminUserVO`
>
> **注意**：查询返回的 User 实体含 password 字段，Controller 中需转换为 AdminUserVO 返回，禁止直接序列化 User。

**UserMapper XML 新增**：
```xml
<!-- 分页查询用户列表（按注册时间倒序） -->
<select id="findList" resultMap="userResultMap">
    SELECT * FROM user ORDER BY create_time DESC LIMIT #{size} OFFSET #{offset}
</select>

<!-- 查询用户总数 -->
<select id="countAll" resultType="java.lang.Long">
    SELECT COUNT(*) FROM user
</select>

<!-- 更新用户状态 -->
<update id="updateStatus">
    UPDATE user SET status = #{status} WHERE id = #{id}
</update>
```

**ArticleMapper**：
```java
// 管理员查询所有文章（含草稿，按创建时间倒序）
List<ArticleVO> findAdminList(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

// 管理员查询文章总数
Long countAdminList(@Param("keyword") String keyword);
```

**ArticleMapper XML 新增**：
```xml
<!-- 管理员查询所有文章（含草稿） -->
<select id="findAdminList" resultMap="articleVOResultMap">
    SELECT
        a.id, a.title, a.summary, a.cover,
        a.view_count, a.like_count, a.status,
        a.user_id AS author_id,
        u.nickname AS author_nickname,
        u.avatar AS author_avatar,
        a.category_id,
        c.name AS category_name,
        a.create_time
    FROM article a
    LEFT JOIN user u ON a.user_id = u.id
    LEFT JOIN category c ON a.category_id = c.id
    WHERE 1=1
    <if test="keyword != null and keyword != ''">
        AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.summary LIKE CONCAT('%', #{keyword}, '%'))
    </if>
    ORDER BY a.create_time DESC
    LIMIT #{size} OFFSET #{offset}
</select>

<!-- 管理员查询文章总数 -->
<select id="countAdminList" resultType="java.lang.Long">
    SELECT COUNT(*) FROM article a
    WHERE 1=1
    <if test="keyword != null and keyword != ''">
        AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.summary LIKE CONCAT('%', #{keyword}, '%'))
    </if>
</select>
```

> **keyword 处理**：和现有 `findList` 一致，Service 层去除首尾空格、过滤 SQL 通配符 `%` `_` `*`。
>
> **tags 和实时计数**：Service 层查询出列表后，遍历每篇文章，调用 `findTagNamesByArticleId` 补充标签，从 Redis 读取实时 viewCount/likeCount（和现有 `findList` 逻辑一致）。

**CommentMapper**：
```java
// 管理员查询所有评论（扁平化，JOIN 文章标题，按创建时间倒序）
List<AdminCommentVO> findAdminList(@Param("articleId") Long articleId, @Param("offset") int offset, @Param("size") int size);

// 管理员查询评论总数
Long countAdminList(@Param("articleId") Long articleId);
```

**CommentMapper XML 新增**（需定义 `adminCommentVOResultMap`）：
```xml
<!-- 管理员评论 VO 映射 -->
<resultMap id="adminCommentVOResultMap" type="com.novablog.vo.AdminCommentVO">
    <id property="id" column="id"/>
    <result property="content" column="content"/>
    <result property="articleId" column="article_id"/>
    <result property="articleTitle" column="article_title"/>
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

<!-- 管理员查询所有评论（JOIN 文章标题） -->
<select id="findAdminList" resultMap="adminCommentVOResultMap">
    SELECT
        c.id, c.content, c.article_id, c.user_id, c.parent_id, c.reply_to_id, c.create_time,
        a.title AS article_title,
        u.id AS uid, u.nickname AS unickname, u.avatar AS uavatar,
        ru.id AS ruid, ru.nickname AS runickname
    FROM comment c
    LEFT JOIN article a ON c.article_id = a.id
    LEFT JOIN user u ON c.user_id = u.id
    LEFT JOIN user ru ON c.reply_to_id = ru.id
    WHERE c.status = 1
    <if test="articleId != null">
        AND c.article_id = #{articleId}
    </if>
    ORDER BY c.create_time DESC
    LIMIT #{size} OFFSET #{offset}
</select>

<!-- 管理员查询评论总数 -->
<select id="countAdminList" resultType="java.lang.Long">
    SELECT COUNT(*) FROM comment
    WHERE status = 1
    <if test="articleId != null">
        AND article_id = #{articleId}
    </if>
</select>
```

**CategoryMapper**：
```java
// 插入分类
int insert(Category category);

// 更新分类
int update(Category category);

// 删除分类
int deleteById(@Param("id") Long id);

// 根据名称查询分类
Category findByName(@Param("name") String name);

// 查询引用该分类的文章数量
Long countArticlesByCategoryId(@Param("categoryId") Long categoryId);
```

**CategoryMapper XML 新增**：
```xml
<!-- 根据名称查询分类 -->
<select id="findByName" resultMap="categoryResultMap">
    SELECT * FROM category WHERE name = #{name} LIMIT 1
</select>

<!-- 插入分类 -->
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO category (name, description) VALUES (#{name}, #{description})
</insert>

<!-- 更新分类 -->
<update id="update">
    UPDATE category SET name = #{name}, description = #{description} WHERE id = #{id}
</update>

<!-- 删除分类 -->
<delete id="deleteById">
    DELETE FROM category WHERE id = #{id}
</delete>

<!-- 查询引用该分类的文章数量 -->
<select id="countArticlesByCategoryId" resultType="java.lang.Long">
    SELECT COUNT(*) FROM article WHERE category_id = #{categoryId}
</select>
```

**TagMapper**：
```java
// 更新标签
int update(Tag tag);

// 删除标签
int deleteById(@Param("id") Long id);

// 删除文章标签关联
int deleteArticleTagByTagId(@Param("tagId") Long tagId);
```

**TagMapper XML 新增**：
```xml
<!-- 更新标签 -->
<update id="update">
    UPDATE tag SET name = #{name} WHERE id = #{id}
</update>

<!-- 删除标签 -->
<delete id="deleteById">
    DELETE FROM tag WHERE id = #{id}
</delete>

<!-- 删除文章标签关联 -->
<delete id="deleteArticleTagByTagId">
    DELETE FROM article_tag WHERE tag_id = #{tagId}
</delete>
```

## 4. 后端实现

### 4.1 JwtInterceptor 增强

**第一步：注入 UserMapper**

`JwtInterceptor` 当前只注入了 `JwtUtil`，需增加 `UserMapper`：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;  // 新增注入
    private final ObjectMapper objectMapper = new ObjectMapper();
    // ...
}
```

**第二步：两处增加用户状态检查**

位置 1 — 通用 Token 验证逻辑（非公开接口）：

```java
// 2. 解析并验证 Token
try {
    String tokenType = jwtUtil.getTokenType(token);
    if (!"access".equals(tokenType)) {
        writeErrorResponse(response, 401, "Token 类型错误");
        return false;
    }

    UserDTO user = jwtUtil.getUserFromToken(token);
    UserContext.set(user);

    // 新增：实时校验用户状态
    User dbUser = userMapper.findById(user.getId());
    if (dbUser == null || dbUser.getStatus() == 0) {
        writeErrorResponse(response, 401, "用户不存在或已被禁用");
        return false;
    }

    return true;
} catch (ExpiredJwtException e) {
    // ... 原有逻辑
}
```

位置 2 — `GET /article/{id}` 特殊处理（带 Token 时）：

```java
if ("GET".equals(method) && uri.matches("/article/\\d+")) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        try {
            String token = authHeader.substring(7);
            UserDTO user = jwtUtil.getUserFromToken(token);
            UserContext.set(user);

            // 新增：实时校验用户状态
            User dbUser = userMapper.findById(user.getId());
            if (dbUser == null || dbUser.getStatus() == 0) {
                writeErrorResponse(response, 401, "用户不存在或已被禁用");
                return false;
            }
        } catch (Exception e) {
            // Token 无效或过期，忽略，作为未登录用户处理
        }
    }
    return true;
}
```

> **性能考虑**：每次请求都查询数据库会增加开销。当前项目量级较小，此方案可接受。后续可优化为 Redis 缓存用户状态。
>
> **公开接口处理**：被禁用户不带 Token 仍可访问公开接口（如文章列表），带已禁用 Token 则返回 401。这是安全且合理的行为。

### 4.2 管理员权限检查工具方法

在每个需要 ADMIN 权限的 Controller 方法中调用：

```java
private void checkAdmin() {
    if (!"ADMIN".equals(UserContext.getRole())) {
        throw new BusinessException(403, "无权访问");
    }
}
```

### 4.3 各管理接口实现

#### UserController 新增

```java
@GetMapping("/admin/list")
public Result<PageResult<AdminUserVO>> adminList(
        @RequestParam(required = false, defaultValue = "1") Integer page,
        @RequestParam(required = false, defaultValue = "10") Integer size) {
    checkAdmin();
    // 分页参数修正
    if (page == null || page < 1) page = 1;
    if (size == null || size < 1) size = 10;
    if (size > 50) size = 50;
    int offset = (page - 1) * size;
    // 查询并转换为 AdminUserVO
}

@PutMapping("/admin/status")
public Result<Void> updateStatus(@RequestBody UserStatusDTO dto) {
    checkAdmin();
    // status 只能为 0 或 1
    if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
        throw new BusinessException(400, "状态值只能为 0 或 1");
    }
    // 不能禁用自己
    if (dto.getUserId().equals(UserContext.getUserId())) {
        throw new BusinessException(400, "不能禁用当前登录用户");
    }
    // 校验用户是否存在
    User user = userMapper.findById(dto.getUserId());
    if (user == null) {
        throw new BusinessException(404, "用户不存在");
    }
    userMapper.updateStatus(dto.getUserId(), dto.getStatus());
    return Result.success();
}
```

#### ArticleController 新增

```java
@GetMapping("/admin/list")
public Result<PageResult<ArticleVO>> adminList(
        @RequestParam(required = false, defaultValue = "1") Integer page,
        @RequestParam(required = false, defaultValue = "10") Integer size,
        @RequestParam(required = false) String keyword) {
    checkAdmin();
    // 分页参数修正
    if (page == null || page < 1) page = 1;
    if (size == null || size < 1) size = 10;
    if (size > 50) size = 50;
    int offset = (page - 1) * size;
    // 查询所有文章（含草稿）
}
```

#### CommentController 新增

```java
@GetMapping("/admin/list")
public Result<PageResult<AdminCommentVO>> adminList(
        @RequestParam(required = false, defaultValue = "1") Integer page,
        @RequestParam(required = false, defaultValue = "10") Integer size,
        @RequestParam(required = false) Long articleId) {
    checkAdmin();
    // 分页参数修正
    if (page == null || page < 1) page = 1;
    if (size == null || size < 1) size = 10;
    if (size > 50) size = 50;
    int offset = (page - 1) * size;
    // 查询所有评论（含文章标题）
}
```

#### CategoryController 扩展

```java
@PostMapping
public Result<Long> create(@RequestBody CategoryDTO dto) {
    checkAdmin();
    // 参数校验
    if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
        throw new BusinessException(400, "分类名称长度必须为1-20位");
    }
    // 分类名称唯一性校验
    Category existing = categoryMapper.findByName(dto.getName());
    if (existing != null) {
        throw new BusinessException(409, "分类名称已存在");
    }
    Category category = new Category();
    category.setName(dto.getName());
    category.setDescription(dto.getDescription());
    categoryMapper.insert(category);
    return Result.success(category.getId());
}

@PutMapping
public Result<Void> update(@RequestBody CategoryDTO dto) {
    checkAdmin();
    if (dto.getId() == null) {
        throw new BusinessException(400, "分类ID不能为空");
    }
    if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
        throw new BusinessException(400, "分类名称长度必须为1-20位");
    }
    // 校验存在性
    Category existing = categoryMapper.findById(dto.getId());
    if (existing == null) {
        throw new BusinessException(404, "分类不存在");
    }
    // 名称唯一性校验（排除自身）
    Category nameExists = categoryMapper.findByName(dto.getName());
    if (nameExists != null && !nameExists.getId().equals(dto.getId())) {
        throw new BusinessException(409, "分类名称已存在");
    }
    Category category = new Category();
    category.setId(dto.getId());
    category.setName(dto.getName());
    category.setDescription(dto.getDescription());
    categoryMapper.update(category);
    return Result.success();
}

@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id) {
    checkAdmin();
    // 校验存在性
    Category category = categoryMapper.findById(id);
    if (category == null) {
        throw new BusinessException(404, "分类不存在");
    }
    // 校验是否有文章引用，有则禁止删除
    Long count = categoryMapper.countArticlesByCategoryId(id);
    if (count > 0) {
        throw new BusinessException(400, "该分类下有文章，无法删除");
    }
    categoryMapper.deleteById(id);
    return Result.success();
}
```

#### TagController 扩展

```java
@PostMapping
public Result<Long> create(@RequestBody TagDTO dto) {
    checkAdmin();
    // 参数校验
    if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
        throw new BusinessException(400, "标签名称长度必须为1-20位");
    }
    // 名称唯一性校验
    Tag existing = tagMapper.findByName(dto.getName());
    if (existing != null) {
        throw new BusinessException(409, "标签名称已存在");
    }
    Tag tag = new Tag();
    tag.setName(dto.getName());
    tagMapper.insert(tag);
    return Result.success(tag.getId());
}

@PutMapping
public Result<Void> update(@RequestBody TagDTO dto) {
    checkAdmin();
    if (dto.getId() == null) {
        throw new BusinessException(400, "标签ID不能为空");
    }
    if (dto.getName() == null || dto.getName().isEmpty() || dto.getName().length() > 20) {
        throw new BusinessException(400, "标签名称长度必须为1-20位");
    }
    // 校验存在性
    Tag existing = tagMapper.findById(dto.getId());
    if (existing == null) {
        throw new BusinessException(404, "标签不存在");
    }
    // 名称唯一性校验（排除自身）
    Tag nameExists = tagMapper.findByName(dto.getName());
    if (nameExists != null && !nameExists.getId().equals(dto.getId())) {
        throw new BusinessException(409, "标签名称已存在");
    }
    Tag tag = new Tag();
    tag.setId(dto.getId());
    tag.setName(dto.getName());
    tagMapper.update(tag);
    return Result.success();
}

@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id) {
    checkAdmin();
    // 校验存在性
    Tag tag = tagMapper.findById(id);
    if (tag == null) {
        throw new BusinessException(404, "标签不存在");
    }
    // 删除 article_tag 关联，再删除标签
    tagMapper.deleteArticleTagByTagId(id);
    tagMapper.deleteById(id);
    return Result.success();
}
```

## 5. 核心逻辑

### 5.1 JwtInterceptor 用户状态校验 + findDetail 草稿权限放行 ADMIN

**JwtInterceptor 增强**：
位置：Token 验证通过后、UserContext.set() 之后

1. 从 Token 解析出 userId
2. 查询数据库 `SELECT status FROM user WHERE id = ?`
3. 用户不存在或 status = 0 → 返回 401
4. 用户正常 → 继续处理请求

**findDetail 草稿权限修改**（`ArticleServiceImpl` 第 209-214 行）：

原逻辑仅作者本人可查看草稿，需增加 ADMIN 放行：

```java
// 2. 草稿权限判断：作者本人或管理员可查看
if (detail.getStatus() != null && detail.getStatus() == 0) {
    Long currentUserId = UserContext.getUserId();
    Long authorId = detail.getAuthor() != null ? detail.getAuthor().getId() : null;
    String currentRole = UserContext.getRole();
    if ((currentUserId == null || !currentUserId.equals(authorId)) 
            && !"ADMIN".equals(currentRole)) {
        throw new BusinessException(404, "文章不存在");
    }
    // 草稿不增加浏览量
}
```

### 5.2 分类删除流程

1. 校验 ADMIN 权限
2. 查询分类是否存在
3. 查询 `countArticlesByCategoryId` → > 0 则抛异常 "该分类下有文章，无法删除"
4. 执行删除

### 5.3 标签删除流程

1. 校验 ADMIN 权限
2. 查询标签是否存在
3. 删除 `article_tag` 表中该标签的所有关联
4. 删除标签本身

### 5.4 用户禁用流程

1. 校验 ADMIN 权限
2. 校验不能禁用自己
3. 执行 `UPDATE user SET status = ? WHERE id = ?`
4. 被禁用用户的已有 Token：
   - 下次请求时 JwtInterceptor 会查询数据库状态 → 返回 401
   - 实现即时失效

### 5.5 异常处理

| 异常场景 | 返回结果 |
|----------|----------|
| 非 ADMIN 访问管理接口 | 403 "无权访问" |
| 禁用自己 | 400 "不能禁用当前登录用户" |
| 用户不存在 | 404 "用户不存在" |
| 分类/标签不存在 | 404 "分类/标签不存在" |
| 分类名称已存在 | 409 "分类名称已存在" |
| 标签名称已存在 | 409 "标签名称已存在" |
| 分类下有文章 | 400 "该分类下有文章，无法删除" |
| status 不为 0 或 1 | 400 "状态值只能为 0 或 1" |
| 非作者/ADMIN 访问草稿 | 404 "文章不存在" |

## 6. 安全考虑

### 6.1 权限控制

| 接口 | 权限要求 | 越权处理 |
|------|----------|----------|
| 所有 /admin/* 接口 | ADMIN | 403 "无权访问" |
| 分类/标签增删改 | ADMIN | 403 "无权访问" |

### 6.2 防误操作

- 禁止管理员禁用自己（防止系统无管理员可用）
- 分类删除前检查文章引用
- 用户状态修改时记录日志（可选）

### 6.3 Token 即时失效

JwtInterceptor 增加用户状态实时校验后：
- 用户被禁用时，已有的 Access Token 在下次请求时立即失效
- Refresh Token 同样会在刷新时失效（refresh 接口也经过拦截器）

## 7. 前端交互

### 7.1 页面结构

AdminView.vue 采用 **el-tabs** 单页面方案，左侧窄边栏固定，右侧主内容区随 tab 切换：

```
后台管理页面
├── 顶部导航（含返回首页链接）
└── 主内容区
    └── el-tabs（左侧标签栏纵向排列）
        ├── 用户管理
        ├── 文章管理
        ├── 评论管理
        ├── 分类管理
        └── 标签管理
```

> **方案选择**：使用 `el-tabs` 而非子路由，减少组件文件数量，所有管理模块在一个 Vue 文件中实现，降低复杂度。每个 tab-pane 内独立维护分页和 loading 状态。

### 7.2 路由配置

```js
{
  path: '/admin',
  name: 'Admin',
  component: () => import('../views/AdminView.vue'),
  meta: { requiresAuth: true, requiresAdmin: true }
}
```

路由守卫增加 ADMIN 检查（异步确保 userInfo 已加载）：

```js
router.beforeEach(async (to, from, next) => {
  // 1. 需要登录的页面检查 token
  if (to.meta.requiresAuth || to.meta.requiresAdmin) {
    const token = localStorage.getItem('token')
    if (!token) return next('/login')
  }

  // 2. 需要 ADMIN 的页面检查角色
  if (to.meta.requiresAdmin) {
    const userStore = useUserStore()
    // 如果 userInfo 为空（如页面刷新后），先异步获取
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
```

> **竞态条件处理**：路由守卫使用 `async/await`，当 userInfo 为空时主动请求 `/user/profile`，确保 ADMIN 判断在用户信息恢复后才执行。
>
> **与 App.vue 的协调**：App.vue `onMounted` 和路由守卫可能同时请求 `/user/profile`，造成重复请求。由于两者请求相同接口且结果一致，重复请求不会导致数据错误。若需优化，可在 Pinia store 中增加 `isLoadingProfile` 标志位进行互斥（当前设计暂不需要）。

### 7.3 导航栏入口

所有带导航栏的页面（HomeView.vue、ArticleDetailView.vue、ProfileView.vue、ArticleEditView.vue）中，对 ADMIN 角色显示"后台管理"入口：

```
导航栏
├── ...（已有链接）
└── ADMIN 可见：后台管理 → /admin
```

判断条件：`userStore.userInfo?.role === 'ADMIN'`

### 7.4 各管理模块

**用户管理**：
- el-table 展示用户列表
- 操作列：启用/禁用开关（el-switch）
- 分页

**文章管理**：
- el-table 展示所有文章（含草稿）
- 状态列用 tag 区分（已发布/草稿）
- 操作列：查看、编辑、删除
- 分页 + 标题搜索

**评论管理**：
- el-table 展示所有评论（一级+二级，扁平化）
- 显示评论内容、所属文章标题、评论者、时间
- parentId 不为 null 时标注为"回复"
- 操作列：删除
- 分页 + 按文章筛选

**分类管理**：
- el-table 展示分类列表
- 操作列：编辑、删除
- 顶部"新增分类"按钮 → el-dialog 表单

**标签管理**：
- el-table 展示标签列表
- 操作列：编辑、删除
- 顶部"新增标签"按钮 → el-dialog 表单

### 7.5 API 模块

```js
// api/user.js 新增
export function getAdminUserList(params) { return request({ url: '/user/admin/list', method: 'get', params }) }
export function updateUserStatus(data) { return request({ url: '/user/admin/status', method: 'put', data }) }

// api/article.js 新增
export function getAdminArticleList(params) { return request({ url: '/article/admin/list', method: 'get', params }) }

// api/comment.js 新增
export function getAdminCommentList(params) { return request({ url: '/comment/admin/list', method: 'get', params }) }

// api/category.js 新增
export function createCategory(data) { return request({ url: '/category', method: 'post', data }) }
export function updateCategory(data) { return request({ url: '/category', method: 'put', data }) }
export function deleteCategory(id) { return request({ url: `/category/${id}`, method: 'delete' }) }

// api/tag.js 新增
export function createTag(data) { return request({ url: '/tag', method: 'post', data }) }
export function updateTag(data) { return request({ url: '/tag', method: 'put', data }) }
export function deleteTag(id) { return request({ url: `/tag/${id}`, method: 'delete' }) }
```

## 8. 任务拆分

| 序号 | 子任务 | 依赖 | 说明 |
|------|--------|------|------|
| 1 | JwtInterceptor 增加用户状态校验 | — | 被禁用用户 token 即时失效 |
| 2 | 各 Mapper 新增 SQL | — | User/Article/Comment/Category/Tag |
| 3 | UserController 新增 admin 接口 | 1 | 用户列表 + 状态修改 |
| 4 | ArticleController 新增 admin 接口 | 1 | 文章列表（管理） |
| 5 | CommentController 新增 admin 接口 | 1 | 评论列表（管理） |
| 6 | CategoryController 扩展 CRUD | 1 | 增删改 + 引用检查 |
| 7 | TagController 扩展 CRUD | 1 | 增删改 + 关联清理 |
| 8 | 验证 WebConfig 无需修改 | — | 确认 admin 接口不在放行列表中 |
| 9 | 后端 Postman 测试 | 3-7 | 各管理接口权限/功能测试 |
| 10 | 前端路由守卫增加 ADMIN 检查（含异步恢复） | — | meta.requiresAdmin + async/await |
| 11 | 前端导航栏增加后台管理入口 | — | ADMIN 角色可见 |
| 12 | 前端 API 模块扩展 | — | 各 admin API |
| 13 | 前端 AdminView 开发 | 10,11,12 | 后台管理页面 |
| 14 | 前后端联调 | 9,13 | 完整流程验证 |
| 15 | Git 提交 | 14 | — |

## 9. 补充说明

### 9.1 已有 ADMIN 权限支持

以下接口已实现 ADMIN 权限（无需修改）：
- `PUT /article` — 修改文章（ArticleServiceImpl.checkPermission 支持 ADMIN）
- `DELETE /article/{id}` — 删除文章（同上）
- `DELETE /comment/{id}` — 删除评论（CommentServiceImpl 已检查 role == ADMIN）

### 9.2 前端 userInfo 恢复与竞态条件

App.vue 已包含页面刷新后恢复 userInfo 的逻辑（通过 `/user/profile`）。但路由守卫可能在 App.vue `onMounted` 完成前执行，导致 ADMIN 判断错误。

**解决方案**：路由守卫使用 `async/await`，当 userInfo 为空时主动请求 `/user/profile`，确保角色判断在用户信息恢复后才执行。

### 9.3 分类/标签名称唯一性

- 分类名称：数据库无唯一索引，需在业务层校验（插入前查询是否已存在）
- 标签名称：数据库无唯一索引，同样业务层校验

### 9.4 删除分类的替代方案

如果分类下有文章但确实需要删除：
- 先批量修改这些文章的 category_id 为其他分类
- 再删除该分类

当前设计不支持此操作，需管理员手动处理。

---

**确认状态**：
- [x] 待评审
- [x] 已确认
- [ ] 需要修改
