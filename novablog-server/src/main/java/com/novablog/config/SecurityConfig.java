package com.novablog.config;

import com.novablog.security.AccessDeniedHandlerImpl;
import com.novablog.security.AuthOriginFilter;
import com.novablog.security.AuthenticationEntryPointImpl;
import com.novablog.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthOriginFilter authOriginFilter;
    private final AccessDeniedHandlerImpl accessDeniedHandler;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;
    private final AuthSecurityProperties authSecurityProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(accessDeniedHandler)
                .authenticationEntryPoint(authenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/login",
                    "/auth/register",
                    "/user/login",
                    "/user/register",
                    "/auth/refresh",
                    // 登出放行：AT 恰好过期时请求本身不该被拦下，
                    // 否则前端要先绕一圈刷新流程才能登出，接口内部凭 RT 仍能定位会话
                    "/auth/logout",
                    "/article/list",
                    "/article/hot",
                    "/comment/list",
                    "/category/list",
                    "/tag/list",
                    "/rag/ask",
                    "/rag/ask/stream",
                    "/v3/api-docs/**",
                    "/doc.html",
                    "/webjars/**",
                    "/swagger-ui/**"
                ).permitAll()
                // 公开的文章详情与用户主页：只放行「纯数字 ID」。
                // 原来的 /article/*、/user/* 会把 /article/import、/user/profile
                // 这类单段路径一并放行，收紧后写接口默认都需要登录
                .requestMatchers(
                    "/article/{id:[0-9]+}",
                    "/user/{id:[0-9]+}",
                    "/user/{id:[0-9]+}/articles"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // 来源校验必须在认证之前：Cookie 会被浏览器自动携带，
            // 先确认请求确实来自可信前端，再谈令牌是否有效
            .addFilterBefore(authOriginFilter, JwtAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // RT 走 Cookie 后，allowCredentials(true) 绝不能配通配 Origin：
        // 那等于允许任意站点带着用户的 Cookie 读取响应，AccessToken 直接被读走。
        // 这里与 Origin 校验共用同一份白名单
        List<String> allowedOrigins = authSecurityProperties.getAllowedOrigins().stream()
            .filter(origin -> origin != null && !origin.isBlank())
            .filter(origin -> {
                if ("*".equals(origin.trim())) {
                    log.warn("auth.security.allowed-origins 中的 * 已被忽略：携带凭据的 CORS 不允许通配来源");
                    return false;
                }
                return true;
            })
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
