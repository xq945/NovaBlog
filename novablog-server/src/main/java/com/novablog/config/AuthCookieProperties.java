package com.novablog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RefreshToken Cookie 配置
 *
 * <p>RT 通过 HttpOnly Cookie 下发，前端 JS 完全读不到，XSS 即使得手也拿不走长期凭证。
 * 生产环境必须开启 secure；本地 http://localhost 下浏览器会拒收 Secure Cookie。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth.cookie")
public class AuthCookieProperties {

    /**
     * Cookie 名称
     */
    private String refreshTokenName = "nb_refresh_token";

    /**
     * Cookie 作用路径。默认 /：
     * 浏览器看到的刷新地址可能是 /api/auth/refresh（反代或 Vite 代理），
     * 若收紧到 /auth 会匹配不上，Cookie 根本不会被发送
     */
    private String path = "/";

    /**
     * 是否只允许 HTTPS 传输。生产置 true，本地 http 开发置 false
     */
    private boolean secure = false;

    /**
     * SameSite 属性，默认 Lax：跨站 POST 不携带 Cookie，挡住大部分 CSRF
     */
    private String sameSite = "Lax";

    /**
     * Cookie 作用域，留空表示 host-only（localhost 场景最稳）
     */
    private String domain = "";

    /**
     * 是否兼容旧的 Authorization: Bearer &lt;RT&gt; 传法。
     * 仅用于前后端分批升级的过渡期，全部升级后应置 false 并删除兼容分支
     */
    private boolean allowHeaderFallback = true;
}
