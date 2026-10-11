package com.hotel.hotelmanagement.config;

import org.springframework.cache.annotation.CacheEvict;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 「清空全部展示读路径缓存」的组合注解，等价于：
 * <pre>
 * &#64;CacheEvict(cacheNames = {ROOM_STATUS, BOOKABLE_ROOMS, DASHBOARD}, allEntries = true)
 * </pre>
 *
 * <p>为什么要组合：{@code @CacheEvict} 的 {@code cacheNames} 只能写编译期常量
 * 字面量数组，不能引用 {@code String[]} 常量字段（数组不是常量表达式）；
 * 组合注解把缓存名清单收敛到 {@link CacheConfig} 一处，订单/房间的写路径统一标注它。
 * Spring Cache 原生支持元注解组合，拦截器会自动展开本注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict(cacheNames = {CacheConfig.ROOM_STATUS, CacheConfig.BOOKABLE_ROOMS, CacheConfig.DASHBOARD},
        allEntries = true)
public @interface EvictDisplayCaches {
}
