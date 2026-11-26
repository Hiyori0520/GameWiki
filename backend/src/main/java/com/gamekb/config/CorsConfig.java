package com.gamekb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。
 * 注意：allowCredentials(true) 与 allowedOriginPatterns("*") 同时使用既不安全，
 * 也在部分浏览器/版本下被直接拒绝，这里改为显式白名单。
 * 其他前端来源可通过 GAMEWIKI_CORS_ALLOWED_ORIGINS 显式配置。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${GAMEWIKI_CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
