package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.request.LoginDTO;
import com.novablog.dto.request.RegisterDTO;
import com.novablog.service.AuthService;
import com.novablog.service.SessionService;
import com.novablog.vo.SessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "认证管理", description = "登录、注册、Token 刷新、登出")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;

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

    @Operation(summary = "登录设备列表", description = "列出该账号全部活跃设备，并标记本机")
    @GetMapping("/sessions")
    public Result<List<SessionVO>> sessions() {
        return Result.success(sessionService.listSessions(requireLogin(), UserContext.getSid()));
    }

    @Operation(summary = "退出指定设备", description = "被踢出的设备在下一次请求时失效；踢的是本机时响应 current 为 true")
    @DeleteMapping("/sessions/{sid}")
    public Result<Map<String, Object>> kickSession(@PathVariable String sid) {
        boolean current = sessionService.kickSession(requireLogin(), sid, UserContext.getSid());
        return Result.success(Map.of("current", current));
    }

    @Operation(summary = "退出其他设备", description = "保留本机，撤销该账号其余全部会话")
    @DeleteMapping("/sessions/others")
    public Result<Map<String, Object>> kickOtherSessions() {
        int count = sessionService.kickOthers(requireLogin(), UserContext.getSid());
        return Result.success(Map.of("count", count));
    }

    /**
     * 取当前登录用户。这些接口不在放行名单里，理论上不会为空，
     * 但存量令牌或过滤器异常时仍要给出明确的 401 而不是 NPE
     */
    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
