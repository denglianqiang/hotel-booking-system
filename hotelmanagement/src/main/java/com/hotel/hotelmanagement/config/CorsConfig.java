package com.hotel.hotelmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * 跨域配置
 * 允许前端 http://localhost:5173 访问后端接口
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的前端源（patterns 支持通配，前后端分离开发/各部署形态都能过）
        //   - localhost:5173   Vite 开发服务器
        //   - localhost        本机 Docker（Nginx 托管前端）
        //   - *.trycloudflare.com  trycloudflare 免费隧道（域名每次启动都会变）
        // 生产走自有域名/服务器 IP 时按需在这里补充
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://localhost:8088",
                "http://localhost",
                "https://*.trycloudflare.com"
        ));
        // 允许所有请求方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许所有请求头
        config.setAllowedHeaders(List.of("*"));
        // 允许携带凭证（Cookie）
        config.setAllowCredentials(true);
        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
