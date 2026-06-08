# 设计文档：Redis 统计功能模块

## 1. 需求概述

- **功能名称**：Redis 统计功能
- **目标**：实现文章浏览量缓存、点赞功能、热门文章排行，提升读性能并减轻数据库压力
- **涉及角色**：访客（浏览、查看热门）、登录用户（点赞/取消点赞）、管理员（无特殊权限）
- **设计决策**：使用 Redis 作为实时计数缓存，MySQL 作为持久化存储，定时任务同步

## 2. 接口设计

### 2.1 接口列表

| 接口 | 方法 | 路径 | 权限 |
|------|------|------|------|
| 点赞文章 | POST | /article/like/{id} | 登录用户 |
| 取消点赞 | DELETE | /article/like/{id} | 登录用户 |
| 查询点赞状态 | GET | /article/like/status?articleId={id} | 登录用户 |
| 热门文章列表 | GET | /article/hot?size={size} | 公开 |

### 2.2 DTO/VO

**LikeStatusVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| liked | Boolean | 当前用户是否点赞 |
| likeCount | Long | 点赞总数 |

**HotArticleVO**（复用 ArticleVO，增加 hotScore 字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 文章ID |
| title | String | 标题 |
| summary | String | 摘要 |
| cover | String | 封面图 |
| viewCount | Long | 浏览量 |
| likeCount | Long | 点赞数 |
| hotScore | Double | 热度分 |
| author | UserVO | 作者信息 |
| category | CategoryVO | 分类信息 |
| tags | List<String> | 标签名称列表 |
| createTime | LocalDateTime | 创建时间 |

### 2.3 请求与响应

#### 点赞文章

**请求**：`POST /article/like/1`

**响应**：`Result<Void>`

**状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 成功 | — |
| 400 | 参数错误 | 文章ID为空 |
| 401 | 未登录 | — |
| 404 | 文章不存在 | — |
| 409 | 已点赞 | 重复点赞 |

#### 取消点赞

**请求**：`DELETE /article/like/1`

**响应**：`Result<Void>`

**状态码**：

| 状态码 | 含义 | 触发条件 |
|--------|------|----------|
| 200 | 成功 | — |
| 401 | 未登录 | — |
| 404 | 未点赞 | 用户未点赞该文章 |

#### 查询点赞状态

**请求**：`GET /article/like/status?articleId=1`

**响应**：`Result<LikeStatusVO>`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "liked": true,
    "likeCount": 128
  }
}
```

#### 热门文章列表

**请求**：`GET /article/hot?size=10`

> size 默认 10，最大 50

**响应**：`Result<List<HotArticleVO>>`

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "title": "Spring Boot 入门指南",
      "summary": "本文介绍 Spring Boot 的基础知识...",
      "cover": "https://example.com/cover.jpg",
      "viewCount": 1250,
      "likeCount": 128,
      "hotScore": 1634.0,
      "author": { "id": 1, "nickname": "作者", "avatar": null },
      "category": { "id": 1, "name": "后端" },
      "tags": ["Spring Boot", "Java"],
      "createTime": "2025-06-01 10:00:00"
    }
  ]
}
```

## 3. 数据库变更

### 3.1 新增字段

无需新增表，已有 `article` 表字段已覆盖需求：
- `view_count` — 浏览量（BigInt，默认 0）
- `like_count` — 点赞数（BigInt，默认 0）

### 3.2 Mapper 新增方法

**ArticleMapper 接口新增**：

```java
// 更新浏览量（定时任务调用）
int updateViewCount(@Param("id") Long id, @Param("viewCount") Long viewCount);

// 查询所有已发布文章（初始化用）
List<Article> findAllPublished();

// 根据 ID 列表批量查询文章（热门文章用）
List<ArticleVO> findByIds(@Param("ids") List<Long> ids);
```

### 3.3 Mapper XML 新增 SQL

```xml
<update id="updateViewCount">
    UPDATE article SET view_count = #{viewCount} WHERE id = #{id}
</update>

<select id="findAllPublished" resultType="com.novablog.entity.Article">
    SELECT id, view_count, like_count FROM article WHERE status = 1
</select>

<select id="findByIds" resultType="com.novablog.vo.ArticleVO">
    SELECT a.id, a.title, a.summary, a.cover, a.view_count, a.like_count,
           a.create_time, u.id as uid, u.nickname as unickname, u.avatar as uavatar,
           c.id as cid, c.name as cname
    FROM article a
    LEFT JOIN user u ON a.user_id = u.id
    LEFT JOIN category c ON a.category_id = c.id
    WHERE a.id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
    AND a.status = 1
</select>
```

