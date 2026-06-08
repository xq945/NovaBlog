package com.novablog.config;

import com.novablog.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类
 * 注册 JWT 拦截器并配置放行路径
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 拦截所有路径
                .addPathPatterns("/**")
                // 放行公开接口
                .excludePathPatterns(
                        "/user/register",      // 用户注册
                        "/user/login",         // 用户登录
                        "/auth/refresh",       // Token 刷新
                        "/article/list",       // 文章列表
                        "/article/hot",        // 热门文章列表
                        "/category/list",      // 分类列表
                        "/tag/list",           // 标签列表
                        "/comment/list"        // 评论列表
                );
    }
}
