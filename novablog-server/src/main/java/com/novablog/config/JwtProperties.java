package com.novablog.config;

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
}
