package com.hotel.hotelmanagement.interceptor;

import com.hotel.hotelmanagement.util.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器
 * 管理端接口校验管理员 token，/client/** 客人端接口校验客人 token
 * 校验请求头 Authorization 中的 token，未登录返回 401
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final TokenStore tokenStore;

    public LoginInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        // 客人端接口校验客人 token，其余接口校验管理员 token
        Long userId = request.getRequestURI().startsWith("/client/")
                ? tokenStore.getClientUserId(token)
                : tokenStore.getUserId(token);
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
            return false;
        }
        return true;
    }
}
