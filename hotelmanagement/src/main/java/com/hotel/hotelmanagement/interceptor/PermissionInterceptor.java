package com.hotel.hotelmanagement.interceptor;

import com.hotel.hotelmanagement.annotation.RequirePermission;
import com.hotel.hotelmanagement.entity.User;
import com.hotel.hotelmanagement.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 页面级权限校验拦截器。
 *
 * <p>LoginInterceptor 只回答"token 是否有效"，本拦截器进一步回答
 * "这个账号有没有权访问这个接口"。必须注册在 LoginInterceptor <b>之后</b>。
 *
 * <p>鉴权口径与前端菜单一致：账号的 permissions（逗号分隔）与
 * {@link RequirePermission#value()} 求交集，非空即放行。这样"能看到的菜单"
 * 与"能调的接口"不会再是两套标准。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public PermissionInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 只有 Controller 方法才需要鉴权（静态资源等直接放行）
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 方法上的注解优先，其次取类上的
        RequirePermission required = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (required == null || required.value().length == 0) {
            return true;
        }

        User user = userService.getByToken(request.getHeader("Authorization"));
        if (user != null && hasAnyPermission(user.getPermissions(), required.value())) {
            return true;
        }

        writeForbidden(response);
        return false;
    }

    /**
     * 账号的权限码（逗号分隔）与所需权限码求交集，非空即有权
     */
    private boolean hasAnyPermission(String permissions, String[] required) {
        if (!StringUtils.hasText(permissions)) {
            return false;
        }
        Set<String> owned = Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return Arrays.stream(required).anyMatch(owned::contains);
    }

    /**
     * 返回 403。前端 request.js 会把响应体里的 message 直接提示给用户。
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"当前账号没有该功能的访问权限\",\"data\":null}");
    }
}
