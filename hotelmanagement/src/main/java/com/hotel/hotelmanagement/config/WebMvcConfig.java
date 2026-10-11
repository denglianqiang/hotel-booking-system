package com.hotel.hotelmanagement.config;

import com.hotel.hotelmanagement.interceptor.LoginInterceptor;
import com.hotel.hotelmanagement.interceptor.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC 配置：注册拦截器 + 前端单页应用的刷新兜底。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 需要登录校验的接口前缀。
     *
     * <p>这里刻意<b>不使用 {@code "/**"}</b>：前端打包后由本服务的静态资源一并提供，
     * 如果拦截所有路径，会出现两个问题：
     * <ol>
     *   <li>刷新 {@code /orders} 这类前端路由时，请求根本没有对应接口，却被拦成 401；</li>
     *   <li>静态资源（index.html、js、css）每一条都要白跑一遍拦截器。</li>
     * </ol>
     * 因此只拦真正的业务接口。
     */
    private static final String[] API_PATTERNS = {
            "/user/**", "/role/**", "/room/**", "/order/**", "/customer/**",
            "/statistics/**", "/checkin/**", "/client/**"
    };

    /**
     * 无需登录即可访问的接口。
     *
     * <p>注销接口也必须放行：登录入口是统一的（/user/login 同时支持管理端账号与普通用户手机号），
     * 但登录拦截器是按路径前缀选 token 池的（/client/** 查客人 token，其余查管理端 token），
     * 所以普通用户拿自己的 token 调 /user/logout、管理员调 /client/logout 都会被拦成 401，
     * 请求根本到不了 Controller，结果就是"点了退出登录，服务端 token 依然有效"。
     * 注销本身应当幂等：token 无效（或没带）也返回成功。
     */
    private static final String[] PUBLIC_PATTERNS = {
            "/user/login",
            "/client/login",
            "/client/register",
            "/user/logout",
            "/client/logout"
    };

    private final LoginInterceptor loginInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    public WebMvcConfig(LoginInterceptor loginInterceptor, PermissionInterceptor permissionInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1) 登录校验：token 是否有效
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(API_PATTERNS)
                .excludePathPatterns(PUBLIC_PATTERNS);

        // 2) 页面级权限校验：该账号有没有权访问这个接口（注册顺序保证在登录校验之后执行）
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns(API_PATTERNS)
                .excludePathPatterns(PUBLIC_PATTERNS);
    }

    /**
     * 静态资源处理 + 前端路由兜底。
     *
     * <p>前端用 history 模式路由，直接访问或刷新 {@code /orders} 时服务端并没有这个资源。
     * 此时回退到 {@code index.html}，把路由交给前端处理；否则用户一刷新就 404。
     *
     * <p>注意：必须保留 {@code META-INF/resources}（Swagger UI 的 webjar 在这里），
     * 否则接口文档页面会被这次自定义覆盖掉。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(
                        "classpath:/static/",
                        "classpath:/public/",
                        "classpath:/resources/",
                        "classpath:/META-INF/resources/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 带扩展名的（js/css/图片）或 api 路径不做兜底，
                        // 避免把"资源确实不存在"伪装成首页，那样反而更难排查
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
