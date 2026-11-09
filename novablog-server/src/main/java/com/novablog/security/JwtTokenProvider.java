package com.novablog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * 创建 AccessToken
     *
     * @param sid 会话标识，同一次登录派生的 AT/RT 共享同一 sid，
     *            登出或判定令牌泄露时按 sid 整体撤销该会话
     */
    public String createAccessToken(Long userId, String username, List<String> roles, String sid) {
        return createToken(userId, username, roles, "ACCESS", accessExpiration, sid);
    }

    /**
     * 创建 RefreshToken。轮换时沿用同一 sid，使同一会话的历次轮换同属一个家族。
     */
    public String createRefreshToken(Long userId, String sid) {
        return createToken(userId, null, null, "REFRESH", refreshExpiration, sid);
    }

    private String createToken(Long userId, String username,
                                List<String> roles, String type, long expiration, String sid) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("type", type)
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSignKey());

        if (username != null) builder.claim("username", username);
        if (roles != null) builder.claim("roles", roles);
        if (sid != null) builder.claim("sid", sid);

        return builder.compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSignKey()).build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 空串或 null 抛的是 IllegalArgumentException（如 "Bearer " 后无内容），
            // 只捕 JwtException 会让它穿透到过滤器/登出/刷新接口变成 500
            return false;
        }
    }

    public Long getUserId(String token) {
        String sub = parseClaims(token).getPayload().getSubject();
        return Long.valueOf(sub);
    }

    public String getJti(String token) {
        return parseClaims(token).getPayload().getId();
    }

    /**
     * 读取会话标识。历史 Token 不含 sid 时返回 null。
     */
    public String getSid(String token) {
        return parseClaims(token).getPayload().get("sid", String.class);
    }

    /**
     * AccessToken 有效期的秒数，用于登录/刷新响应中的 expiresIn
     */
    public long getAccessExpirationSeconds() {
        return accessExpiration / 1000;
    }

    /**
     * RefreshToken 的有效期毫秒数，用于确定会话撤销标记的最长存活时长
     */
    public long getRefreshExpirationMillis() {
        return refreshExpiration;
    }

    public boolean isAccessToken(String token) {
        return "ACCESS".equals(parseClaims(token).getPayload().get("type", String.class));
    }

    public boolean isRefreshToken(String token) {
        return "REFRESH".equals(parseClaims(token).getPayload().get("type", String.class));
    }

    public long getRemainingMillis(String token) {
        Date exp = parseClaims(token).getPayload().getExpiration();
        return exp.getTime() - System.currentTimeMillis();
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parser().verifyWith(getSignKey()).build()
            .parseSignedClaims(token);
    }
}
