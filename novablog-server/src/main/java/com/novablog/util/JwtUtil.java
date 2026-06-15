package com.novablog.util;

import com.novablog.config.JwtProperties;
import com.novablog.dto.UserDTO;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 * 负责 Token 的生成、解析和验证
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    /**
     * 获取签名密钥
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token
     *
     * @param user 用户信息
     * @return JWT 字符串
     */
    public String generateAccessToken(UserDTO user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim("type", "access")
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 生成 Refresh Token
     *
     * @param userId 用户ID
     * @return JWT 字符串
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getRefreshExpiration());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("type", "refresh")
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 解析 Token，获取 Claims
     *
     * @param token JWT 字符串
     * @return Claims 载荷
     * @throws JwtException Token 无效或过期时抛出
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户信息
     *
     * @param token JWT 字符串
     * @return UserDTO
     */
    public UserDTO getUserFromToken(String token) {
        Claims claims = parseToken(token);
        UserDTO user = new UserDTO();
        // jjwt 解析数字 claim 的类型不确定，先取 Object 再安全转换
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            user.setId(((Number) userIdObj).longValue());
        }
        user.setUsername(claims.get("username", String.class));
        user.setRole(claims.get("role", String.class));
        return user;
    }

    /**
     * 从 Token 中提取用户ID
     *
     * @param token JWT 字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        return null;
    }

    /**
     * 获取 Token 类型（access / refresh）
     *
     * @param token JWT 字符串
     * @return Token 类型
     */
    public String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("type", String.class);
    }

    /**
     * 从 Token 中提取 jti
     *
     * @param token JWT 字符串
     * @return jti
     */
    public String getJtiFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("jti", String.class);
    }

    /**
     * 从 Token 中提取过期时间
     *
     * @param token JWT 字符串
     * @return 过期时间
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }

    /**
     * 获取 Access Token 过期时间（毫秒）
     */
    public Long getExpiration() {
        return jwtProperties.getExpiration();
    }
}
