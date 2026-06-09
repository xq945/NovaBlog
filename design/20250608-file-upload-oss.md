# 设计文档：文件上传模块（阿里云OSS）

## 1. 需求分析

### 1.1 功能目标
实现基于阿里云OSS的文件上传功能，支持以下场景：
- **文章封面图上传** — 替换当前手动输入URL的方式
- **用户头像上传** — 个人中心支持上传/修改头像
- **Markdown编辑器图片上传** — 文章正文中插入图片

### 1.2 技术选型决策

| 方案 | 描述 | 优点 | 缺点 | 结论 |
|------|------|------|------|------|
| A. 服务端签名直传 | 后端生成临时签名，前端直传OSS | 不占用服务器带宽 | 实现复杂，需处理跨域和回调 | 否 |
| B. 后端代理上传 | 前端→后端→OSS | 统一权限控制、文件校验简单 | 文件流经服务器 | **是** |

选择方案B（后端代理上传），原因：
- 个人博客流量不大，服务器带宽不是瓶颈
- 统一做登录鉴权、文件类型/大小校验更方便
- 与MdEditor等第三方组件集成更简单
- 代码量和复杂度显著降低

## 2. 接口设计

### 2.1 文件上传接口

```
POST /upload
```

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传的文件 |

**请求头**：`Authorization: Bearer {token}`（需登录）

**响应成功**（200）：
```json
{
  "code": 200,
  "data": {
    "url": "https://xq945.oss-cn-beijing.aliyuncs.com/uploads/20250608/a1b2c3d4e5f6.jpg",
    "filename": "a1b2c3d4e5f6.jpg",
    "size": 123456
  },
  "message": "success"
}
```

**响应失败**：
- `400` — 文件为空 / 文件类型不合法 / 文件超过大小限制
- `401` — 未登录
- `500` — OSS上传失败

### 2.2 接口约束

| 约束项 | 值 | 说明 |
|--------|-----|------|
| 最大文件大小（通用） | 5MB | 与SpringBoot multipart配置一致，适用于封面图/MdEditor图片 |
| 最大文件大小（头像） | 2MB | 头像场景单独限制，前端校验 |
| 允许的文件类型 | image/jpeg, image/png, image/gif, image/webp | 仅限图片 |
| 存储路径格式 | `uploads/{yyyyMMdd}/{uuid}.{ext}` | 按日期分目录，UUID文件名防冲突 |
| 文件名校验 | 去除路径遍历字符，强制UUID重命名 | 防止安全攻击 |
| URL长度限制 | 500字符 | avatar字段入库长度限制 |

## 3. 数据库变更

**无变更**。文件URL直接存储在已有字段中：
- `article.cover` — 文章封面图URL
- `user.avatar` — 用户头像URL

## 4. 核心逻辑

### 4.1 后端处理流程

```
1. 接收请求 → JWT拦截器校验登录状态
2. 校验文件是否为空
3. 校验文件类型（MIME类型 + 扩展名双重校验）
4. 校验文件大小
5. 生成唯一文件名：UUID + 原始扩展名
6. 构建OSS存储路径：uploads/{yyyyMMdd}/{filename}
7. 调用OSS SDK上传文件
8. 返回文件的公网访问URL
```

### 4.2 OSS配置

```yaml
aliyun:
  oss:
    endpoint: ${ALIYUN_OSS_ENDPOINT}        # oss-cn-beijing.aliyuncs.com
    bucket: ${ALIYUN_OSS_BUCKET}            # xq945
    access-key-id: ${ALIYUN_OSS_ACCESS_KEY_ID}
    access-key-secret: ${ALIYUN_OSS_ACCESS_KEY_SECRET}
```

**访问URL格式**：`https://{bucket}.{endpoint}/{objectKey}`

实际示例：`https://xq945.oss-cn-beijing.aliyuncs.com/uploads/20250608/a1b2c3d4e5f6.jpg`

