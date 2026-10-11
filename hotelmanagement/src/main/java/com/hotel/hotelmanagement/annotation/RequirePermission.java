package com.hotel.hotelmanagement.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 页面级权限校验注解。
 *
 * <p>标注在 Controller 的方法或类上，要求当前登录账号拥有其中<b>任意一个</b>权限码。
 * 未标注的接口只要求登录（由 LoginInterceptor 负责）。
 *
 * <p>改造前 permissions 只是前端菜单概念，后端不校验，
 * 于是"只有订单管理权限"的账号也能直接调删房间、删客户、看统计等接口（越权）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 需要的权限码，满足其中任意一个即可通过 */
    String[] value();
}
