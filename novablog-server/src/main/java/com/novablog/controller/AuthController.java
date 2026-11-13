package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.request.LoginDTO;
import com.novablog.dto.request.RegisterDTO;
import com.novablog.security.AuthCookieService;
import com.novablog.service.AuthService;
import com.novablog.service.SessionService;
import com.novablog.vo.SessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    private final AuthCookieService authCookieService;

    @Operation(summary = "用户登录", description = "返回 AccessToken + 用户信息；RefreshToken 通过 HttpOnly Cookie 下发")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO loginDTO,
                                             HttpServletResponse response) {
        return issueTokens(authService.login(loginDTO), response);
    }

    @Operation(summary = "用户注册", description = "注册成功后自动返回 AccessToken；RefreshToken 通过 HttpOnly Cookie 下发")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO registerDTO,
                                                HttpServletResponse response) {
        return issueTokens(authService.register(registerDTO), response);
    }

    @Operation(summary = "刷新 Token",
        description = "凭 HttpOnly Cookie 中的 RefreshToken 换发新的 AccessToken；RefreshToken 每次刷新同步轮换并重写 Cookie")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(HttpServletRequest request,
                                               HttpServletResponse response) {
        String refreshToken = authCookieService.resolveRefreshToken(request);
        if (refreshToken == null) {
            // 没有 RT 说明浏览器侧已经不可恢复，顺手清掉可能残留的脏 Cookie
            authCookieService.clearRefreshToken(response);
            throw new BusinessException(401, "请先登录");
        }
        try {
            return issueTokens(authService.refresh(refreshToken), response);
        } catch (BusinessException e) {
            // 只有确认性的认证失败才清 Cookie：Redis 抖动等可恢复错误若也清掉，
            // 一次故障就会把全体在线用户踢下线
            if (Integer.valueOf(401).equals(e.getCode())) {
                authCookieService.clearRefreshToken(response);
            }
            throw e;
        }
    }

    @Operation(summary = "登出", description = "撤销当前会话，该会话的 AccessToken 与 RefreshToken 一并失效，并清除 RefreshToken Cookie")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request,
                               @RequestBody(required = false) Map<String, String> body,
                               HttpServletResponse response) {
        // AT 可能已过期，优先用 AT 的 sid；取不到再回退到 Cookie 里的 RT。
        // body 中的 refreshToken 仅为过渡期兼容保留
        String accessToken = extractBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        String cookieToken = authCookieService.readRefreshToken(request);
        String refreshToken = cookieToken != null
            ? cookieToken
            : (body != null ? body.get("refreshToken") : null);

        try {
            authService.logout(accessToken, refreshToken);
        } finally {
            // 登出的意图就是结束本浏览器的登录态，服务端撤销失败也不能把 RT 留在客户端
            authCookieService.clearRefreshToken(response);
        }
        return Result.success();
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
    public Result<Map<String, Object>> kickSession(@PathVariable String sid,
                                                   HttpServletResponse response) {
        boolean current = sessionService.kickSession(requireLogin(), sid, UserContext.getSid());
        if (current) {
            // 踢掉本机时顺手清 Cookie：会话已经撤销，
            // 留着这张 RT 只会让浏览器每次刷新都白跑一次注定失败的续期
            authCookieService.clearRefreshToken(response);
        }
        return Result.success(Map.of("current", current));
    }

    @Operation(summary = "退出其他设备", description = "保留本机，撤销该账号其余全部会话")
    @DeleteMapping("/sessions/others")
    public Result<Map<String, Object>> kickOtherSessions() {
        int count = sessionService.kickOthers(requireLogin(), UserContext.getSid());
        return Result.success(Map.of("count", count));
    }

    /**
     * 把服务层返回的令牌对拆开：AT 照常返回，RT 只写 Cookie，不进入响应体
     *
     * <p>响应加 no-store，避免中间层缓存带令牌的登录/刷新结果
     */
    private Result<Map<String, Object>> issueTokens(Map<String, Object> data,
                                                    HttpServletResponse response) {
        authCookieService.moveRefreshTokenToCookie(data, response);
        return Result.success(data);
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