### 4.3 文件类型白名单

```java
private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
    "image/jpeg", "image/png", "image/gif", "image/webp"
);
private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
```

双重校验逻辑：
1. 先校验 `file.getContentType()` 是否在白名单中
2. 再校验从原始文件名提取的扩展名是否在白名单中
3. 两者都通过才允许上传

**已知安全限制**：当前版本未做文件魔数（Magic Number）校验。恶意用户理论上可将可执行文件改名为图片扩展名上传，但由于：
- 文件存储在OSS而非服务器本地，不会直接执行
- 前端以 `<img>` 标签展示，不会解析执行非图片内容
- 个人博客场景下风险可控

**OSS孤儿文件问题**：用户上传头像/封面后点击取消不保存，OSS上会产生未被数据库引用的文件。个人博客场景下文件量小，当前版本不做清理。后续可通过定时任务扫描OSS与数据库差异进行清理。

如需更高安全性，后续可引入第三方库（如 Apache Tika）做文件头魔数校验。

### 4.4 异常处理

| 异常场景 | 返回状态码 | 错误消息 |
|----------|-----------|----------|
| 文件为空 | 400 | 请选择要上传的文件 |
| 文件超过5MB | 400 | 文件大小不能超过5MB |
| MIME类型不合法 | 400 | 不支持的文件类型，仅允许jpg/png/gif/webp |
| 扩展名不合法 | 400 | 不支持的文件格式 |
| 头像URL超过500字符 | 400 | 头像链接过长 |
| 文件读取失败 | 500 | 文件读取失败，请稍后重试 |
| OSS连接失败/无权限 | 500 | 文件上传失败，请稍后重试 |

## 5. 安全考虑

1. **登录鉴权**：上传接口走JWT拦截器，未登录用户无法上传
2. **文件类型白名单**：仅允许图片类型，禁止上传可执行文件、脚本等
3. **文件大小限制**：防止大文件攻击，占用OSS存储和带宽
4. **文件名随机化**：使用UUID重命名，防止：
   - 文件名冲突导致覆盖
   - 路径遍历攻击（`../../etc/passwd`）
   - 特殊字符注入
5. **MIME类型校验**：不仅校验扩展名，还校验Content-Type，防止修改扩展名绕过
6. **前后端双重校验**：前端 `before-upload` 作为体验优化快速拦截，但可被绕过（直接调API）。后端校验为**最终防线**，两者缺一不可
7. **OSSClient创建策略**：当前实现每次上传新建OSSClient实例（用完即关）。个人博客场景下QPS低，此策略简单安全。高并发场景可改用连接池或单例模式
8. **Bucket权限**：OSS Bucket设置为**私有读**还是**公共读**？
   - 建议设置为**公共读**（或特定目录公共读），因为文章封面和头像需要被所有访客访问
   - 如果Bucket整体私有，需要生成带签名的临时URL，复杂度高且不利于CDN缓存

## 6. 前端交互

### 6.1 文章封面图上传（ArticleEditView.vue）

替换当前的URL输入框为Element Plus Upload组件：

```
当前：el-input 手动输入封面图URL
改为：el-upload + 预览图
     - 支持点击上传
     - 上传成功后显示预览图
     - 支持删除/重新上传
     - 上传中显示loading
```

**交互流程**：
1. 用户点击上传区域，选择图片文件（前端先校验类型和大小）
2. 前端调用 `POST /api/upload`（携带token）
3. 上传成功后，将返回的URL填充到form.cover，显示预览图
4. 点击预览图上的删除按钮可移除封面图，恢复为上传状态

**前端校验**：类型(jpg/png/gif/webp)、大小(<5MB) — 作为体验优化，可被绕过，后端校验为最终防线。

### 6.2 用户头像上传（ProfileView.vue）

在个人中心编辑资料时增加头像上传：

