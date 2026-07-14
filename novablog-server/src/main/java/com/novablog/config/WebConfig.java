package com.novablog.config;

import com.novablog.rag.interceptor.RagRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类。CORS 已迁移至 SecurityConfig，JWT 认证由 JwtAuthenticationFilter 接管。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RagRateLimitInterceptor ragRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ragRateLimitInterceptor)
                .addPathPatterns("/rag/ask", "/chat/ask", "/rag/ask/stream", "/chat/ask/stream");
    }
}