## 4. Redis Key 设计

| Key | 类型 | 说明 | 过期策略 |
|-----|------|------|----------|
| `article:view:{id}` | String | 浏览量计数 | 无（持久化） |
| `article:like:{id}` | Set | 点赞用户ID集合 | 无（持久化） |
| `blog:hot` | ZSet | 热门文章排行，score=热度分 | 无（持久化） |

## 5. 后端实现

### 5.1 Redis 配置与工具类

**启用定时任务**：在启动类或配置类上添加 `@EnableScheduling` 注解。

**RedisUtil 工具类**：使用 `StringRedisTemplate` 操作 Redis，封装统一操作方法：

```java
@Component
@RequiredArgsConstructor
public class RedisUtil {
    private final StringRedisTemplate redisTemplate;
    
    // String 操作
    public Long incr(String key) { ... }
    public String get(String key) { ... }
    public void set(String key, String value) { ... }
    
    // Set 操作
    public Long sAdd(String key, String member) { ... }
    public Long sRem(String key, String member) { ... }
    public Boolean sIsMember(String key, String member) { ... }
    public Long sCard(String key) { ... }
    
    // ZSet 操作
    public void zAdd(String key, String member, double score) { ... }
    public Set<ZSetOperations.TypedTuple<String>> zRevRange(String key, long start, long end) { ... }
    public Double zScore(String key, String member) { ... }
    
    // 扫描匹配的所有 key
    public Set<String> scan(String pattern) { ... }
}
```

### 5.2 Service 接口扩展

在 ArticleService 中新增方法：

```java
void like(Long articleId);
void unlike(Long articleId);
LikeStatusVO getLikeStatus(Long articleId);
List<HotArticleVO> findHotArticles(Integer size);
```

### 5.3 浏览量双写逻辑

**读文章时（findDetail 方法改造）**：

1. 查询文章详情（不限制状态，已有逻辑）
2. 草稿判断（已有逻辑）
3. **已发布文章浏览量处理**：
   - 尝试 `INCR article:view:{id}`（使用 try-catch 包裹）
   - 成功：读取 Redis 中的 `article:view:{id}` 值，设置到 detail.viewCount
   - 失败（Redis 异常）：降级读取 MySQL `view_count`，记录 warn 日志
4. **已发布文章点赞数处理**：
   - 尝试从 Redis 读取：`SCARD article:like:{id}`
   - 成功：设置到 detail.likeCount
   - 失败（Redis 异常）：降级读取 MySQL `like_count`
5. **更新热门排行 ZSet**（Redis 正常时）：
   - 获取当前点赞数：`SCARD article:like:{id}`（Redis 为空则读 MySQL `like_count`）
   - 计算热度分：`viewCount * 1 + likeCount * 3`
   - `ZADD blog:hot {score} {articleId}`

> **定时任务多实例说明**：当前为单机部署，无需分布式锁。若后续多实例部署，需引入分布式锁（如 Redis RedLock）避免重复同步。

**Redis 异常降级原则**：
- Redis 操作全部使用 try-catch 包裹
- 异常时读 MySQL 原字段，不抛异常影响主流程
- 记录 warn 日志便于排查

### 5.4 定时任务

每小时执行一次，将 Redis 中的浏览量同步回 MySQL：

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncTask {

    private final RedisUtil redisUtil;
    private final ArticleMapper articleMapper;

    @Scheduled(cron = "0 0 * * * *")
    public void syncViewCountToDb() {
        // 1. 使用 SCAN 遍历所有 article:view:* key（避免 KEYS 阻塞）
        Set<String> keys = redisUtil.scan("article:view:*");
        if (keys.isEmpty()) {
            return;
        }

        // 2. 批量读取 Redis 值并更新 MySQL
        for (String key : keys) {
            try {
                String articleIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long articleId = Long.parseLong(articleIdStr);
                String viewCountStr = redisUtil.get(key);
                if (viewCountStr != null) {
                    Long viewCount = Long.parseLong(viewCountStr);
                    articleMapper.updateViewCount(articleId, viewCount);
                }
            } catch (Exception e) {
                log.error("同步浏览量失败, key={}", key, e);
            }
        }
        log.info("浏览量同步完成, 共 {} 条", keys.size());
    }
}
```

### 5.5 应用启动初始化

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisInitRunner implements ApplicationRunner {

    private final RedisUtil redisUtil;
    private final ArticleMapper articleMapper;

    @Override
    public void run(ApplicationArguments args) {
        // 检查是否已有 article:view 数据
        Set<String> keys = redisUtil.scan("article:view:*");
        if (!keys.isEmpty()) {
            log.info("Redis 中已有浏览量数据, 跳过初始化");
            return;
        }

        // 从 MySQL 加载所有已发布文章的 view_count
        List<Article> articles = articleMapper.findAllPublished();
        for (Article article : articles) {
            String key = "article:view:" + article.getId();
            redisUtil.set(key, String.valueOf(article.getViewCount()));
        }
        log.info("Redis 浏览量初始化完成, 共 {} 条", articles.size());
    }
}
```