```
在编辑模式下增加：
- 头像预览区域（圆形，与当前展示一致）
- 点击头像触发文件选择
- 上传成功后即时更新预览
- 保存个人资料时将avatar URL一并提交
```

**后端配合**：扩展 `PUT /user/profile` 接口，使其支持接收并持久化 `avatar` 字段。涉及以下修改：
- `dto/UpdateProfileDTO` 新增 `avatar` 字段
- `service/UserService` 及 `service/impl/UserServiceImpl` 的 `updateProfile` 方法增加 `avatar` 参数
- `mapper/UserMapper` 及 `mapper/UserMapper.xml` 的 `updateProfile` 增加 `avatar` 字段
- `controller/UserController` 将 `avatar` 从 DTO 透传至 Service

### 6.3 Markdown编辑器图片上传（ArticleEditView.vue）

MdEditor组件支持配置 `onUploadImg` 回调：

```javascript
const onUploadImg = async (files, callback) => {
  try {
    const res = await Promise.all(
      files.map(file => uploadFile(file))
    )
    const urls = res.map(item => {
      if (item.code === 200) {
        return item.data.url
      }
      throw new Error(item.message || '上传失败')
    })
    callback(urls)
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
    callback([]) // 通知 MdEditor 上传失败，取消 loading
  }
}
```

**交互流程**：
1. 用户在MdEditor中点击"插入图片"或拖拽图片到编辑器
2. MdEditor调用 `onUploadImg` 回调
3. 前端将文件通过 `/api/upload` 上传到后端
4. 后端返回URL，MdEditor将图片URL插入Markdown正文

## 7. 依赖变更

### 7.1 后端新增依赖（pom.xml）

```xml
<!-- 阿里云OSS SDK -->
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
</dependency>
```

### 7.2 配置变更

**application.yml** 修改：
1. multipart 配置调整：
```yaml
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 10MB   # 需大于 max-file-size，容纳 boundary 等开销
```
2. 新增OSS配置：
```yaml
# 8. 阿里云OSS配置
aliyun:
  oss:
    endpoint: ${ALIYUN_OSS_ENDPOINT}
    bucket: ${ALIYUN_OSS_BUCKET}
    access-key-id: ${ALIYUN_OSS_ACCESS_KEY_ID}
    access-key-secret: ${ALIYUN_OSS_ACCESS_KEY_SECRET}
```

**application-dev.yml.example** 新增：
```yaml
aliyun:
  oss:
    endpoint: oss-cn-beijing.aliyuncs.com
    bucket: YOUR_BUCKET_NAME
    access-key-id: YOUR_ACCESS_KEY_ID
    access-key-secret: YOUR_ACCESS_KEY_SECRET
```

## 8. 文件变更清单

### 后端

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 添加阿里云OSS SDK 3.17.4 |
| `application.yml` | 修改 | 添加 `aliyun.oss.*` 占位符配置；调整 multipart `max-request-size` 5MB→10MB |
| `application-dev.yml.example` | 修改 | 添加OSS配置模板 |
| `application-dev.yml` | 修改 | 修正键名：`alioss.*` → `aliyun.oss.*` |
| `config/OssProperties.java` | 新增 | OSS配置属性绑定类 |
| `controller/UploadController.java` | 新增 | 文件上传接口 |
| `dto/UpdateProfileDTO.java` | 修改 | 新增 `avatar` 字段 |
| `service/UserService.java` | 修改 | `updateProfile` 增加 `avatar` 参数 |
| `service/impl/UserServiceImpl.java` | 修改 | 头像更新逻辑与校验 |
| `mapper/UserMapper.java` | 修改 | `updateProfile` 增加 `avatar` 参数 |
| `mapper/UserMapper.xml` | 修改 | 更新SQL增加 `avatar` 字段 |
| `controller/UserController.java` | 修改 | `updateProfile` 透传 `avatar` |
| `common/Result.java` | 无需修改 | 复用现有统一响应 |

### 前端

