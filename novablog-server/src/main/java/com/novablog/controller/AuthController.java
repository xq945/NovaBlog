package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.request.LoginDTO;
import com.novablog.dto.request.RegisterDTO;
import com.novablog.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "认证管理", description = "登录、注册、Token 刷新、登出")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录", description = "返回 AccessToken + RefreshToken + 用户信息")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }

    @Operation(summary = "用户注册", description = "注册成功后自动返回登录 Token")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO registerDTO) {
        return Result.success(authService.register(registerDTO));
    }

    @Operation(summary = "刷新 Token", description = "使用 RefreshToken 换发新的 AccessToken；RefreshToken 每次刷新同步轮换，旧令牌立即作废")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(401, "请先登录");
        }
        String refreshToken = authHeader.substring(7);
        return Result.success(authService.refresh(refreshToken));
    }

    @Operation(summary = "登出", description = "撤销当前会话，该会话的 AccessToken 与 RefreshToken 一并失效")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request,
                               @RequestBody(required = false) Map<String, String> body) {
        // 两个令牌都传，AT 已过期时后端还能凭 RT 找到会话并撤销
        String refreshToken = body != null ? body.get("refreshToken") : null;
        authService.logout(extractBearer(request.getHeader("Authorization")), refreshToken);
        return Result.success();
    }

    /**
     * 从 Authorization 头里取出 Bearer 令牌，缺失或格式不符时返回 null
     */
    private String extractBearer(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    @Operation(summary = "获取当前用户信息", description = "需要登录")
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.success(authService.profile());
    }
}