## 6. 核心逻辑

### 6.1 点赞流程

1. **参数校验**：articleId 非空且为正整数
2. **校验文章**：存在且 status = 1，否则抛异常
3. **获取用户ID**：从 UserContext 获取当前用户ID
4. **Redis 操作**：`SADD article:like:{articleId} {userId}`
   - 返回 1（新增成功）→ 继续步骤 5
   - 返回 0（已存在）→ 抛 409 "已点赞"
5. **更新 MySQL**：`UPDATE article SET like_count = like_count + 1 WHERE id = {articleId}`
6. **更新热门排行**：
   - 获取 Redis 浏览量：`GET article:view:{articleId}`（为空则读 MySQL）
   - 获取 Redis 点赞数：`SCARD article:like:{articleId}`
   - 计算热度分：`viewCount * 1 + likeCount * 3`
   - `ZADD blog:hot {score} {articleId}`

### 6.2 取消点赞流程

1. **参数校验**：articleId 非空且为正整数
2. **获取用户ID**：从 UserContext 获取当前用户ID
3. **Redis 操作**：`SREM article:like:{articleId} {userId}`
   - 返回 1（移除成功）→ 继续步骤 4
   - 返回 0（不存在）→ 抛 404 "未点赞"
4. **更新 MySQL**：`UPDATE article SET like_count = like_count - 1 WHERE id = {articleId}`
5. **更新热门排行**：重新计算热度分并更新 ZSet（若点赞数归零且浏览量也为 0，可选 `ZREM blog:hot {articleId}`）

### 6.3 查询点赞状态流程

1. **参数校验**：articleId 非空且为正整数
2. **校验文章**：存在且 status = 1
3. **获取用户ID**：从 UserContext 获取当前用户ID
4. **查询 Redis**：`SISMEMBER article:like:{articleId} {userId}` → 返回 liked 布尔值
5. **查询点赞总数**：`SCARD article:like:{articleId}` → 返回 likeCount
   - 若 Redis 异常降级读 MySQL `like_count`

### 6.4 热门文章排行流程

1. **参数修正**：size 为空或 <1 → 10；size >50 → 50
2. **查询 ZSet**：`ZREVRANGE blog:hot 0 {size-1} WITHSCORES`
   - 返回空列表 → 返回空结果
3. **批量查询文章详情**：根据返回的 articleId 列表，批量查询 MySQL 获取文章信息
   - 过滤掉已删除或变为草稿的文章（status ≠ 1）
   - 同时从 ZSet 中移除这些无效条目：`ZREM blog:hot {articleId}`
4. **组装 HotArticleVO**：
   - 查询每篇文章的标签
   - 设置 viewCount（Redis `article:view:{id}` 或 MySQL）
   - 设置 likeCount（Redis `SCARD` 或 MySQL）
   - 设置 hotScore（ZSet 的 score）
5. 返回 List<HotArticleVO>

> **注意**：ZSet 中可能存在已删除或变草稿的文章，查询 MySQL 时过滤并清理 ZSet，保证热门列表数据准确。

**热度分公式**：`热度分 = 浏览量 × 1 + 点赞数 × 3`

### 6.5 异常处理

| 异常场景 | 返回结果 |
|----------|----------|
| articleId 为空 / 非正整数 | 400 "文章ID不能为空" / "文章ID格式错误" |
| 文章不存在 | 404 "文章不存在" |
| 文章 status = 0（草稿） | 400 "草稿文章禁止操作" |
| 重复点赞 | 409 "已点赞" |
| 取消点赞时未点赞 | 404 "未点赞" |
| 非登录用户调用点赞接口 | 401 "请先登录" |
| Redis 异常 | 降级处理，不影响主流程 |