| 文件 | 操作 | 说明 |
|------|------|------|
| `api/upload.js` | 新增 | 上传API（FormData封装，Content-Type由axios自动设置） |
| `views/ArticleEditView.vue` | 修改 | 封面图改为el-upload组件；MdEditor配置`@onUploadImg`图片上传 |
| `views/ProfileView.vue` | 修改 | 编辑模式头像上传；保存时提交avatar字段 |

## 9. 任务拆分

1. **后端**：添加OSS依赖 + 配置类 + UploadController
2. **后端**：扩展 `updateProfile` 接口支持 `avatar` 字段（DTO/Service/Mapper/Controller）
3. **Postman测试**：测试各种上传场景（正常/空文件/大文件/非法类型）
4. **前端**：编写upload.js API模块（注意Content-Type由axios自动设置）
5. **前端**：ArticleEditView.vue集成封面图上传 + MdEditor图片上传
6. **前端**：ProfileView.vue集成头像上传
7. **前后端联调**：三个场景分别验证
8. **迭代检错**：检查文档与代码一致性、命名对齐、边界条件、安全考虑
9. **Git提交**

## 10. 已确认配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| Endpoint | `oss-cn-beijing.aliyuncs.com` | 北京区域 |
| Bucket | `xq945` | 用户账号作为Bucket名 |
| AccessKey | 已配置 | 位于 `application-dev.yml`（gitignore，不提交） |

**注意事项**：
- Bucket 需设置为**公共读**，否则文章封面和头像无法被访客访问
- 生产环境应使用环境变量注入配置，避免将 AccessKey 写入代码仓库

## 11. 迭代优化记录

### 第一轮 — 基础结构
- 完成接口设计、OSS配置、核心逻辑、安全考虑、前端交互

### 第二轮 — 完整性检查
- 补充异常场景：文件读取失败、OSS连接失败/无权限
- 补充已知安全限制说明（文件魔数校验未实现，原因及后续扩展方案）

### 第三轮 — 一致性检查
- 修正文档变量名与代码对齐：`ALLOWED_TYPES`/`ALLOWED_EXTS` → `ALLOWED_CONTENT_TYPES`/`ALLOWED_EXTENSIONS`
- 修正文档示例URL与实际配置对齐：`novablog-xq945` → `xq945`
- 修正前端 `upload.js`：移除显式 `Content-Type` 头，由 axios 自动设置（避免 boundary 丢失）
- 补充文档8.1节缺少的后端文件变更记录（`UpdateProfileDTO`、`UserService`、`UserMapper`等）
- 修正 `application-dev.yml` 键名：`alioss.*` → `aliyun.oss.*`，与 `application.yml` 和 `OssProperties` 保持一致

### 第四轮 — 可实施性检查
- 移除 `UploadController.java` 中未使用的 `BinaryUtil` 导入
- 任务拆分增加第2步（扩展 `updateProfile` 接口）和第8步（迭代检错）

### 第五轮 — 深入边界条件检查
- 修正响应示例 message 字段：`"上传成功"` → `"success"`，与 `Result.success()` 实际返回值一致
- 修正 `application.yml` multipart 配置：`max-request-size` 5MB → 10MB，避免文件接近5MB时加上 boundary 开销导致请求被拒
- 补充接口约束：头像单独限制 2MB（前端校验）、avatar URL 入库长度限制 500 字符
- 补充异常场景：头像URL超过500字符
- 补充安全考虑：前后端双重校验策略、OSSClient 创建策略说明
- 补充前端交互：封面图删除功能、MdEditor 上传失败时调用 `callback([])` 取消 loading
- 修正 `ProfileView.vue`：取消编辑时重置 `avatarLoading` 状态，避免再次进入编辑模式时loading残留
- **发现遗留问题**：`md-editor-v3` 未在 `package.json` 中声明，需执行 `npm install md-editor-v3` 安装后才能联调
