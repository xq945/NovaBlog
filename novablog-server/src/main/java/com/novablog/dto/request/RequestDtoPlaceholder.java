package com.novablog.dto.request;

/**
 * TODO [第二阶段] dto/ 重组为 request/ 和 response/ 子包
 *
 * 需要移入此包的类（请求 DTO）：
 * - LoginDTO         → dto/request/LoginRequest
 * - RegisterDTO      → dto/request/RegisterRequest
 * - ArticleDTO       → dto/request/ArticleRequest
 * - CommentDTO       → dto/request/CommentRequest
 * - SummaryRequest   → dto/request/SummaryRequest
 * - UpdateProfileDTO → dto/request/UpdateProfileRequest
 * - UserStatusDTO    → dto/request/UserStatusRequest
 *
 * 注意：移动后需要同步更新所有 import 语句（约 50+ 处），涉及：
 * - Controller 层：AuthController, UserController, ArticleController, CommentController
 * - Service 层：AuthService, UserService, ArticleService, CommentService 及 Impl
 *
 * 建议：第二阶段执行时，先创建新类 → 逐步替换引用 → 删除旧类
 */
@SuppressWarnings("unused")
public class RequestDtoPlaceholder {
    private RequestDtoPlaceholder() {}
}