## 7. 安全考虑

### 7.1 权限控制

| 接口 | 权限要求 | 未登录处理 |
|------|----------|------------|
| POST /article/like/{id} | 登录用户 | 401 |
| DELETE /article/like/{id} | 登录用户 | 401 |
| GET /article/like/status | 登录用户 | 401 |
| GET /article/hot | 公开 | 直接返回 |

> WebConfig 拦截器需放行 `/article/hot`，在 `excludePathPatterns` 中新增：`"/article/hot"`。

### 7.2 输入验证

- articleId：非空、正整数
- size：1-50，超出范围修正为边界值

### 7.3 防攻击

- **重复点赞**：SADD 原子操作，天然防并发重复点赞
- **Redis 雪崩**：定时任务只在单机执行，使用 try-catch 包裹所有 Redis 操作，异常时降级 MySQL
- **热点 Key**：`article:view:{id}` 对于热门文章可能成为热点，目前项目量级较小暂不处理，后续可优化为本地缓存 + Redis 异步批量写入
- **ZSet 无限增长**：只存储已发布文章的 score，文章删除时从 ZSet 移除

### 7.4 数据一致性

- **最终一致性**：Redis 和 MySQL 通过定时任务同步，容忍最多 1 小时的数据延迟
- **同步丢失防护**：定时任务使用 SCAN 而非 KEYS，避免阻塞 Redis；每条同步单独 try-catch，单条失败不影响其他
- **初始化一致性**：应用启动时若 Redis 为空，从 MySQL 加载，避免冷启动数据丢失

## 8. 前端交互

### 8.1 文章详情页点赞区域

在 ArticleDetailView.vue 中文章正文下方、评论区上方新增点赞区域：

```
点赞区域
├── 点赞按钮（带图标）
│   ├── 未登录：点击跳转 /login
│   └── 已登录：
│       ├── 未点赞：空心图标 + "点赞" + 点赞数
│       └── 已点赞：实心图标 + "已赞" + 点赞数（主色调高亮）
├── 浏览量显示（👁 图标 + viewCount）
└── 分享/收藏按钮（预留）
```

### 8.2 状态管理

| 状态 | 类型 | 说明 |
|------|------|------|
| liked | Ref<boolean> | 当前用户是否点赞 |
| likeCount | Ref<number> | 点赞总数 |
| likeLoading | Ref<boolean> | 点赞操作 loading（防止重复点击）|

### 8.3 交互流程

1. **页面加载**：
   - 调用 `GET /article/{id}` 获取文章详情（含 viewCount）
   - 若已登录，调用 `GET /article/like/status?articleId={id}` 获取点赞状态

2. **点击点赞**：
   - `likeLoading = true`
   - 调用 `POST /article/like/{id}`
   - 成功：`liked = true; likeCount++`
   - 失败：`ElMessage.error` 显示错误
   - `likeLoading = false`

3. **点击取消点赞**：
   - `likeLoading = true`
   - 调用 `DELETE /article/like/{id}`
   - 成功：`liked = false; likeCount--`
   - 失败：`ElMessage.error` 显示错误
   - `likeLoading = false`

### 8.4 热门文章展示

在 HomeView.vue 侧边栏（预留位置）展示热门文章列表：
- 页面加载时调用 `GET /article/hot?size=10`
- 展示文章标题 + 热度分/浏览量
- 点击跳转文章详情页

### 8.5 API 模块

```js
// api/article.js 中新增
export function likeArticle(id)      // POST /article/like/{id}
export function unlikeArticle(id)    // DELETE /article/like/{id}
export function getLikeStatus(articleId) // GET /article/like/status?articleId={articleId}
export function getHotArticles(size = 10) // GET /article/hot?size={size}
```

## 9. 任务拆分

