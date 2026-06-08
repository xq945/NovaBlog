package com.novablog.controller;

import com.novablog.common.Result;
import com.novablog.common.exception.BusinessException;
import com.novablog.dto.UserDTO;
import com.novablog.entity.User;
import com.novablog.mapper.UserMapper;
import com.novablog.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 处理 Token 刷新等认证相关请求
 * 该接口不走 JWT 拦截器，由 Controller 自己验证 Refresh Token
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    /**
     * 使用 Refresh Token 换取新的 Access Token
     * 同时换发新的 Refresh Token（Token 旋转机制）
     *
     * @param request HTTP 请求（包含 Authorization 头）
     * @return 新的 token、refreshToken、expiresIn、userInfo
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(HttpServletRequest request) {
        // 1. 从请求头提取 Refresh Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(401, "请先登录");
        }

        String token = authHeader.substring(7);

        // 2. 解析并验证 Refresh Token
        try {
            Claims claims = jwtUtil.parseToken(token);

            // 验证 Token 类型必须是 refresh
            String tokenType = claims.get("type", String.class);
            if (!"refresh".equals(tokenType)) {
                throw new BusinessException(401, "Token 类型错误");
            }

            // 3. 提取用户ID，从数据库查询完整用户信息
            Long userId = claims.get("userId", Long.class);
            User user = userMapper.findById(userId);
            if (user == null || user.getStatus() == 0) {
                throw new BusinessException(401, "用户不存在或已被禁用");
            }

            // 4. 构建 UserDTO 生成新的 Access Token
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setUsername(user.getUsername());
            userDTO.setRole(user.getRole());

            String newAccessToken = jwtUtil.generateAccessToken(userDTO);
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());

            // 5. 组装用户信息
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("email", user.getEmail());
            userInfo.put("role", user.getRole());

            // 6. 返回新的 Token 和用户信息
            Map<String, Object> result = new HashMap<>();
            result.put("token", newAccessToken);
            result.put("refreshToken", newRefreshToken);
            result.put("expiresIn", jwtUtil.getExpiration() / 1000);
            result.put("userInfo", userInfo);

            return Result.success(result);

        } catch (ExpiredJwtException e) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        } catch (JwtException e) {
            throw new BusinessException(401, "Token 无效");
        }
    }
}
