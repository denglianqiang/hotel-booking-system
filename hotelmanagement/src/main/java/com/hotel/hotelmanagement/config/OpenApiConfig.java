package com.hotel.hotelmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（springdoc-openapi）。
 *
 * <p>启动后可访问：
 * <ul>
 *   <li>Swagger UI：{@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON：{@code /v3/api-docs}</li>
 * </ul>
 *
 * <p>说明：接口文档未做登录保护，便于开发与答辩演示；
 * 生产环境如需关闭，可设置 {@code springdoc.api-docs.enabled=false}。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hotelOpenApi() {
        return new OpenAPI().info(new Info()
                .title("酒店客房预订管理系统 API")
                .version("1.0.0")
                .description("管理端 + 普通用户端接口。"
                        + "认证方式：请求头 Authorization 直接携带登录接口返回的 token（没有 Bearer 前缀）。"
                        + "状态码约定：认证/授权失败返回真实 HTTP 401/403；"
                        + "业务错误返回 HTTP 200 且响应体 code=400；未预期异常 code=500 且不暴露内部细节。"
                        + "页面级权限由各接口上的 @RequirePermission 权限码控制，"
                        + "完整的「接口 → 权限码」对照表见项目根目录 README.md 的「接口文档」一节。"));
    }
}