| 序号 | 子任务 | 依赖 | 说明 |
|------|--------|------|------|
| 1 | 封装 RedisUtil 工具类 | — | String/Set/ZSet 常用操作 + SCAN |
| 2 | 新增 LikeStatusVO、HotArticleVO | — | LikeStatusVO 含 liked、likeCount；HotArticleVO 继承 ArticleVO 加 hotScore |
| 3 | Mapper 新增 SQL | — | updateViewCount、findAllPublished、findByIds |
| 4 | 浏览量双写改造（findDetail） | 1,3 | Redis 异常降级读 MySQL |
| 5 | 文章列表计数改造（findList） | 1 | 从 Redis 读取 viewCount/likeCount 替换 MySQL 值 |
| 6 | 点赞/取消点赞接口 | 1,3 | ArticleServiceImpl.like/unlike，@Transactional |
| 7 | 查询点赞状态接口 | 1,3 | ArticleServiceImpl.getLikeStatus |
| 8 | 热门文章排行接口 | 1,3 | ArticleServiceImpl.findHotArticles |
| 9 | 应用启动初始化 | 1,3 | RedisInitRunner 加载 MySQL 数据到 Redis |
| 10 | 定时任务同步浏览量 | 1,3 | ViewCountSyncTask 每小时同步，需 @EnableScheduling |
| 11 | Controller 新增接口 | 2,4,5,6,7,8 | like/unlike/likeStatus/hot |
| 12 | WebConfig 权限配置 | 11 | 放行 /article/hot |
| 13 | 后端 Postman 测试 | 11 | 点赞/取消/状态查询/热门列表/浏览量 |
| 14 | 前端 API 模块扩展 | — | article.js 新增 4 个方法 |
| 15 | 前端文章详情页点赞按钮 | 14 | ArticleDetailView.vue 改造 |
| 16 | 前端热门文章展示 | 14 | HomeView.vue 侧边栏 |
| 17 | 前后端联调 | 13,15,16 | 完整流程验证 |
| 18 | Git 提交 | 17 | — |

## 10. 补充说明

### 10.1 文章删除时的 Redis 清理

文章删除时（ArticleServiceImpl.delete）需要同步清理 Redis：
- `DEL article:view:{id}`
- `DEL article:like:{id}`
- `ZREM blog:hot {id}`

### 10.2 文章发布时的 Redis 初始化

新文章发布时：
- `SET article:view:{id} 0`
- 不加入 ZSet（热度分为 0 的文章不展示在热门列表）

### 10.3 文章更新时的 Redis 处理

文章状态变更为草稿时（update status=0）：从 ZSet 移除，但不删除 view 和 like 数据。
文章状态从草稿变更为已发布时（update status=1）：重新计算热度分并加入 ZSet。

### 10.4 事务边界

| 方法 | 是否需要 @Transactional | 原因 |
|------|------------------------|------|
| like | **是** | SADD + UPDATE like_count + ZADD，保证 Redis 和 MySQL 一致 |
| unlike | **是** | SREM + UPDATE like_count + ZADD/ZREM |
| findDetail | 否 | 读操作 |
| getLikeStatus | 否 | 读操作 |
| findHotArticles | 否 | 读操作 |

### 10.5 文章列表的 viewCount/likeCount 来源

`GET /article/list` 返回的 ArticleVO 中：
- **viewCount**：优先从 Redis `article:view:{id}` 读取，Redis 异常时降级读 MySQL
- **likeCount**：优先从 Redis `SCARD article:like:{id}` 读取，Redis 异常时降级读 MySQL

> 改造点：在 `ArticleServiceImpl.findList` 中，查询出列表后遍历每篇文章，从 Redis 读取实时计数替换 MySQL 值。
> **注意**：Redis 返回 Long，VO 中字段为 Integer，赋值时需做类型转换（或考虑将 VO 字段升级为 Long）。

### 10.6 前后端数据结构对齐

**点赞状态响应**：
- 后端 `LikeStatusVO` 字段：`liked` (Boolean)、`likeCount` (Long)
- 前端直接使用，无需转换

**热门文章响应**：
- 后端 `HotArticleVO` 复用 `ArticleVO` 全部字段，新增 `hotScore` (Double)
- 前端列表组件可直接复用 ArticleVO 的展示逻辑，hotScore 可选展示

**浏览量字段对齐**：
- 后端：`viewCount` (Long) — 实体类字段名为 `viewCount`（MyBatis 下划线转驼峰）
- 前端：`article.viewCount` — 直接使用

### 10.7 前端 API 命名

```js
// api/article.js
export function likeArticle(id)           // POST /article/like/{id}
export function unlikeArticle(id)         // DELETE /article/like/{id}
export function getLikeStatus(articleId)  // GET /article/like/status?articleId={articleId}
export function getHotArticles(size = 10) // GET /article/hot?size={size}
```

---

**确认状态**：
- [x] 待评审
- [ ] 已确认
- [ ] 需要修改
