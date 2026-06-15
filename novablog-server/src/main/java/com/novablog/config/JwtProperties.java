package com.novablog.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置类
 * 从 application.yml 中读取 jwt.* 配置项
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥最小长度
     */
    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * JWT 签名密钥
     */
    private String secret;

    /**
     * Access Token 过期时间（毫秒）
     */
    private Long expiration = 7200000L;

    /**
     * Refresh Token 过期时间（毫秒）
     */
    private Long refreshExpiration = 604800000L;

    /**
     * 校验 JWT 密钥强度，防止使用弱密钥导致安全问题
     */
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT 密钥长度不能小于 " + MIN_SECRET_LENGTH + " 位，请在配置中设置高强度密钥"
            );
        }
    }
}
