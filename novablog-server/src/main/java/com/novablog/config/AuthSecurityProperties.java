package com.novablog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 认证接口的来源白名单
 *
 * <p>RT 改用 Cookie 后，浏览器会自动携带凭据，必须限制哪些 Origin 能调用登录/刷新/登出，
 * 否则任意站点都能借用户的 Cookie 换取 AccessToken。同一份列表也用于 CORS，
 * 避免「CORS 通配 + 允许凭据」让任意站点读取带 Cookie 的响应。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.security")
public class AuthSecurityProperties {

    /**
     * 允许调用认证接口的前端来源，支持 * 通配（如 https://*.cpolar.top）
     */
    private List<String> allowedOrigins = List.of(
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:8080",
        "http://127.0.0.1:8080"
    );
}
