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

    public String createAccessToken(Long userId, String username, List<String> roles) {
        return createToken(userId, username, roles, "ACCESS", accessExpiration);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, null, null, "REFRESH", refreshExpiration);
    }

    private String createToken(Long userId, String username,
                                List<String> roles, String type, long expiration) {
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

        return builder.compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSignKey()).build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
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
